package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.Date
import dev.brahmkshatriya.echo.common.models.Playlist
import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.Extras

object LocalPlaylistStore : LocalStore<Playlist>() {
    override val key = StorageKeys.LOCAL_PLAYLISTS

    override fun serializeItem(item: Playlist, json: JsonObjectBuilder) {
        json.putCommonFields(item)
        item.description?.let { json.put("description", it) }
        json.put("isPrivate", item.isPrivate)
        item.creationDate?.let { json.put("creationDate", it.epochTimeMs) }
    }

    override fun deserializeItem(obj: JsonObject): Playlist? {
        val common = obj.parseCommonFields() ?: return null
        return Playlist(
            id = common.id,
            title = common.title,
            subtitle = common.subtitle,
            cover = common.cover,
            isEditable = true,
            isPrivate = obj["isPrivate"]?.jsonPrimitive?.booleanOrNull ?: false,
            description = obj["description"]?.jsonPrimitive?.content,
            creationDate = obj["creationDate"]?.jsonPrimitive?.longOrNull?.let { Date(it) },
            extras = mapOf(Extras.PERMA_URL to common.permaUrl)
        )
    }
}
