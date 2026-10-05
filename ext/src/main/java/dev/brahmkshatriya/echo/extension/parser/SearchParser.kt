package dev.brahmkshatriya.echo.extension.parser

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder

import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.convertImageUrl
import dev.brahmkshatriya.echo.extension.utils.Extras

class SearchParser : BaseParser() {

    fun parseSearchAll(response: JsonObject): SearchAllResult {
        return SearchAllResult(
            topQuery = parseSection(response["topquery"]).firstOrNull(),
            songs = parseSection(response["songs"]),
            albums = parseSection(response["albums"]),
            artists = parseSection(response["artists"]),
            playlists = parseSection(response["playlists"])
        )
    }

    private fun parseSection(element: JsonElement?): List<EchoMediaItem> {
        val data = element?.jsonObject?.get("data")?.jsonArray ?: return emptyList()
        return data.mapNotNull { parseSearchItem(it.jsonObject) }
    }

    private fun parseSearchItem(obj: JsonObject): EchoMediaItem? {
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        val permaUrl = obj["perma_url"]?.jsonPrimitive?.content ?: ""
        val id = obj["id"]?.jsonPrimitive?.content ?: return null

        val title = decodeHtml(obj["title"]?.jsonPrimitive?.content ?: "")
        val subtitle = decodeHtml(obj["subtitle"]?.jsonPrimitive?.content ?: "")
        val cover = convertImageUrl(obj["image"]?.jsonPrimitive?.content).toImageHolder()
        val extras = mapOf(Extras.PERMA_URL to permaUrl)

        return when (type) {
            "song" -> Track(
                id = id,
                title = title,
                subtitle = subtitle,
                cover = cover,
                extras = extras,
                streamables = emptyList()
            )
            "album" -> Album(
                id = id,
                title = title,
                subtitle = subtitle,
                cover = cover,
                extras = extras
            )
            "artist" -> Artist(
                id = id,
                name = title,
                subtitle = subtitle,
                cover = cover,
                extras = extras
            )
            "playlist" -> Playlist(
                id = id,
                title = title,
                subtitle = subtitle,
                cover = cover,
                isEditable = false,
                extras = extras
            )
            else -> null
        }
    }
}

data class SearchAllResult(
    val topQuery: EchoMediaItem?,
    val songs: List<EchoMediaItem>,
    val albums: List<EchoMediaItem>,
    val artists: List<EchoMediaItem>,
    val playlists: List<EchoMediaItem>
)
