package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.utils.Extras
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.runSafe

class RadioClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser
) : RadioClient {

    // ===== RADIO =====

    override suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio {
        if (item !is Track) {
            throw Exception("Radio only supported for tracks")
        }

        val songId = item.id
        Logger.d("RadioClient", "Creating radio for track: $songId")

        return runSafe("RadioClient", emptyRadio(item)) {
            val response = api.radio.createSongStation(songId)
            val stationId = parser.radio.parseStationId(response)
                ?: throw Exception("Failed to create station")

            Logger.d("RadioClient", "Station created: $stationId")

            Radio(
                id = "radio_$stationId",
                title = "${item.title} Radio",
                subtitle = "Similar to ${item.title}",
                cover = item.cover,
                extras = mapOf(Extras.STATION_ID to stationId)
            )
        }
    }

    // ===== LOAD TRACKS =====

    override suspend fun loadTracks(radio: Radio): Feed<Track> {
        val stationId = radio.extras[Extras.STATION_ID]
            ?: return emptyList<Track>().toFeed() as Feed<Track>

        Logger.d("RadioClient", "Loading radio tracks for station: $stationId")

        return runSafe("RadioClient", emptyList<Track>().toFeed() as Feed<Track>) {
            val response = api.radio.getSongSuggestions(stationId, limit = 20)
            val tracks = parser.radio.parseSongSuggestions(response)
            Logger.d("RadioClient", "Loaded ${tracks.size} radio tracks")
            tracks.toFeed() as Feed<Track>
        }
    }

    // ===== LOAD RADIO =====

    override suspend fun loadRadio(radio: Radio): Radio = radio

    // ===== FALLBACK =====

    private fun emptyRadio(track: Track) = Radio(
        id = "radio_${track.id}",
        title = "${track.title} Radio",
        subtitle = "Similar to ${track.title}",
        cover = track.cover,
        extras = emptyMap()
    )
}
