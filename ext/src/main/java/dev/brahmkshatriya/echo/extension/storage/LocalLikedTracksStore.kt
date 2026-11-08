package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

import dev.brahmkshatriya.echo.extension.utils.Extras

object LocalLikedTracksStore : LocalStore<Track>() {
    override val key = StorageKeys.LIKED_TRACKS

    override fun serializeItem(item: Track, json: JsonObjectBuilder) {
        json.putCommonFields(item)
        json.putRichTrackFields(item)
    }

    override fun deserializeItem(obj: JsonObject): Track? {
        val common = obj.parseCommonFields() ?: return null
        return obj.parseRichTrackFields(common)
    }
}
