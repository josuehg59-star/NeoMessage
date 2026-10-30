package com.josuedev.neomessage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat

object NeoNotificationHelper {
    private const val CHANNEL_ID = "neo_messages"
    const val KEY_QUICK_REPLY = "key_quick_reply_text"
    private var lastMessageId: String? = null

    /**
     * Muestra la notificación en el teléfono y sincroniza instantáneamente con Relojes Inteligentes
     * (Wear OS, Apple Watch, Samsung Galaxy Watch, Huawei, Xiaomi, Garmin, Amazfit).
     * Incluye respuesta por voz o teclado directamente desde la muñeca.
     */
    fun showNotification(
        context: Context,
        title: String,
        message: String,
        messageId: String,
        videoUrl: String = "",
        chatId: String = "",
        otherPhone: String = ""
    ) {
        if (messageId == lastMessageId) return
        lastMessageId = messageId

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = messageId.hashCode()

        // 1. Intent para abrir el chat al tocar la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (videoUrl.isNotEmpty()) putExtra("VIDEO_URL", videoUrl)
            if (chatId.isNotEmpty()) putExtra("CHAT_ID", chatId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. Canal de Notificación de Alta Prioridad
        val channelId = "neo_messages_fast"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mensajes NeoMessage",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones instantáneas de mensajes compatibles con Smartwatch"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 150)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 3. RESPUESTA RÁPIDA PARA RELOJES INTELIGENTES (WEAR OS / GALAXY WATCH / APPLE WATCH)
        val remoteInput = RemoteInput.Builder(KEY_QUICK_REPLY)
            .setLabel("Responder desde el reloj...")
            .setChoices(arrayOf("¡Entendido! 👍", "OK", "En un momento te llamo", "Estoy ocupado", "Voy en camino 🚗"))
            .build()

        val replyIntent = Intent(context, SmartwatchReplyReceiver::class.java).apply {
            putExtra("EXTRA_CHAT_ID", chatId)
            putExtra("EXTRA_OTHER_PHONE", otherPhone)
            putExtra("EXTRA_NOTIFICATION_ID", notifId)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Responder",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
         .setAllowGeneratedReplies(true)
         .build()

        // 4. Extensión para Wearable / Reloj Inteligente
        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(replyAction)
            .setHintHideIcon(false)

        // 5. Construcción de Notificación con Compatibilidad de Reloj
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 150, 80, 150))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .extend(wearableExtender)

        notificationManager.notify(notifId, builder.build())
    }

    fun showCallNotification(context: Context, callerName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "neo_calls")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("📞 LLAMADA ENTRANTE")
            .setContentText("$callerName te está llamando...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(pendingIntent, true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(pendingIntent)
            .extend(NotificationCompat.WearableExtender()) // Vibración y timbre en el Smartwatch

        notificationManager.notify(777, builder.build())
    }

    fun cancelCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(777)
    }

    private var mediaPlayer: android.media.MediaPlayer? = null

    fun dispararAlarmaFuerte(context: Context) {
        try {
            if (mediaPlayer == null) {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = android.media.MediaPlayer.create(context, alarmUri)
                mediaPlayer?.isLooping = true
                mediaPlayer?.setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            android.util.Log.e("TITAN_ALARM", "Error sirena: ${e.message}")
        }
    }

    fun detenerAlarma() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun isAlarmaSonando(): Boolean = mediaPlayer?.isPlaying ?: false
}
