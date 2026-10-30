package com.josuedev.neomessage

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DatabaseError

/**
 * MOTOR DE RED MULTI-CLÚSTER Y CONMUTACIÓN AUTOMÁTICA (AUTO-FAILOVER)
 * - Nodo 1 (Principal): 10 GB / 66 Millones de mensajes
 * - Nodo 2 (Respaldo Automático): 10 GB / 66 Millones de mensajes adicionales
 * - Total Gratuito: 20 GB / 132 Millones de mensajes al mes a $0.00 pesos.
 */
object NetworkConfig {
    // 🟢 CLÚSTER 1 (Principal Oficial)
    const val FIREBASE_DATABASE_URL = "https://neomessage-19ab4-default-rtdb.firebaseio.com"
    
    // 🔵 CLÚSTER 2 (Respaldo Secundario Automático)
    const val FIREBASE_BACKUP_URL = "https://neomessage-cluster2-rtdb.firebaseio.com"
    
    // ⚡ NODO SOBERANO RELAY (P2P / Local / VPS)
    const val NEO_RELAY_URL = "http://192.168.100.38:3060"

    // Estado del Clúster Activo
    @Volatile
    private var isUsingBackupNode = false

    fun getActiveDatabaseUrl(): String {
        return if (isUsingBackupNode) FIREBASE_BACKUP_URL else FIREBASE_DATABASE_URL
    }

    /**
     * Conmuta automáticamente al nodo secundario si el principal agota su cuota
     */
    fun switchToBackup(reason: String = "Límite mensual o falla de red") {
        if (!isUsingBackupNode) {
            isUsingBackupNode = true
            Log.w("NEO_FAILOVER", "⚡ CONMUTACIÓN AUTOMÁTICA ACTIVADA: $reason -> Cambiando a Nodo 2.")
        }
    }

    /**
     * Restablece al Nodo 1 (por ejemplo al inicio de cada mes)
     */
    fun resetToPrimaryNode() {
        isUsingBackupNode = false
        Log.i("NEO_FAILOVER", "🟢 Restablecido a Nodo Principal 1.")
    }

    /**
     * Verifica errores de cuota de Firebase para disparar la conmutación instantánea
     */
    fun handleDatabaseError(error: DatabaseError?) {
        if (error == null) return
        val msg = error.message.lowercase()
        val details = error.details.lowercase()
        if (msg.contains("quota") || msg.contains("limit") || msg.contains("exceeded") || details.contains("quota")) {
            switchToBackup("Cuota mensual de Nodo 1 alcanzada")
        }
    }

    fun getDatabaseReference(path: String): DatabaseReference {
        return FirebaseDatabase.getInstance(getActiveDatabaseUrl()).getReference(path)
    }

    /**
     * Regla de Blindaje de Cuota:
     * Si un archivo supera 500 KB, debe viajar por P2P directo para NO quemar los 10 GB de la nube.
     */
    fun isHeavyFile(bytes: Long): Boolean {
        return bytes > 500 * 1024L // > 500 KB
    }
    
    // Nodos comunes
    const val PATH_CHATS = "chats"
    const val PATH_USERS = "users"
    const val PATH_TYPING = "typing"
    const val PATH_STATUS = "status"
    const val PATH_GROUPS = "groups"
    const val PATH_STARRED = "starred"
    
    // El bucket exacto del proyecto NeoMessage Ultra
    const val FIREBASE_STORAGE_BUCKET = "neomessage-19ab4.firebasestorage.app"
}
