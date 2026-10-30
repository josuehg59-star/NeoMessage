package com.josuedev.neomessage

/**
 * CONFIGURACIÓN CENTRAL DE RED Y SERVIDORES SOBERANOS NEOMESSAGE
 * - Cero dependencias de servidores externos con anuncios (Catbox/Litterbox).
 * - Servidor de actualizaciones alojado directamente en tu propia PC.
 */
object SovereignConfig {
    // IP local de tu PC en la red Wi-Fi para descargas ultra rápidas y limpias sin anuncios
    const val MASTER_PC_IP = "192.168.100.38"
    const val MASTER_PC_PORT = 3060

    // URL directa local desde tu PC
    const val LOCAL_UPDATE_URL = "http://$MASTER_PC_IP:$MASTER_PC_PORT/download"
    
    // Túnel soberano seguro para cuando estés fuera de tu casa/taller
    const val PUBLIC_TUNNEL_URL = "https://forty-mice-battle.loca.lt/download"
}
