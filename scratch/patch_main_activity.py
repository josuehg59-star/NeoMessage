with open('app/src/main/java/com/josuedev/neomessage/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

old_block = """                if (incoming != null) {
                    if (state.currentScreen != Screen.Calling) {
                        NeoNotificationHelper.showCallNotification(context, incoming)
                        if (currentRingtone == null) {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                            audioManager.mode = android.media.AudioManager.MODE_RINGTONE
                            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                            currentRingtone = android.media.RingtoneManager.getRingtone(context, uri)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                currentRingtone?.isLooping = true
                            }
                            currentRingtone?.play()
                        }
                        // Conectar a la llamada con canal sincronizado
                        state = state.copy(activeChatName = incoming, activeCallId = if (chId.isNotEmpty()) chId else state.activeCallId, currentScreen = Screen.Calling)
                    }
                }"""

new_block = """                if (incoming != null) {
                    val cleanCaller = NeoUtils.sanitizeId(cId)
                    if (cleanCaller.isNotEmpty() && cleanMyPhone.isNotEmpty() && cleanCaller == cleanMyPhone) {
                        return
                    }
                    if (state.currentScreen != Screen.Calling) {
                        NeoNotificationHelper.showCallNotification(context, incoming)
                        if (currentRingtone == null) {
                            try {
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                audioManager.mode = android.media.AudioManager.MODE_RINGTONE
                                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                                currentRingtone = android.media.RingtoneManager.getRingtone(context, uri)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    currentRingtone?.isLooping = true
                                }
                                currentRingtone?.play()
                            } catch (_: Exception) {}
                        }
                        // Conectar a la llamada con canal sincronizado
                        state = state.copy(activeChatName = incoming, activeCallId = if (chId.isNotEmpty()) chId else state.activeCallId, currentScreen = Screen.Calling)
                    }
                }"""

if old_block in code:
    code = code.replace(old_block, new_block)
    print("Patched caller check in incoming calls")

old_other_id = "otherId = state.activeChatName,"
new_other_id = "otherId = if (state.activeChatId.isNotEmpty()) state.activeChatId else state.activeChatName,"
if old_other_id in code:
    code = code.replace(old_other_id, new_other_id)
    print("Patched otherId in CallingScreen")

with open('app/src/main/java/com/josuedev/neomessage/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("MainActivity.kt patched!")
