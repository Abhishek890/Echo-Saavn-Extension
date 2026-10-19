package dev.brahmkshatriya.echo.extension.parser

import dev.brahmkshatriya.echo.common.models.Track

import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.Logger

class RadioParser(
    private val trackParser: TrackParser
) : BaseParser() {

    fun parseStationId(obj: JsonObject): String? {
        return try {
            val stationId = obj["stationid"]?.jsonPrimitive?.content
            Logger.d("RadioParser", "Extracted station ID: $stationId")
            stationId
        } catch (e: Exception) {
            Logger.e("RadioParser", "Failed to parse station ID: ${e.message}", e)
            null
        }
    }

    fun parseSongSuggestions(obj: JsonObject): List<Track> {
        return try {            
            val songs = mutableListOf<Track>()
            
            obj.forEach { (key, value) ->
                if (key != "stationid") {
                    try {
                        val songObject = value.jsonObject["song"]?.jsonObject
                        if (songObject != null) {
                            val song = trackParser.parseSongToTrack(songObject)
                            song?.let { songs.add(it) }
                        }
                    } catch (e: Exception) {
                        Logger.e("RadioParser", "Failed to parse song at key $key: ${e.message}", e)
                    }
                }
            }
            
            Logger.d("RadioParser", "Successfully parsed ${songs.size} song suggestions from song station")
            songs
        } catch (e: Exception) {
            Logger.e("RadioParser", "Failed to parse song suggestions: ${e.message}", e)
            emptyList()
        }
    }
}
