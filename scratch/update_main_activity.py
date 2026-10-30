with open('app/src/main/java/com/josuedev/neomessage/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Add state for incoming call
old_incoming_search = "        // --- DETECTOR DE LLAMADAS ENTRANTES ---"
new_incoming_replace = """        // --- DETECTOR DE LLAMADAS ENTRANTES ---
        val cleanMyPhone = NeoUtils.sanitizeId(state.userId)
        val callTargetNode = if (cleanMyPhone.isNotEmpty()) cleanMyPhone else state.userId"""

if old_incoming_search in code:
    code = code.replace(old_incoming_search, new_incoming_replace)
    print("Replaced incoming search target node")

old_call_listener = """        val callsRef = db.getReference("calls").child(state.userId)
        callsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val incoming = snapshot.child("callerName").getValue(String::class.java)
                if (incoming != null) {
                    if (state.currentScreen != Screen.Calling) {
                        NeoNotificationHelper.showCallNotification(context, incoming)
                        // Reproducir timbre forzando AudioManager (REPARACIÓN LLAMADA)
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
                        state = state.copy(activeChatName = incoming, currentScreen = Screen.Calling)
                    }
                } else {

                    // Si el nodo desaparece, la llamada terminó
                    NeoNotificationHelper.cancelCallNotification(context)
                    currentRingtone?.stop()
                    currentRingtone = null
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    audioManager.mode = android.media.AudioManager.MODE_NORMAL
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })"""

new_call_listener = """        val callsRef = db.getReference("calls").child(callTargetNode)
        callsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val incoming = snapshot.child("callerName").getValue(String::class.java)
                val chId = snapshot.child("channelId").getValue(String::class.java) ?: ""
                val cId = snapshot.child("callerId").getValue(String::class.java) ?: ""
                if (incoming != null) {
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
                } else {
                    NeoNotificationHelper.cancelCallNotification(context)
                    currentRingtone?.stop()
                    currentRingtone = null
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    audioManager.mode = android.media.AudioManager.MODE_NORMAL
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })"""

if old_call_listener in code:
    code = code.replace(old_call_listener, new_call_listener)
    print("Replaced calls listener")

# 2. Update ChatsListScreen call in MainActivity to pass onNavigateToCorteCaja
old_chats_list_call = """                        onNavigateToAi = { state = state.copy(currentScreen = Screen.NeoAI) },
                        textColor = textColor, accentGradient = accentGradient
                    )"""

new_chats_list_call = """                        onNavigateToAi = { state = state.copy(currentScreen = Screen.NeoAI) },
                        textColor = textColor, accentGradient = accentGradient,
                        onNavigateToCorteCaja = { state = state.copy(currentScreen = Screen.CorteCaja) }
                    )"""

if old_chats_list_call in code:
    code = code.replace(old_chats_list_call, new_chats_list_call)
    print("Replaced ChatsListScreen call")

# 3. Update onNavigateToCalling in ChatRoomScreen
old_calling_nav = """                        onNavigateToCalling = { otherId -> 
                            state = state.copy(activeChatName = state.activeChatName, activeCallId = otherId, currentScreen = Screen.Calling)
                            val callRef = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("calls").child(otherId)
                            callRef.setValue(mapOf("callerName" to state.myName, "callerId" to state.userId, "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP))
                        }"""

new_calling_nav = """                        onNavigateToCalling = { otherId -> 
                            val cleanMe = NeoUtils.sanitizeId(state.userId)
                            val cleanOther = NeoUtils.sanitizeId(otherId)
                            val callChannel = if (cleanMe.isNotEmpty() && cleanOther.isNotEmpty()) NeoUtils.getChatId(cleanMe, cleanOther) else "call_${otherId}"
                            state = state.copy(activeChatName = state.activeChatName, activeCallId = callChannel, currentScreen = Screen.Calling)
                            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                            val callData = mapOf(
                                "callerName" to state.myName,
                                "callerId" to state.userId,
                                "channelId" to callChannel,
                                "isVideo" to true,
                                "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                            if (cleanOther.isNotEmpty()) {
                                db.getReference("calls").child(cleanOther).setValue(callData)
                            }
                            if (otherId.isNotEmpty() && otherId != cleanOther) {
                                db.getReference("calls").child(otherId).setValue(callData)
                            }
                        }"""

if old_calling_nav in code:
    code = code.replace(old_calling_nav, new_calling_nav)
    print("Replaced onNavigateToCalling")

# 4. Update Screen.Calling in MainActivity
old_calling_screen = """                    Screen.Calling -> CallingScreen(chatName = state.activeChatName, onHangUp = { 
                        val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                        db.getReference("calls").child(state.userId).removeValue()
                        if (state.activeCallId.isNotEmpty()) db.getReference("calls").child(state.activeCallId).removeValue()
                        currentRingtone?.stop(); currentRingtone = null
                        state = state.copy(currentScreen = Screen.ChatsList, activeCallId = "") 
                    })"""

new_calling_screen = """                    Screen.Calling -> CallingScreen(
                        chatName = state.activeChatName,
                        callChannel = state.activeCallId,
                        myId = state.userId,
                        otherId = state.activeChatName,
                        initialIsVideo = true,
                        onHangUp = { 
                            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                            val cleanMe = NeoUtils.sanitizeId(state.userId)
                            db.getReference("calls").child(state.userId).removeValue()
                            if (cleanMe.isNotEmpty()) db.getReference("calls").child(cleanMe).removeValue()
                            if (state.activeCallId.isNotEmpty()) db.getReference("calls").child(state.activeCallId).removeValue()
                            currentRingtone?.stop(); currentRingtone = null
                            state = state.copy(currentScreen = Screen.ChatsList, activeCallId = "") 
                        }
                    )
                    Screen.CorteCaja -> CorteDeCajaScreen(onBack = { state = state.copy(currentScreen = Screen.ChatsList) })"""

if old_calling_screen in code:
    code = code.replace(old_calling_screen, new_calling_screen)
    print("Replaced Screen.Calling and added Screen.CorteCaja")

with open('app/src/main/java/com/josuedev/neomessage/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("MainActivity.kt updated!")
