package dev.brahmkshatriya.echo.extension.service

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Track

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.getToken
import dev.brahmkshatriya.echo.extension.utils.runSafe

class AlbumService(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser,
    private val scope: CoroutineScope
) {
    // ===== CACHE (single entry) =====

    private var cachedAlbumId: String? = null
    private var cachedResponse: JsonObject? = null
    private var cachedAlbum: Album? = null
    private var cachedTracks: List<Track>? = null

    // ===== IN-FLIGHT =====

    private val inflightLoads = ConcurrentHashMap<String, Deferred<Album>>()

    // ===== PUBLIC API =====

    suspend fun loadAlbum(album: Album): Album {
        // Cache hit
        if (cachedAlbumId == album.id && cachedAlbum != null) {
            return cachedAlbum!!
        }

        // In-flight check + start
        val deferred = inflightLoads.computeIfAbsent(album.id) {
            scope.async {
                val token = album.getToken()
                val response = api.album.getDetails(token)
                val parsedAlbum = parser.album.parseAlbumToAlbum(response) ?: album
                val tracks = parser.album.parseAlbumTracks(response)

                cachedAlbumId = album.id
                cachedResponse = response
                cachedAlbum = parsedAlbum
                cachedTracks = tracks

                Logger.d("AlbumService", "Loaded album ${album.id}: ${tracks.size} tracks")
                parsedAlbum
            }
        }

        return try {
            deferred.await()
        } finally {
            inflightLoads.remove(album.id)
        }
    }

    fun getCachedTracks(albumId: String): List<Track>? {
        return if (cachedAlbumId == albumId) cachedTracks else null
    }

    fun getCachedResponse(albumId: String): JsonObject? {
        return if (cachedAlbumId == albumId) cachedResponse else null
    }
}
