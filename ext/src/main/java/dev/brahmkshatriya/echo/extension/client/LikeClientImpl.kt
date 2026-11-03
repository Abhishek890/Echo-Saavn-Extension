package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.helpers.ClientException

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.SaavnDependencies
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.requireSuccess

class LikeClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser,
) : LikeClient {

    override suspend fun isItemLiked(item: EchoMediaItem): Boolean {
        return try {
            Logger.d("LikeClient", "isItemLiked: ${item::class.simpleName} id=${item.id}")

            val library = SaavnDependencies.getLibraryShared()
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
            SaavnDependencies.invalidateLibraryCache()
        } catch (e: ClientException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("LikeClient", "Failed to ${if (shouldLike) "like" else "unlike"} $type/$id", e)
        }
    }

}
