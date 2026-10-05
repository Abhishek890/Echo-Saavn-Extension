package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

import dev.brahmkshatriya.echo.extension.utils.Extras

object LocalRecentStore : LocalStore<Track>() {
    override val key = StorageKeys.RECENT_TRACKS
    override val maxSize = StorageLimits.RECENT_MAX

    override fun getSortKey(): Long = System.currentTimeMillis()

    override fun serializeItem(item: Track, json: JsonObjectBuilder) {
        json.putCommonFields(item)
    }

    override fun deserializeItem(obj: JsonObject): Track? {
        val common = obj.parseCommonFields() ?: return null
        return Track(
            id = common.id,
            title = common.title,
            subtitle = common.subtitle,
            cover = common.cover,
            extras = mapOf(Extras.PERMA_URL to common.permaUrl),
            streamables = emptyList()
        )
    }
}
