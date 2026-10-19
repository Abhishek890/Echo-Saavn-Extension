package dev.brahmkshatriya.echo.extension.parser

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf

import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.Logger

data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String?,
    val items: List<EchoMediaItem>,
    val moreInfo: MoreInfo?
)

data class MoreInfo(
    val api: String,
    val pageParam: String,
    val sizeParam: String,
    val defaultSize: Int
)

class HomeParser(
    private val trackParser: TrackParser,
    private val albumParser: AlbumParser,
    private val artistParser: ArtistParser,
    private val playlistParser: PlaylistParser
) : BaseParser() {

    fun parseHomeSections(response: JsonObject): List<HomeSection> {
        val modules = response["modules"]?.jsonObject ?: return emptyList()
        
        return modules.entries
            .sortedBy { (_, module) ->
                module.jsonObject["position"]?.jsonPrimitive?.intOrNull ?: 0
            }
            .mapNotNull { (key, moduleElement) ->
                val module = moduleElement.jsonObject
                val title = decodeHtml(module["title"]?.jsonPrimitive?.content ?: "")
                if (title.isBlank()) return@mapNotNull null
                
                val sectionData = response[key]?.jsonArray ?: return@mapNotNull null
                val items = sectionData.mapNotNull { parseHomeItem(it.jsonObject) }
                if (items.isEmpty()) return@mapNotNull null
                
                val subtitle = module["subtitle"]?.jsonPrimitive?.content
                
                // Extract more info
                val moreInfo = module["view_more"]?.let { viewMoreElement ->
                    if (viewMoreElement is JsonObject) {
                        val api = viewMoreElement["api"]?.jsonPrimitive?.content
                        if (!api.isNullOrBlank()) {
                            MoreInfo(
                                api = api,
                                pageParam = viewMoreElement["page_param"]?.jsonPrimitive?.content ?: "p",
                                sizeParam = viewMoreElement["size_param"]?.jsonPrimitive?.content ?: "n",
                                defaultSize = viewMoreElement["default_size"]?.jsonPrimitive?.intOrNull ?: 10
                            )
                        } else null
                    } else null
                }
                
                HomeSection(
                    id = key,
                    title = title,
                    subtitle = subtitle,
                    items = items,
                    moreInfo = moreInfo
                )
            }
    }
    
    private fun parseHomeItem(obj: JsonObject): EchoMediaItem? {
        val type = obj["type"]?.jsonPrimitive?.content ?: ""
        return when (type) {
            "song" -> trackParser.parseSongToTrack(obj)
            "album" -> albumParser.parseAlbumToAlbum(obj)
            "playlist" -> playlistParser.parsePlaylistToPlaylist(obj)
            "artist" -> artistParser.parseArtistToArtist(obj)
            else -> null
        }
    }

    fun parseMoreResponse(response: JsonObject): List<Shelf.Item> {
        val array = (response["results"] as? JsonArray)
            ?: (response["data"] as? JsonArray)
            ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            parseHomeItem(obj)?.toShelf()
        }
    }

}
