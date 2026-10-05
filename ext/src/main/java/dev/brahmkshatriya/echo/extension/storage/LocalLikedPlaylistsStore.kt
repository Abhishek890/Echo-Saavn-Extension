package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.Playlist
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

import dev.brahmkshatriya.echo.extension.utils.Extras

object LocalLikedPlaylistsStore : LocalStore<Playlist>() {
    override val key = StorageKeys.LIKED_PLAYLISTS

    override fun serializeItem(item: Playlist, json: JsonObjectBuilder) {
        json.putCommonFields(item)
    }

    override fun deserializeItem(obj: JsonObject): Playlist? {
        val common = obj.parseCommonFields() ?: return null
        return Playlist(
            id = common.id,
            title = common.title,
            subtitle = common.subtitle,
            cover = common.cover,
            isEditable = false,
            extras = mapOf(Extras.PERMA_URL to common.permaUrl)
        )
    }
}
