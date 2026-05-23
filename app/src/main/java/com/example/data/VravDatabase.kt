package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val content: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val isFile: Boolean,
    val fileName: String,
    val fileSize: String,
    val fileCid: String,
    val fileUnpinSecondsLeft: Int,
    val fileUnpinTotalSeconds: Int = 60,
    val isDelivered: Boolean,
    val isOutgoing: Boolean,
    val aesIv: String,
    val cryptographyType: String
)

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val peerId: String,
    val name: String,
    val ipAddress: String,
    val multiaddress: String,
    val x25519PublicKey: String,
    val kyberPublicKey: String,
    val isOnline: Boolean,
    val lastSeen: Long
)

@Entity(tableName = "crdt_logs")
data class CrdtLogEntity(
    @PrimaryKey val id: String,
    val logicalClock: Long,
    val operationType: String, // "INSERT", "DELETE"
    val targetId: String, // target messageId
    val originatingPeerId: String,
    val timestamp: Long
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY timestamp ASC")
    suspend fun getMessagesForPeerOnce(peerId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("SELECT * FROM messages WHERE isFile = 1 AND fileUnpinSecondsLeft > 0")
    suspend fun getActiveTransientFiles(): List<MessageEntity>
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeersFlow(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE peerId = :peerId LIMIT 1")
    suspend fun getPeerById(peerId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: PeerEntity)

    @Query("UPDATE peers SET isOnline = :isOnline, lastSeen = :lastSeen WHERE peerId = :peerId")
    suspend fun updatePeerStatus(peerId: String, isOnline: Boolean, lastSeen: Long)

    @Query("DELETE FROM peers WHERE peerId = :peerId")
    suspend fun deletePeer(peerId: String)
}

@Dao
interface CrdtLogDao {
    @Query("SELECT * FROM crdt_logs ORDER BY logicalClock ASC, timestamp ASC")
    fun getAllLogsFlow(): Flow<List<CrdtLogEntity>>

    @Query("SELECT * FROM crdt_logs ORDER BY logicalClock ASC, timestamp ASC")
    suspend fun getAllLogsOnce(): List<CrdtLogEntity>

    @Query("SELECT MAX(logicalClock) FROM crdt_logs")
    suspend fun getMaxClock(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CrdtLogEntity)
}

@Database(
    entities = [MessageEntity::class, PeerEntity::class, CrdtLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VravDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun crdtLogDao(): CrdtLogDao

    companion object {
        @Volatile
        private var INSTANCE: VravDatabase? = null

        fun getDatabase(context: Context): VravDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VravDatabase::class.java,
                    "vrav_messenger_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
