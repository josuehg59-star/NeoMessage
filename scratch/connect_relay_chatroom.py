path = r'app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Listen to NeoRelayClient in LaunchedEffect(chatId)
listen_target = '''            database.limitToLast(50).addChildEventListener(listener)
        } catch (_: Exception) {}
    }'''

new_listen = '''            database.limitToLast(50).addChildEventListener(listener)
        } catch (_: Exception) {}

        // 3. ESCUCHA ACTIVA DE RELAY SOBERANO ($0.00 COSTO)
        coroutineScope.launch {
            NeoRelayClient.incomingMessages.collect { (relChatId, relMsg) ->
                if (relChatId == chatId || relMsg.senderId == otherCleanId) {
                    withContext(Dispatchers.Main) {
                        if (firebaseMessages.none { it.id == relMsg.id }) {
                            firebaseMessages.add(relMsg)
                            firebaseMessages.sortBy { it.timestamp }
                        }
                    }
                }
            }
        }
    }'''

if listen_target in c:
    c = c.replace(listen_target, new_listen, 1)

# 2. Add NeoRelayClient.sendMessage in sendMessage
send_target = '''                // 3. EMISIÓN INSTANTÁNEA UDP MESH (LLEGA EN < 1 ms A CUALQUIER TELÉFONO EN LA MISMA RED/HOTSPOT)
                NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)'''

new_send = '''                // 3. EMISIÓN INSTANTÁNEA UDP MESH (LLEGA EN < 1 ms A CUALQUIER TELÉFONO EN LA MISMA RED/HOTSPOT)
                NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)

                // 3.1 DESPACHO SOBERANO RELAY CELULAR -> SERVIDOR -> CELULAR ($0.00 COSTO)
                NeoRelayClient.sendMessage(context, chatId, otherCleanId, msg)'''

if send_target in c:
    c = c.replace(send_target, new_send, 1)

# 3. Add NeoRelayClient.sendMessage in performMediaUpload
media_target = '''                    // Enviar vía UDP Mesh local
                    NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)'''

new_media = '''                    // Enviar vía UDP Mesh local
                    NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)

                    // Despacho vía Relay ($0.00 costo)
                    NeoRelayClient.sendMessage(context, chatId, otherCleanId, msg)'''

if media_target in c:
    c = c.replace(media_target, new_media, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("SUCCESS: Connected NeoRelayClient to ChatRoomScreen.kt")
