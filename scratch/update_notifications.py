path = r'app/src/main/java/com/josuedev/neomessage/NeoNotificationHelper.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

old_fn = '''    fun showNotification(context: Context, title: String, message: String, messageId: String, videoUrl: String = "") {
        if (messageId == lastMessageId) return
        lastMessageId = messageId

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // --- INTENT ENRUTADO: Siempre abre la app y delega la decisión de reproducir video para ahorrar datos ---
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (videoUrl.isNotEmpty()) {
                putExtra("VIDEO_URL", videoUrl)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context, messageId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "yaris_alerts_priority"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "🚨 ALERTAS YARIS (Prioridad Máxima)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de seguridad en tiempo real"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setBypassDnd(true) // Saltar No Molestar
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true) // NO SE QUITA SOLA
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)) // VIBRACIÓN LARGA Y REPETITIVA
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER ALARMA", pendingIntent)

        notificationManager.notify(messageId.hashCode(), builder.build())
    }'''

new_fn = '''    fun showNotification(context: Context, title: String, message: String, messageId: String, videoUrl: String = "") {
        if (messageId == lastMessageId) return
        lastMessageId = messageId

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Intent fluido: no destruye la actividad en curso ni bloquea la app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (videoUrl.isNotEmpty()) {
                putExtra("VIDEO_URL", videoUrl)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context, messageId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "neo_messages_fast"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mensajes NeoMessage",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones instantáneas de mensajes"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 120, 60, 120)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOngoing(false) // Descartable fácilmente, no se queda trabada
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 120, 60, 120))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)

        notificationManager.notify(messageId.hashCode(), builder.build())
    }'''

if old_fn in c:
    c = c.replace(old_fn, new_fn)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)
    print("SUCCESS: Updated NeoNotificationHelper.kt with clean fast non-blocking notification")
else:
    print("ERROR: old_fn not found in NeoNotificationHelper.kt")
