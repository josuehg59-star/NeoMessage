with open('app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Update otherCleanId so groups have proper non-empty ID
old_other_clean = """    val otherCleanId = remember(chatId) { 
        val parts = chatId.replace("private_", "").split("_")
        if (parts.size == 2) {
            if (parts[0] == cleanMyId) parts[1] else parts[0]
        } else ""
    }"""

new_other_clean = """    val otherCleanId = remember(chatId) { 
        val parts = chatId.replace("private_", "").split("_")
        if (parts.size == 2) {
            if (parts[0] == cleanMyId) parts[1] else parts[0]
        } else chatId
    }"""

if old_other_clean in code:
    code = code.replace(old_other_clean, new_other_clean)
    print("Replaced otherCleanId")
else:
    print("WARNING: old_other_clean not found")

# 2. Update MessageContent call around line 748
old_call = "MessageContent(msg, textColor, textSubColor, context, onImageClick = { activeZoomBitmap = it })"
new_call = "MessageContent(msg, textColor, textSubColor, context, isMine = isMine, playingVoiceMsgId = playingVoiceMsgId, onPlayingVoiceChange = { playingVoiceMsgId = it }, onImageClick = { activeZoomBitmap = it })"

if old_call in code:
    code = code.replace(old_call, new_call)
    print("Replaced MessageContent call")
else:
    print("WARNING: old_call not found")

# 3. Update MessageContent signature and body
old_def = """@Composable
fun MessageContent(
    msg: CloudMessage, 
    textColor: Color, 
    textSubColor: Color, 
    context: Context,
    onImageClick: ((ImageBitmap) -> Unit)? = null
) {"""

new_def = """@Composable
fun MessageContent(
    msg: CloudMessage, 
    textColor: Color, 
    textSubColor: Color, 
    context: Context,
    isMine: Boolean = false,
    playingVoiceMsgId: String? = null,
    onPlayingVoiceChange: ((String?) -> Unit)? = null,
    onImageClick: ((ImageBitmap) -> Unit)? = null
) {"""

if old_def in code:
    code = code.replace(old_def, new_def)
    print("Replaced MessageContent signature")
else:
    print("WARNING: old_def not found")

# 4. In MessageContent voice player
old_voice_block = """                    IconButton(
                        onClick = {
                            if (isPlayingThis) {
                                NeoVoiceNoteManager.stopPlayback()
                                playingVoiceMsgId = null
                            } else {
                                playingVoiceMsgId = msg.id
                                NeoVoiceNoteManager.playVoiceNote(context, msg.id, base64Audio) {
                                    playingVoiceMsgId = null
                                }
                            }
                        },"""

new_voice_block = """                    IconButton(
                        onClick = {
                            if (isPlayingThis) {
                                NeoVoiceNoteManager.stopPlayback()
                                onPlayingVoiceChange?.invoke(null)
                            } else {
                                onPlayingVoiceChange?.invoke(msg.id)
                                NeoVoiceNoteManager.playVoiceNote(context, msg.id, base64Audio) {
                                    onPlayingVoiceChange?.invoke(null)
                                }
                            }
                        },"""

if old_voice_block in code:
    code = code.replace(old_voice_block, new_voice_block)
    print("Replaced voice player block")
else:
    print("WARNING: old_voice_block not found")

with open('app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("ChatRoomScreen.kt updated successfully!")
