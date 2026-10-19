package dev.brahmkshatriya.echo.extension.service

import dev.brahmkshatriya.echo.common.models.Track

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.runSafe

class RadioService(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser,
    private val scope: CoroutineScope
) {
    // ===== CACHE (single entry) =====

    private var cachedSongId: String? = null
    private var cachedStationId: String? = null
    private var cachedTracks: List<Track>? = null

    // ===== IN-FLIGHT (per-id) =====

    private val inflightStationLoads = ConcurrentHashMap<String, Deferred<String?>>()
    private val inflightTracksLoads = ConcurrentHashMap<String, Deferred<List<Track>>>()

    // ===== PUBLIC API =====

    suspend fun getStationId(songId: String): String? {
        // Cache hit
        if (cachedSongId == songId) return cachedStationId

        // Atomic check + start
        val deferred = inflightStationLoads.computeIfAbsent(songId) {
            scope.async {
                val stationId = runSafe("RadioService", null) {
                    val response = api.radio.createSongStation(songId)
                    parser.radio.parseStationId(response)
                }
                if (stationId != null) {
                    cachedSongId = songId
                    cachedStationId = stationId
                    cachedTracks = null
                }
                Logger.d("RadioService", "Station for $songId: $stationId")
                stationId
            }
        }

        return try {
            deferred.await()
        } finally {
            inflightStationLoads.remove(songId)
        }
    }

    suspend fun loadRadioTracks(stationId: String, limit: Int = 20): List<Track> {
        // Cache hit
        if (cachedStationId == stationId && cachedTracks != null) {
            Logger.d("RadioService", "Tracks cache hit for $stationId (${cachedTracks!!.size} tracks)")
            return cachedTracks!!
        }

        // Atomic check + start
        val deferred = inflightTracksLoads.computeIfAbsent(stationId) {
            scope.async {
                val tracks = runSafe("RadioService", null) {
                    val response = api.radio.getSongSuggestions(stationId, limit = limit)
                    parser.radio.parseSongSuggestions(response)
                }
                if (tracks != null) {
                    cachedTracks = tracks
                    Logger.d("RadioService", "Loaded and cached ${tracks.size} tracks for $stationId")
                }
                tracks ?: emptyList()
            }
        }

        return try {
            deferred.await()
        } finally {
            inflightTracksLoads.remove(stationId)
        }
    }

    suspend fun getRadioTracks(songId: String, limit: Int = 20): List<Track> {
        val stationId = getStationId(songId) ?: return emptyList()
        return loadRadioTracks(stationId, limit)
    }
}
