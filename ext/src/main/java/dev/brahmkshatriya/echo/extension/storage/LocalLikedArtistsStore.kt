package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.Artist
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder

import dev.brahmkshatriya.echo.extension.utils.Extras

object LocalLikedArtistsStore : LocalStore<Artist>() {
    override val key = StorageKeys.LIKED_ARTISTS

    override fun serializeItem(item: Artist, json: JsonObjectBuilder) {
        json.putCommonFields(item)
    }

    override fun deserializeItem(obj: JsonObject): Artist? {
        val common = obj.parseCommonFields() ?: return null
        return Artist(
            id = common.id,
            name = common.title,  // Artist uses `name`
            subtitle = common.subtitle,
            cover = common.cover,
            extras = mapOf(Extras.PERMA_URL to common.permaUrl)
        )
    }
}
