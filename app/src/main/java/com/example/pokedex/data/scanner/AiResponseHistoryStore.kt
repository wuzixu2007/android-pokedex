package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AiResponseHistoryEntry(
    val id: String,
    val createdAtMillis: Long,
    val source: String,
    val provider: String,
    val model: String,
    val protocol: String,
    val elapsedMillis: Long?,
    val httpStatus: Int?,
    val requestId: String?,
    val parsedName: String?,
    val error: String?,
    val rawResponse: String,
)

class AiResponseHistoryStore(context: Context) {
    private val file = File(context.filesDir, "ai-response-history.json")
    private val mutex = Mutex()
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<AiResponseHistoryEntry>> = _entries.asStateFlow()

    suspend fun append(entry: AiResponseHistoryEntry, limit: Int) = withContext(Dispatchers.IO) {
        mutex.withLock { update((listOf(entry) + _entries.value).take(limit.coerceIn(2, 20))) }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { update(_entries.value.filterNot { it.id == id }) }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { update(emptyList()) } }

    suspend fun trim(limit: Int) = withContext(Dispatchers.IO) { mutex.withLock { update(_entries.value.take(limit.coerceIn(2, 20))) } }

    private fun update(entries: List<AiResponseHistoryEntry>) {
        val payload = JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString()
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, "${file.name}.part")
        pending.writeText(payload, Charsets.UTF_8)
        if (file.exists()) file.delete()
        pending.renameTo(file)
        _entries.value = entries
    }

    private fun load(): List<AiResponseHistoryEntry> = runCatching {
        val array = JSONArray(file.readText(Charsets.UTF_8))
        List(array.length()) { index -> array.getJSONObject(index).toEntry() }
    }.getOrDefault(emptyList())

    companion object {
        fun newEntry(
            source: String, user: UserAiSettings, developer: DeveloperAiSettings,
            elapsedMillis: Long?, status: Int?, requestId: String?, parsedName: String?, error: String?, raw: String?,
        ) = AiResponseHistoryEntry(
            id = UUID.randomUUID().toString(), createdAtMillis = System.currentTimeMillis(), source = source,
            provider = user.provider.displayName, model = user.model, protocol = developer.protocol.name,
            elapsedMillis = elapsedMillis, httpStatus = status, requestId = requestId,
            parsedName = parsedName, error = error, rawResponse = raw.orEmpty(),
        )

        private fun AiResponseHistoryEntry.toJson() = JSONObject().apply {
            put("id", id); put("createdAt", createdAtMillis); put("source", source); put("provider", provider)
            put("model", model); put("protocol", protocol); put("elapsed", elapsedMillis ?: JSONObject.NULL)
            put("status", httpStatus ?: JSONObject.NULL); put("requestId", requestId ?: JSONObject.NULL)
            put("parsedName", parsedName ?: JSONObject.NULL); put("error", error ?: JSONObject.NULL); put("raw", rawResponse)
        }
        private fun JSONObject.toEntry() = AiResponseHistoryEntry(
            getString("id"), getLong("createdAt"), getString("source"), getString("provider"), getString("model"),
            getString("protocol"), optLong("elapsed").takeIf { !isNull("elapsed") }, optInt("status").takeIf { !isNull("status") },
            optString("requestId").takeIf { !isNull("requestId") }, optString("parsedName").takeIf { !isNull("parsedName") },
            optString("error").takeIf { !isNull("error") }, optString("raw"),
        )
    }
}
