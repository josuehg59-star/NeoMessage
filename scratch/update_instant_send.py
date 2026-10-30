path = r'app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

old_block = '''                // 4. INTENTO 2: SINCRONIZACIÓN EN SEGUNDO PLANO (TOTALMENTE SILENCIOSA)
                PocketBaseManager.sendMessage(
                    chatId, cleanMyId, myName, currentText,
                    onSuccess = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val pingDb = NetworkConfig.getDatabaseReference("pings").child(chatId)
                                pingDb.setValue(System.currentTimeMillis())
                            } catch (_: Exception) {}
                        }
                    },
                    onFailure = { _ ->
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                database.child(msgId).setValue(msg)
                            } catch (e: Exception) {
                                // Silencioso: no se muestra ningún Toast molesto si Firebase está offline o denegado
                                android.util.Log.w("NEO_FIREBASE", "Cloud sync queued: ${e.message}")
                            }
                        }
                    }
                )'''

new_block = '''                // 4. ENTREGA INMEDIATA EN TIEMPO REAL VÍA FIREBASE (CERO DEMORA, MILISEGUNDOS)
                try {
                    database.child(msgId).setValue(msg)
                } catch (e: Exception) {
                    android.util.Log.w("NEO_FIREBASE", "Cloud sync queued: ${e.message}")
                }

                // 5. RESPALDO ASÍNCRONO SIN BLOQUEAR
                PocketBaseManager.sendMessage(chatId, cleanMyId, myName, currentText, {}, {})'''

if old_block in c:
    c = c.replace(old_block, new_block)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)
    print("SUCCESS: Replaced PocketBase blocking delay with instant Firebase delivery")
else:
    print("ERROR: old_block not found in ChatRoomScreen.kt")
