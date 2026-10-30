package com.josuedev.neomessage

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
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
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesDirect(): List<MessageEntity>

    @Query("SELECT DISTINCT chatId FROM messages")
    suspend fun getDistinctChatIds(): List<String>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(chatId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChat(chatId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM messages WHERE expiryTime IS NOT NULL AND expiryTime < :now")
    suspend fun deleteExpiredMessages(now: Long)
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neo_message_db"
                ).fallbackToDestructiveMigration() // ESTO EVITA QUE LA APP SE CIERRE SI CAMBIA EL CÓDIGO
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Extensiones para convertir entre CloudMessage y MessageEntity
fun CloudMessage.toEntity(chatId: String): MessageEntity = MessageEntity(
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
)
