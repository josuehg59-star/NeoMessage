package com.josuedev.neomessage

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
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
    // Código de versión local de la app
    const val CURRENT_VERSION_CODE = 23
    const val CURRENT_VERSION_NAME = "3.13-GOLD-INTERACTIVE"

    /**
     * Consulta Firebase o el servidor para ver si hay una versión más reciente
     */
    fun checkForUpdates(context: Context, onUpdateAvailable: (UpdateInfo) -> Unit) {
        try {
            val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            db.getReference("app_updates").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val serverCode = snapshot.child("versionCode").getValue(Long::class.java)?.toInt() ?: 0
                    val versionName = snapshot.child("versionName").getValue(String::class.java) ?: ""
                    val apkUrl = snapshot.child("apkUrl").getValue(String::class.java) ?: ""
                    val changelog = snapshot.child("changelog").getValue(String::class.java) ?: "Mejoras de rendimiento y nuevas funciones."
                    val isMandatory = snapshot.child("isMandatory").getValue(Boolean::class.java) ?: false

                    if (serverCode > CURRENT_VERSION_CODE && apkUrl.isNotBlank()) {
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
     * Descarga el APK con motor resiliente en primer plano/caché e inicia la instalación automática
     */
    fun downloadAndInstallApk(context: Context, apkUrl: String, onProgress: ((Int) -> Unit)? = null) {
        Toast.makeText(context, "📥 Descargando actualización... Por favor espera unos segundos.", Toast.LENGTH_LONG).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apkFile = File(context.cacheDir, "NeoMessage_Update.apk")
                if (apkFile.exists()) apkFile.delete()

                val url = URL(apkUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 40000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                }
                conn.connect()

                if (conn.responseCode in 200..299) {
                    val totalSize = conn.contentLength
                    var downloaded = 0L

                    conn.inputStream.use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalSize > 0) {
                                    val percent = ((downloaded * 100) / totalSize).toInt()
                                    onProgress?.invoke(percent)
                                }
                            }
                            output.flush()
                        }
                    }

                    if (apkFile.exists() && apkFile.length() > 1024 * 1024) { // Mayor a 1 MB
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "✅ Descarga completa. Instalando...", Toast.LENGTH_SHORT).show()
                            installApk(context, apkFile, apkUrl)
                        }
                        return@launch
                    }
                }

                // Si falla la descarga directa, abrir en navegador
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Abriendo descarga directa en el navegador...", Toast.LENGTH_LONG).show()
                    openInBrowser(context, apkUrl)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Descarga en app falló: ${e.message}. Abriendo navegador...", Toast.LENGTH_LONG).show()
                    openInBrowser(context, apkUrl)
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
                Toast.makeText(context, "El archivo de actualización no se encontró. Abriendo descarga directa...", Toast.LENGTH_SHORT).show()
                if (fallbackUrl != null) openInBrowser(context, fallbackUrl)
                return
            }

            // Permiso de instalación en Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Permite la instalación de NeoMessage para actualizar", Toast.LENGTH_LONG).show()
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

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)

        } catch (e: Exception) {
            Toast.makeText(context, "Error al iniciar instalador: ${e.message}", Toast.LENGTH_LONG).show()
            if (fallbackUrl != null) openInBrowser(context, fallbackUrl)
        }
    }

    /**
     * Abre el enlace en el navegador si el instalador interno o la descarga encuentran trabas del fabricante
     */
    fun openInBrowser(context: Context, url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se encontró navegador web para abrir la descarga.", Toast.LENGTH_SHORT).show()
        }
    }
}
