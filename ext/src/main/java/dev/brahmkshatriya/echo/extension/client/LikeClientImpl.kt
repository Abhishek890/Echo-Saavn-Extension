package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.helpers.ClientException

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.requireSuccess

class LikeClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser,
    private val scope: CoroutineScope,
) : LikeClient {

    // Library cache — shared across isItemLiked and likeItem so that
    // rapid state re-checks don't re-fetch, and mutations invalidate immediately.
    private var cachedLibrary: JsonObject? = null
    private var cachedLibraryTime: Long = 0
    private val inflightLibraryLoads = ConcurrentHashMap<String, Deferred<JsonObject>>()

    private suspend fun getLibraryShared(): JsonObject {
        val now = System.currentTimeMillis()
        cachedLibrary?.let {
            if (now - cachedLibraryTime < LIBRARY_TTL_MS) {
                Logger.d("LikeClient", "getLibraryShared: cache hit (age=${now - cachedLibraryTime}ms)")
                return it
            }
        }

        val deferred = inflightLibraryLoads.computeIfAbsent("library") {
            scope.async { api.library.getAll() }
        }
        val library = try {
            deferred.await()
        } finally {
            inflightLibraryLoads.remove("library")
        }

        cachedLibrary = library
        cachedLibraryTime = System.currentTimeMillis()
        Logger.d("LikeClient", "getLibraryShared: fetched (next TTL ${LIBRARY_TTL_MS}ms)")
        return library
    }

    private fun invalidateLibraryCache() {
        cachedLibrary = null
        cachedLibraryTime = 0
    }

    private companion object {
        private const val LIBRARY_TTL_MS = 2_000L
    }

    override suspend fun isItemLiked(item: EchoMediaItem): Boolean {
        return try {
            Logger.d("LikeClient", "isItemLiked: ${item::class.simpleName} id=${item.id}")

            val library = getLibraryShared()
            val likedIds = when (item) {
                is Track -> parser.library.parseSongIds(library)
                is Album -> parser.library.parseAlbumIds(library)
                else -> return false
            }
            item.id in likedIds
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("LikeClient", "Failed to check like status for ${item.id}", e)
            false
        }
    }

    override suspend fun likeItem(item: EchoMediaItem, shouldLike: Boolean) {
        val (id, type) = when (item) {
            is Track -> item.id to "song"
            is Album -> item.id to "album"
            else -> return
        }
        try {
            val response = if (shouldLike) api.library.add(id, type)
                           else api.library.delete(id, type)
            response.requireSuccess("library.${if (shouldLike) "add" else "delete"}")
            invalidateLibraryCache()
        } catch (e: ClientException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("LikeClient", "Failed to ${if (shouldLike) "like" else "unlike"} $type/$id", e)
        }
    }

}
