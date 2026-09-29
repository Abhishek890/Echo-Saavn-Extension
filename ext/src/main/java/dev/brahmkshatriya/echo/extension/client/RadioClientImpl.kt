package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track

import dev.brahmkshatriya.echo.extension.utils.Extras
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.runSafe
import dev.brahmkshatriya.echo.extension.service.RadioService
import dev.brahmkshatriya.echo.extension.SaavnDependencies

/*
 * We are supporting radio only for tracks
 * Adding radio support for tracks enables endless queue
 * Echo adds songs to the queue based on radio from last song in current queue
 */
class RadioClientImpl(
    private val radioService: RadioService
) : RadioClient {

    override suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio {
        if (!SaavnDependencies.isAutoRadioEnabled()) {
            return Radio(
                id = "radio_disabled_${item.id}",
                title = "",
                extras = emptyMap()
            )
        }

        if (item !is Track) throw Exception("Radio only supported for tracks")

        val songId = item.id
        Logger.d("RadioClient", "Creating radio for track: $songId")

        val stationId = radioService.getStationId(songId)

        return Radio(
            id = "radio_${item.id}",
            title = "${item.title} Radio",
            subtitle = "Similar to ${item.title}",
            cover = item.cover,
            extras = if (stationId != null) mapOf(Extras.STATION_ID to stationId) else emptyMap()
        )
    }

    override suspend fun loadTracks(radio: Radio): Feed<Track> {
        val stationId = radio.extras[Extras.STATION_ID]
            ?: return emptyList<Track>().toFeed() as Feed<Track>

        val tracks = radioService.loadRadioTracks(stationId, limit = 20)
        return tracks.toFeed() as Feed<Track>
    }

    override suspend fun loadRadio(radio: Radio): Radio = radio
}
