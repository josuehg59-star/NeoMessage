package com.josuedev.neomessage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val changelog: String = "",
    val isMandatory: Boolean = false
)

object NeoUpdateManager {
    @Volatile
    private var isDownloading = false

    @Volatile
    private var pendingInstallFile: File? = null

    @Volatile
    private var lastAutoTriggeredVersion = 0

    /**
     * Obtiene el versionCode real instalado en el sistema Android (nunca hardcodeado)
     */
    fun getLocalVersionCode(context: Context): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (_: Exception) {
            50
        }
    }

    /**
     * Escucha en tiempo real Firebase para detectar nuevas versiones al instante
     */
    fun checkForUpdates(context: Context, onUpdateAvailable: (UpdateInfo) -> Unit) {
        try {
            val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            db.getReference("app_updates").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val localCode = getLocalVersionCode(context)
                    val serverCode = snapshot.child("versionCode").getValue(Long::class.java)?.toInt() ?: 0
                    val versionName = snapshot.child("versionName").getValue(String::class.java) ?: ""
                    val apkUrl = snapshot.child("apkUrl").getValue(String::class.java)
                        ?: snapshot.child("downloadUrl").getValue(String::class.java)
                        ?: "https://github.com/josuehg59-star/NeoMessage/releases/latest/download/NeoMessage.apk"
                    val changelog = snapshot.child("changelog").getValue(String::class.java)
                        ?: snapshot.child("changeLog").getValue(String::class.java)
                        ?: "Mejoras de rendimiento y nuevas funciones."
                    val isMandatory = snapshot.child("isMandatory").getValue(Boolean::class.java) ?: true

                    if (serverCode > localCode && apkUrl.isNotBlank()) {
                        onUpdateAvailable(
                            UpdateInfo(
                                versionCode = serverCode,
                                versionName = versionName,
                                apkUrl = apkUrl,
                                changelog = changelog,
                                isMandatory = isMandatory
                            )
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (_: Exception) {}
    }

    /**
     * Si el usuario acaba de otorgar el permiso de instalar apps desconocidas y vuelve a la app,
     * lanza automáticamente el instalador del APK ya descargado.
     */
    fun resumePendingInstallIfAny(context: Context) {
        val file = pendingInstallFile ?: return
        if (file.exists() && file.length() > 1024 * 1024) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
                pendingInstallFile = null
                installApk(context, file, null)
            }
        }
    }

    /**
     * Descarga el APK automáticamente en segundo plano e inicia la instalación del sistema de inmediato
     */
    fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        versionCode: Int = 0,
        onProgress: ((Int) -> Unit)? = null
    ) {
        val cachedFile = File(context.cacheDir, "NeoMessage_Update.apk")
        if (versionCode > 0 && lastAutoTriggeredVersion == versionCode && cachedFile.exists() && cachedFile.length() > 5 * 1024 * 1024 && !isDownloading) {
            onProgress?.invoke(100)
            installApk(context, cachedFile, apkUrl)
            return
        }
        if (isDownloading) return
        isDownloading = true
        if (versionCode > 0) lastAutoTriggeredVersion = versionCode

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apkFile = File(context.cacheDir, "NeoMessage_Update.apk")
                if (apkFile.exists()) apkFile.delete()

                var currentUrl = apkUrl
                var conn: HttpURLConnection
                var redirects = 0
                while (true) {
                    val url = URL(currentUrl)
                    conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 25000
                    conn.readTimeout = 45000
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                    conn.connect()
                    val status = conn.responseCode
                    if ((status == HttpURLConnection.HTTP_MOVED_TEMP ||
                                status == HttpURLConnection.HTTP_MOVED_PERM ||
                                status == HttpURLConnection.HTTP_SEE_OTHER ||
                                status == 302 || status == 307 || status == 308) && redirects < 8
                    ) {
                        val loc = conn.getHeaderField("Location") ?: break
                        currentUrl = if (loc.startsWith("http")) loc else URL(url, loc).toString()
                        conn.disconnect()
                        redirects++
                        continue
                    }
                    break
                }

                if (conn.responseCode in 200..299) {
                    val totalSize = conn.contentLength
                    var downloaded = 0L
                    var lastReportedPercent = -1

                    conn.inputStream.use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(16384)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalSize > 0) {
                                    val percent = ((downloaded * 100) / totalSize).toInt().coerceIn(0, 100)
                                    if (percent != lastReportedPercent) {
                                        lastReportedPercent = percent
                                        withContext(Dispatchers.Main) {
                                            onProgress?.invoke(percent)
                                        }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (apkFile.exists() && apkFile.length() > 1024 * 1024) {
                        isDownloading = false
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(100)
                            Toast.makeText(context, "✅ Actualización lista. Instalando automáticamente...", Toast.LENGTH_SHORT).show()
                            installApk(context, apkFile, apkUrl)
                        }
                        return@launch
                    }
                }

                isDownloading = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Reintentando descarga directa...", Toast.LENGTH_SHORT).show()
                    openInBrowser(context, apkUrl)
                }

            } catch (e: Exception) {
                isDownloading = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error de red al descargar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Lanza el instalador oficial de Android para actualizar sobre la app actual sin perder datos
     */
    fun installApk(context: Context, file: File, fallbackUrl: String? = null) {
        try {
            if (!file.exists() || file.length() == 0L) {
                if (fallbackUrl != null) openInBrowser(context, fallbackUrl)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    pendingInstallFile = file
                    Toast.makeText(
                        context,
                        "⚡ Activa 'Permitir desde esta fuente' y la actualización se instalará sola al instante",
                        Toast.LENGTH_LONG
                    ).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            @Suppress("DEPRECATION")
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            }

            try {
                context.startActivity(installIntent)
            } catch (_: Exception) {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
            }

        } catch (e: Exception) {
            Toast.makeText(context, "Error al iniciar instalador: ${e.message}", Toast.LENGTH_LONG).show()
            if (fallbackUrl != null) openInBrowser(context, fallbackUrl)
        }
    }

    private fun openInBrowser(context: Context, url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {}
    }
}
