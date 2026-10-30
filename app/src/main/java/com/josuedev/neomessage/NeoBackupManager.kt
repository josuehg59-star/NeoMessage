package com.josuedev.neomessage

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🛡️ NEO BACKUP MANAGER
 * Sistema de respaldo soberano: guarda y restaura chats en el propio dispositivo
 * con cifrado AES-256 sin depender de servidores de terceros.
 */
object NeoBackupManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Genera un respaldo cifrado local de todos los chats y mensajes.
     * Costo: $0. Privacidad: 100% en el teléfono del usuario.
     */
    fun createLocalEncryptedBackup(context: Context, onComplete: (File?) -> Unit) {
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val messages = db.messageDao().getAllMessagesDirect()

                val rootJson = JSONObject()
                rootJson.put("version", "TITAN-BACKUP-v2")
                rootJson.put("timestamp", System.currentTimeMillis())

                val array = JSONArray()
                messages.forEach { entity ->
                    val obj = JSONObject().apply {
                        put("id", entity.id)
                        put("chatId", entity.chatId)
                        put("text", entity.text)
                        put("timestamp", entity.timestamp)
                        put("senderId", entity.senderId)
                        put("senderName", entity.senderName)
                    }
                    array.put(obj)
                }
                rootJson.put("messages", array)

                val encryptedData = NeoTripleShieldManager.encryptMessage(rootJson.toString())

                val backupFolder = File(context.getExternalFilesDir(null), "NeoMessenger/Backups")
                if (!backupFolder.exists()) backupFolder.mkdirs()

                val backupFile = File(backupFolder, "neo_backup_${System.currentTimeMillis()}.neobackup")
                backupFile.writeText(encryptedData)

                android.util.Log.i("NEO_BACKUP", "✅ Respaldo soberano generado: ${backupFile.absolutePath} (${messages.size} mensajes)")
                onComplete(backupFile)
            } catch (e: Exception) {
                android.util.Log.e("NEO_BACKUP", "Error creando respaldo local: ${e.message}")
                onComplete(null)
            }
        }
    }

    /**
     * Restaura mensajes desde un archivo de respaldo cifrado .neobackup
     */
    fun restoreFromLocalBackup(context: Context, backupFile: File, onComplete: (Int) -> Unit) {
        scope.launch {
            try {
                if (!backupFile.exists()) return@launch onComplete(0)

                val encryptedText = backupFile.readText()
                val decryptedJson = NeoTripleShieldManager.decryptMessage(encryptedText)
                val rootJson = JSONObject(decryptedJson)
                val array = rootJson.optJSONArray("messages") ?: JSONArray()

                val db = AppDatabase.getDatabase(context)
                var restoredCount = 0

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val entity = MessageEntity(
                        id = obj.getString("id"),
                        chatId = obj.getString("chatId"),
                        text = obj.getString("text"),
                        timestamp = obj.getLong("timestamp"),
                        senderId = obj.getString("senderId"),
                        senderName = obj.optString("senderName", "")
                    )
                    db.messageDao().insertMessage(entity)
                    restoredCount++
                }

                android.util.Log.i("NEO_BACKUP", "✅ Restauración completada: $restoredCount mensajes restaurados")
                onComplete(restoredCount)
            } catch (e: Exception) {
                android.util.Log.e("NEO_BACKUP", "Error al restaurar respaldo: ${e.message}")
                onComplete(0)
            }
        }
    }

    /**
     * RESTAURACIÓN REMOTA DINÁMICA: Si hay un servidor central configurado en PocketBase
     */
    fun performEmergencyRestore(context: Context, userPhone: String) {
        if (userPhone.isEmpty()) return
        val apiUrl = PocketBaseManager.getApiUrl()
        if (apiUrl.isEmpty()) return

        Thread {
            try {
                val url = URL("$apiUrl/api/collections/mensajes/records?filter=(usuario='$userPhone')&sort=-created&limit=100")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val response = conn.inputStream.bufferedReader().readText()

                val json = JSONObject(response)
                val items = json.getJSONArray("items")

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    NeoTripleShieldManager.saveToLocalNode(
                        context,
                        "RESTORED",
                        item.optString("texto"),
                        userPhone
                    )
                }
                android.util.Log.i("NEO_RESTORE", "✅ Historial restaurado desde servidor remoto.")
            } catch (e: Exception) {
                android.util.Log.w("NEO_RESTORE", "No se pudo sincronizar con servidor remoto: ${e.message}")
            }
        }.start()
    }
}
