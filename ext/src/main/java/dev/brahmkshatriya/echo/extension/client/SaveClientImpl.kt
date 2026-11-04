package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.SaveClient
import dev.brahmkshatriya.echo.common.models.*

import dev.brahmkshatriya.echo.extension.SaavnSettings
import dev.brahmkshatriya.echo.extension.storage.*

class SaveClientImpl : SaveClient {

    override suspend fun saveToLibrary(item: EchoMediaItem, shouldSave: Boolean) {
        val settings = SaavnSettings.settings ?: return
        when (item) {
            is Track -> if (shouldSave) LocalSavedTracksStore.add(settings, item)
                        else LocalSavedTracksStore.remove(settings, item.id)
            is Album -> if (shouldSave) LocalSavedAlbumsStore.add(settings, item)
                        else LocalSavedAlbumsStore.remove(settings, item.id)
            is Artist -> if (shouldSave) LocalSavedArtistsStore.add(settings, item)
                         else LocalSavedArtistsStore.remove(settings, item.id)
            is Playlist -> if (shouldSave) LocalSavedPlaylistsStore.add(settings, item)
                           else LocalSavedPlaylistsStore.remove(settings, item.id)
            else -> return
        }
    }

    override suspend fun isItemSaved(item: EchoMediaItem): Boolean {
        val settings = SaavnSettings.settings ?: return false
        return when (item) {
            is Track -> LocalSavedTracksStore.contains(settings, item.id)
            is Album -> LocalSavedAlbumsStore.contains(settings, item.id)
            is Artist -> LocalSavedArtistsStore.contains(settings, item.id)
            is Playlist -> LocalSavedPlaylistsStore.contains(settings, item.id)
            else -> false
        }
    }
}
