package com.josuedev.neomessage

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Receptor de respuesta directa desde Relojes Inteligentes (Wear OS, Apple Watch, Garmin, etc.)
 * y barra de notificaciones de Android.
 */
class SmartwatchReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(NeoNotificationHelper.KEY_QUICK_REPLY)?.toString()?.trim() ?: return

        if (replyText.isEmpty()) return

        val chatId = intent.getStringExtra("EXTRA_CHAT_ID") ?: ""
        val otherPhone = intent.getStringExtra("EXTRA_OTHER_PHONE") ?: ""
        val notificationId = intent.getIntExtra("EXTRA_NOTIFICATION_ID", 0)

        val sp = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
        val myPhone = sp.getString("user_phone", "") ?: ""
        val myName = sp.getString("user_name", "Usuario") ?: "Usuario"
        val cleanMyId = NeoUtils.sanitizeId(myPhone)

        if (chatId.isNotEmpty() && cleanMyId.isNotEmpty()) {
            val database = NetworkConfig.getDatabaseReference("chats").child(chatId).child("messages")
            val mid = database.push().key ?: UUID.randomUUID().toString()
            val msg = CloudMessage(
                id = mid,
                text = replyText,
                timestamp = System.currentTimeMillis(),
                senderId = cleanMyId,
                senderName = myName,
                rank = "⌚ Smartwatch Reply"
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = try { AppDatabase.getDatabase(context).messageDao() } catch (_: Exception) { null }
                    dao?.insertMessage(msg.toEntity(chatId))
                } catch (_: Exception) {}

                try {
                    database.child(mid).setValue(msg)
                } catch (_: Exception) {}

                try {
                    NeoRelayClient.sendMessage(context, chatId, otherPhone, msg)
                    NeoLocalNodeServer.broadcastUdpMessage(otherPhone, msg, chatId)
                } catch (_: Exception) {}
            }
        }

        // Descartar o actualizar la notificación en el reloj tras responder
        if (notificationId != 0) {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (_: Exception) {}
        }
    }
}
