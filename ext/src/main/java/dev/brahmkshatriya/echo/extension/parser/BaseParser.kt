package dev.brahmkshatriya.echo.extension.parser

import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder

import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.convertImageUrl
import dev.brahmkshatriya.echo.extension.utils.Extras

open class BaseParser {
    
    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    protected fun decodeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    protected fun parseArtistFromJson(obj: JsonObject): Artist? {
        // Try search format first, fallback to detail format
        val numericId = obj["id"]?.jsonPrimitive?.content
            ?: obj["artistId"]?.jsonPrimitive?.content
            ?: ""
        val permaUrl = obj["perma_url"]?.jsonPrimitive?.content
            ?: obj["urls"]?.jsonObject?.get("overview")?.jsonPrimitive?.content
            ?: ""

        return Artist(
            id = numericId,
            name = decodeHtml(
                obj["name"]?.jsonPrimitive?.content
                    ?: obj["title"]?.jsonPrimitive?.content
                    ?: ""
            ),
            cover = convertImageUrl(
                obj["image"]?.jsonPrimitive?.content 
                    ?: obj["image_url"]?.jsonPrimitive?.content
            ).toImageHolder(),
            subtitle = obj["subtitle"]?.jsonPrimitive?.content
                ?: obj["role"]?.jsonPrimitive?.content,
            isLikeable = true,
            extras = mapOf(
                Extras.PERMA_URL to permaUrl,
            )
        )
    }

    fun parseAllArtistsFromExtras(artistMapJson: String?): List<Artist> {
        if (artistMapJson.isNullOrBlank()) return emptyList()
        return try {
            val artistMap = json.parseToJsonElement(artistMapJson).jsonObject
            parseAllArtists(artistMap)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseAllArtists(artistMap: JsonObject?): List<Artist> {
        if (artistMap == null) return emptyList()

        val primary = artistMap["primary_artists"]?.jsonArray ?: emptyList()
        val featured = artistMap["featured_artists"]?.jsonArray ?: emptyList()
        val all = artistMap["artists"]?.jsonArray ?: emptyList()

        return (primary + featured + all)
            .distinctBy { it.jsonObject["id"]?.jsonPrimitive?.content }
            .mapNotNull { parseArtistFromJson(it.jsonObject) }
    }

    protected fun parsePrimaryArtists(artistMap: JsonObject?): List<Artist> {
        val primary = artistMap?.get("primary_artists")?.jsonArray ?: return emptyList()
        return primary.mapNotNull { parseArtistFromJson(it.jsonObject) }
    }

    fun parseOtherArtists(artistMap: JsonObject?): List<Artist> {
        if (artistMap == null) return emptyList()
        
        val primaryIds = artistMap["primary_artists"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            ?.toSet()
            ?: emptySet()
        
        val all = artistMap["artists"]?.jsonArray ?: return emptyList()
        
        return all
            .filterNot { it.jsonObject["id"]?.jsonPrimitive?.content in primaryIds }
            .mapNotNull { parseArtistFromJson(it.jsonObject) }
    }

    // Reconstruct "other artists" from extras
    fun parseOtherArtistsFromExtras(artistMapJson: String?): List<Artist> {
        if (artistMapJson.isNullOrBlank()) return emptyList()
        return try {
            val artistMap = json.parseToJsonElement(artistMapJson).jsonObject
            parseOtherArtists(artistMap)
        } catch (e: Exception) {
            emptyList()
        }
    }

}
