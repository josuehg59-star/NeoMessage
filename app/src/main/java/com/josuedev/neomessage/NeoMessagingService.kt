package com.josuedev.neomessage

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import android.content.Context

class NeoMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // --- FILTRO DE ACTUALIDAD (TIEMPO REAL) ---
        val sentTime = remoteMessage.sentTime
        val currentTime = System.currentTimeMillis()
        if (sentTime > 0 && (currentTime - sentTime) > 60000) {
            Log.w("NEO_FCM", "🗑️ Alerta vieja descartada (${(currentTime - sentTime) / 1000}s de retraso)")
            return
        }

        // --- FILTRO DE PRIVACIDAD MAESTRO TITÁN ---
        val sp = getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
        val myNumber = sp.getString("user_phone", "") ?: ""
        
        // Si es una alerta de seguridad pero no es el dueño, abortamos
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: ""
        
        // --- FILTRO DE TRANSEÚNTE (SILENCIO TÁCTICO) ---
        val body = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: ""
        if (body.contains("TRANSEÚNTE", ignoreCase = true)) {
            Log.d("NEO_FCM", "🤫 Transeúnte detectado: Ignorando notificación por configuración.")
            return
        }

        if ((title.contains("TITAN") || title.contains("YARIS") || title.contains("MERODEO")) && !myNumber.contains("9612765041")) {
            Log.w("NEO_FCM", "🛡️ Intento de acceso no autorizado a vigilancia bloqueado.")
            return
        }

        Log.d("NEO_FCM", "Protocolo de Seguridad: MENSAJE RECIBIDO")

        // 1. Extraer datos del mensaje
        val messageBody = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: "Movimiento detectado"
        val videoUrl = remoteMessage.data["video_url"] ?: ""
        
        // 2. DISPARAR ALARMA SOLO SI ES CRÍTICA (YARIS)
        if (title.contains("CRÍTICA", ignoreCase = true)) {
            NeoNotificationHelper.dispararAlarmaFuerte(applicationContext)
        }

        NeoNotificationHelper.showNotification(
            applicationContext,
            title,
            body,
            remoteMessage.data["event_id"] ?: System.currentTimeMillis().toString(),
            videoUrl
        )
        
        // 3. Si hay un video, podríamos incluso disparar una acción especial aquí
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("NEO_FCM", "Nuevo Token de Acceso: $token")
    }
}
