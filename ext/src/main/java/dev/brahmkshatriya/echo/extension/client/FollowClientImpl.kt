package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.FollowClient
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.helpers.ClientException

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.requireSuccess

class FollowClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser,
) : FollowClient {

    override suspend fun isFollowing(item: EchoMediaItem): Boolean {
        return try {
            Logger.d("FollowClient", "isFollowing: ${item::class.simpleName} id=${item.id}")

            val library = api.library.getAll()
            val followedIds = when (item) {
                is Artist -> parser.library.parseArtistIds(library)
                is Playlist -> parser.library.parsePlaylistIds(library)
                else -> return false
            }
            item.id in followedIds
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("FollowClient", "Failed to check follow status for ${item.id}", e)
            false
        }
    }

    override suspend fun getFollowersCount(item: EchoMediaItem): Long? = null

    override suspend fun followItem(item: EchoMediaItem, shouldFollow: Boolean) {
        val (id, type) = when (item) {
            is Artist -> item.id to "artist"
            is Playlist -> item.id to "playlist"
            else -> return
        }
        try {
            val response = if (shouldFollow) api.follow.follow(id, type)
                           else api.follow.unfollow(id, type)
            response.requireSuccess("social.${if (shouldFollow) "follow" else "unfollow"}")
        } catch (e: ClientException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("FollowClient", "Failed to ${if (shouldFollow) "follow" else "unfollow"} $type/$id", e)
        }
    }
}
