package com.josuedev.neomessage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class NeoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, NeoForegroundService::class.java)
            // Al reiniciar, no tenemos userId, pero el servicio escuchará alertas de Josué por defecto
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
