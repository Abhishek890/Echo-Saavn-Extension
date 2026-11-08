package dev.brahmkshatriya.echo.extension.parser

import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Artist

import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.convertImageUrl
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.Extras

class PlaylistParser(
    private val trackParser: TrackParser
) : BaseParser() {

    fun parsePlaylistToPlaylist(obj: JsonObject): Playlist? {
        val id = obj["id"]?.jsonPrimitive?.content ?: return null

        val image = obj["image"]?.jsonPrimitive?.content ?: ""
        val songCount = obj["list_count"]?.jsonPrimitive?.content ?: "0"
        val moreInfo = obj["more_info"]?.jsonObject

        return Playlist(
            id = id,
            title = decodeHtml(obj["title"]?.jsonPrimitive?.content ?: ""),
            isEditable = false,
            isPrivate = moreInfo?.get("share")?.jsonPrimitive?.content == "0",
            cover = convertImageUrl(image).toImageHolder(),
            trackCount = songCount.toLongOrNull(),
            subtitle = decodeHtml(obj["subtitle"]?.jsonPrimitive?.content ?: ""),
            isRadioSupported = false,
            isFollowable = true,
            extras = mapOf(
                Extras.PERMA_URL to (obj["perma_url"]?.jsonPrimitive?.content ?: ""),
                "uid" to (moreInfo?.get("uid")?.jsonPrimitive?.content ?: ""),
                "contents" to (moreInfo?.get("contents")?.jsonPrimitive?.contentOrNull ?: ""),
            )
        )
    }

    fun parsePlaylistTracks(obj: JsonObject): List<Track> {
        // JioSaavn is inconsistent: an empty playlist returns "list": [] in some
        // responses and "list": "" in others. Accept both as "empty".
        val listElement = obj["list"] as? JsonArray ?: return emptyList()
        return listElement.mapNotNull {
            (it as? JsonObject)?.let(trackParser::parseSongToTrack)
        }
    }

    fun parsePlaylistResults(obj: JsonObject): List<Playlist> {
        val results = obj["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { parsePlaylistToPlaylist(it.jsonObject) }
    }

    fun parsePlaylistDetails(obj: JsonObject): Playlist? {
        return try {
            parsePlaylistToPlaylist(obj)
        } catch (e: Exception) {
            Logger.e("PlaylistParser", "Failed to parse playlist details: ${e.message}", e)
            null
        }
    }

    /**
     * Parses a `playlist.create` response into a [Playlist].
     *
     * The create response has the shape:
     *
     *     {
     *       "status": "ok",
     *       "details": { ...playlist object... }
     *     }
     *
     * The `details` object is a playlist in a shape similar to — but not
     * guaranteed identical to — what `playlist.getDetails` returns. This function
     * handles the create-specific fixups:
     *
     * - Extracts the playlist from `.details` (returns null if absent).
     * - Marks the result as editable.
     *
     * Callers must still handle the case where `parseCreatedPlaylist` returns null
     * (malformed response) by throwing a user-visible error.
     *
     * @param response the full `playlist.create` response body.
     * @return the parsed [Playlist] marked as editable, or `null` if `.details`
     *   is missing or unparseable.
     */
    fun parseCreatedPlaylist(response: JsonObject): Playlist? {
        val details = response["details"] as? JsonObject ?: return null
        val base = parsePlaylistToPlaylist(details) ?: return null

        return base.copy(
            isEditable = true
        )
    }

    fun parsePlaylistArtists(response: JsonObject): List<Artist> {
        val artists = response["more_info"]?.jsonObject
            ?.get("artists")?.jsonArray
            ?: return emptyList()
        
        return artists
            .distinctBy { it.jsonObject["id"]?.jsonPrimitive?.content }
            .mapNotNull { parseArtistFromJson(it.jsonObject) }
    }

    fun parseTrendingLanguage(response: JsonObject): String? =
        parseModuleLanguage(response, "currentlyTrendingPlaylists")

}
