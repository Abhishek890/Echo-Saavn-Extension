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
            isRadioSupported = false,
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

    protected fun parsePrimaryArtists(artistMap: JsonObject?): List<Artist> {
        val primary = artistMap?.get("primary_artists")?.jsonArray ?: return emptyList()
        return primary.mapNotNull { parseArtistFromJson(it.jsonObject) }
    }

    /**
     * Parses a list of artist JSON entries, deduplicating by id and combining
     * multiple roles (e.g. "singer", "music") into a comma-separated subtitle.
     *
     * If an artist appears once, its original subtitle/role is preserved.
     * If it appears multiple times with different roles, the subtitle becomes
     * `"role1, role2, ..."`.
     */
    private fun parseArtistEntries(entries: List<JsonObject>): List<Artist> {
        return entries
            .groupBy { it["id"]?.jsonPrimitive?.content }
            .mapNotNull { (_, occurrences) ->
                val first = parseArtistFromJson(occurrences.first()) ?: return@mapNotNull null
                val roles = occurrences
                    .mapNotNull { it["role"]?.jsonPrimitive?.content }
                    .distinct()
                if (roles.size > 1) {
                    first.copy(subtitle = roles.joinToString(", "))
                } else {
                    first
                }
            }
    }

    fun parseAllArtists(artistMap: JsonObject?): List<Artist> {
        if (artistMap == null) return emptyList()
        val primary = artistMap["primary_artists"]?.jsonArray ?: emptyList()
        val featured = artistMap["featured_artists"]?.jsonArray ?: emptyList()
        val all = artistMap["artists"]?.jsonArray ?: emptyList()
        return parseArtistEntries((primary + featured + all).map { it.jsonObject })
    }

    fun parseOtherArtists(artistMap: JsonObject?): List<Artist> {
        if (artistMap == null) return emptyList()
        val primaryIds = artistMap["primary_artists"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            ?.toSet()
            ?: emptySet()
        val all = artistMap["artists"]?.jsonArray ?: return emptyList()
        return parseArtistEntries(
            all.map { it.jsonObject }
                .filterNot { it["id"]?.jsonPrimitive?.content in primaryIds }
        )
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

    protected fun parseModuleLanguage(response: JsonObject, moduleKey: String): String? =
        response["modules"]?.jsonObject
            ?.get(moduleKey)?.jsonObject
            ?.get("source_params")?.jsonObject
            ?.get("entity_language")?.jsonPrimitive?.contentOrNull

}
