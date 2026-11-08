package dev.brahmkshatriya.echo.extension.parser

import kotlinx.serialization.json.*

class LibraryParser : BaseParser() {

    private fun parseIds(library: JsonObject, type: String): List<String> {
        val entries = library[type]?.jsonArray ?: return emptyList()
        return when (type) {
            "playlist" -> entries.mapNotNull {
                it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }
            else -> entries.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
    }

    fun parseSongIds(library: JsonObject): List<String> = parseIds(library, "song")

    fun parseAlbumIds(library: JsonObject): List<String> = parseIds(library, "album")

    fun parseArtistIds(library: JsonObject): List<String> = parseIds(library, "artist")

    fun parsePlaylistIds(library: JsonObject): List<String> = parseIds(library, "playlist")

}
