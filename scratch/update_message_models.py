import os

models_path = 'app/src/main/java/com/josuedev/neomessage/Models.kt'
with open(models_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_block = """data class CloudMessage(
    val id: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val senderId: String = "",
    val senderName: String = "",
    val rank: String = "",
    val isEdited: Boolean = false,
    val expiryTime: Long? = null,
    val isStarred: Boolean = false,
    val readBy: Map<String, Boolean> = emptyMap(),
    val replyToId: String? = null,
    val isDeletedForEveryone: Boolean = false
)"""

new_block = """data class CloudMessage(
    val id: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val senderId: String = "",
    val senderName: String = "",
    val rank: String = "",
    val isEdited: Boolean = false,
    val expiryTime: Long? = null,
    val isStarred: Boolean = false,
    val readBy: Map<String, Boolean> = emptyMap(),
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val reactions: Map<String, String> = emptyMap(),
    val isDeletedForEveryone: Boolean = false
)"""

if old_block in content:
    content = content.replace(old_block, new_block)
    with open(models_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Models.kt updated successfully")
else:
    print("Warning: old_block not found in Models.kt")

# Update LocalDatabase.kt
db_path = 'app/src/main/java/com/josuedev/neomessage/LocalDatabase.kt'
with open(db_path, 'r', encoding='utf-8') as f:
    db_content = f.read()

old_entity = """@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val text: String,
    val timestamp: Long,
    val senderId: String,
    val senderName: String,
    val chatId: String, // Importante para filtrar por chat
    val isDeletedForEveryone: Boolean = false,
    val expiryTime: Long? = null
)"""

new_entity = """@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val text: String,
    val timestamp: Long,
    val senderId: String,
    val senderName: String,
    val chatId: String,
    val isDeletedForEveryone: Boolean = false,
    val expiryTime: Long? = null,
    val isStarred: Boolean = false,
    val replyToText: String? = null,
    val replyToSender: String? = null
)"""

if old_entity in db_content:
    db_content = db_content.replace(old_entity, new_entity)
    # Also update mapping extensions
    old_ext = """fun CloudMessage.toEntity(chatId: String): MessageEntity = MessageEntity(
    id = id,
    text = text,
    timestamp = timestamp,
    senderId = senderId,
    senderName = senderName,
    chatId = chatId,
    isDeletedForEveryone = isDeletedForEveryone,
    expiryTime = timestamp + (30L * 24 * 60 * 60 * 1000)
)

fun MessageEntity.toCloud(): CloudMessage = CloudMessage(
    id = id,
    text = text,
    timestamp = timestamp,
    senderId = senderId,
    senderName = senderName,
    isDeletedForEveryone = isDeletedForEveryone,
    expiryTime = expiryTime
)"""

    new_ext = """fun CloudMessage.toEntity(chatId: String): MessageEntity = MessageEntity(
    id = id,
    text = text,
    timestamp = timestamp,
    senderId = senderId,
    senderName = senderName,
    chatId = chatId,
    isDeletedForEveryone = isDeletedForEveryone,
    expiryTime = expiryTime,
    isStarred = isStarred,
    replyToText = replyToText,
    replyToSender = replyToSender
)

fun MessageEntity.toCloud(): CloudMessage = CloudMessage(
    id = id,
    text = text,
    timestamp = timestamp,
    senderId = senderId,
    senderName = senderName,
    isDeletedForEveryone = isDeletedForEveryone,
    expiryTime = expiryTime,
    isStarred = isStarred,
    replyToText = replyToText,
    replyToSender = replyToSender
)"""
    db_content = db_content.replace(old_ext, new_ext)
    with open(db_path, 'w', encoding='utf-8') as f:
        f.write(db_content)
    print("LocalDatabase.kt updated successfully")
else:
    print("Warning: old_entity not found in LocalDatabase.kt")
