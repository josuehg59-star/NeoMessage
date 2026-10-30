with open('app/src/main/java/com/josuedev/neomessage/CallingScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target = """        onDispose {
            try {
                NeoVoiceCallManager.onAudioChunkCaptured = null
                NeoVoiceCallManager.endCall(context)
                val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                db.getReference("calls_live").child(effectiveChannel).removeValue()
            } catch (_: Exception) {}
        }
    }"""

addition = """        onDispose {
            try {
                NeoVoiceCallManager.onAudioChunkCaptured = null
                NeoVoiceCallManager.endCall(context)
                val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                db.getReference("calls_live").child(effectiveChannel).removeValue()
            } catch (_: Exception) {}
        }
    }

    // Tono de llamada saliente ("tuuuut... tuuuut...") mientras espera respuesta
    LaunchedEffect(callingTime, remoteParticipants.size) {
        if (callingTime in 1..8 && remoteParticipants.isEmpty()) {
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICECALL, 70)
                toneGen.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE, 1800)
                kotlinx.coroutines.delay(3000)
                toneGen.release()
            } catch (_: Exception) {}
        }
    }"""

content = content.replace(target, addition, 1)
with open('app/src/main/java/com/josuedev/neomessage/CallingScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("PATCHED CALLING SCREEN SUCCESSFULLY")
