package com.rapidresq.app.data.offline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class QueueItem(
    val id: Long,
    val actionType: String,
    val payloadJson: String,
    val createdAt: Long
)

class OfflineDbHelper(context: Context) : SQLiteOpenHelper(context, "rapidresq_offline_queue.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE pending_actions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS pending_actions")
        onCreate(db)
    }
}

class OfflineQueueManager(context: Context) {
    private val dbHelper = OfflineDbHelper(context)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingCount = MutableStateFlow(getRecordCount())
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private var activeExecutor: (suspend (String, String) -> Boolean)? = null

    init {
        registerNetworkCallback()
    }

    private fun checkInitialConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }
        })
    }

    fun setQueueExecutor(executor: suspend (String, String) -> Boolean) {
        activeExecutor = executor
    }

    private fun getRecordCount(): Int {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT COUNT(*) FROM pending_actions", null)
            var count = 0
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
            cursor.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    suspend fun enqueueAction(actionType: String, payloadJson: String) = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("action_type", actionType)
                put("payload_json", payloadJson)
                put("created_at", System.currentTimeMillis())
            }
            db.insert("pending_actions", null, values)
            _pendingCount.value = getRecordCount()
        } catch (e: Exception) {
            // Ignore DB write errors
        }
    }

    suspend fun flushPendingQueue(executor: suspend (actionType: String, payloadJson: String) -> Boolean): Int = withContext(Dispatchers.IO) {
        var processedCount = 0
        try {
            val db = dbHelper.writableDatabase
            val cursor = db.query(
                "pending_actions",
                arrayOf("id", "action_type", "payload_json", "created_at"),
                null, null, null, null, "id ASC"
            )

            val items = mutableListOf<QueueItem>()
            while (cursor.moveToNext()) {
                items.add(
                    QueueItem(
                        id = cursor.getLong(0),
                        actionType = cursor.getString(1),
                        payloadJson = cursor.getString(2),
                        createdAt = cursor.getLong(3)
                    )
                )
            }
            cursor.close()

            for (item in items) {
                val success = executor(item.actionType, item.payloadJson)
                if (success) {
                    db.delete("pending_actions", "id = ?", arrayOf(item.id.toString()))
                    processedCount++
                } else {
                    break // Stop playing on first failure so FIFO order is preserved
                }
            }

            _pendingCount.value = getRecordCount()
        } catch (e: Exception) {
            // Ignore queue processing errors
        }
        return@withContext processedCount
    }
}
