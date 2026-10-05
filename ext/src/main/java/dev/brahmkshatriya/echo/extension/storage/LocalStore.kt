package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.settings.Settings
import kotlinx.serialization.json.*

abstract class LocalStore<T : EchoMediaItem> {
    protected val json = Json { ignoreUnknownKeys = true; isLenient = true }

    protected abstract val key: String
    protected open val maxSize: Int = Int.MAX_VALUE

    protected abstract fun serializeItem(item: T, json: JsonObjectBuilder)
    protected abstract fun deserializeItem(obj: JsonObject): T?

    protected open fun getSortKey(): Long = System.currentTimeMillis()

    // ===== PUBLIC API =====

    fun getAll(settings: Settings): List<T> {
        val set = settings.getStringSet(key) ?: return emptyList()
        return set
            .mapNotNull { parseEntry(it) }
            .sortedByDescending { it.second }
            .take(maxSize)
            .map { it.first }
    }

    fun contains(settings: Settings, itemId: String): Boolean {
        val set = settings.getStringSet(key) ?: return false
        return set.any { parseItemId(it) == itemId }
    }

    fun add(settings: Settings, item: T) {
        val set = settings.getStringSet(key)?.toMutableSet() ?: mutableSetOf()
        set.removeIf { parseItemId(it) == item.id }
        set.add(serializeEntry(item, getSortKey()))
        settings.putStringSet(key, trim(set))
    }

    fun remove(settings: Settings, itemId: String) {
        val set = settings.getStringSet(key)?.toMutableSet() ?: return
        set.removeIf { parseItemId(it) == itemId }
        settings.putStringSet(key, set)
    }

    fun clear(settings: Settings) {
        settings.putStringSet(key, emptySet())
    }

    // ===== INTERNAL =====

    private fun serializeEntry(item: T, sortKey: Long): String {
        return buildJsonObject {
            serializeItem(item, this)
            put("sortKey", sortKey)
        }.toString()
    }

    private fun parseEntry(jsonString: String): Pair<T, Long>? {
        return try {
            val obj = json.parseToJsonElement(jsonString).jsonObject
            val item = deserializeItem(obj) ?: return null
            val sortKey = obj["sortKey"]?.jsonPrimitive?.longOrNull ?: 0L
            item to sortKey
        } catch (e: Exception) {
            null
        }
    }

    private fun parseItemId(jsonString: String): String? {
        return try {
            json.parseToJsonElement(jsonString).jsonObject["id"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun trim(set: MutableSet<String>): Set<String> {
        if (set.size <= maxSize) return set
        return set
            .mapNotNull { parseEntry(it) }
            .sortedByDescending { it.second }
            .take(maxSize)
            .map { serializeEntry(it.first, it.second) }
            .toSet()
    }
}
