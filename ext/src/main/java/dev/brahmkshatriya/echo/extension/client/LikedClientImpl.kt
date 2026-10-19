package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.extension.SaavnDependencies
import dev.brahmkshatriya.echo.extension.storage.*

class LikeClientImpl : LikeClient {

    override suspend fun likeItem(item: EchoMediaItem, shouldLike: Boolean) {
        val settings = SaavnDependencies.settings ?: return
        when (item) {
            is Track -> if (shouldLike) LocalLikedTracksStore.add(settings, item) else LocalLikedTracksStore.remove(settings, item.id)
            is Album -> if (shouldLike) LocalLikedAlbumsStore.add(settings, item) else LocalLikedAlbumsStore.remove(settings, item.id)
            is Artist -> if (shouldLike) LocalLikedArtistsStore.add(settings, item) else LocalLikedArtistsStore.remove(settings, item.id)
            is Playlist -> if (shouldLike) LocalLikedPlaylistsStore.add(settings, item) else LocalLikedPlaylistsStore.remove(settings, item.id)
            else -> return
        }
    }

    override suspend fun isItemLiked(item: EchoMediaItem): Boolean {
        val settings = SaavnDependencies.settings ?: return false
        return when (item) {
            is Track -> LocalLikedTracksStore.contains(settings, item.id)
            is Album -> LocalLikedAlbumsStore.contains(settings, item.id)
            is Artist -> LocalLikedArtistsStore.contains(settings, item.id)
            is Playlist -> LocalLikedPlaylistsStore.contains(settings, item.id)
            else -> false
        }
    }
}
