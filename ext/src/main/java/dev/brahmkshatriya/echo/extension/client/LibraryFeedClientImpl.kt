package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.SaavnDependencies
import dev.brahmkshatriya.echo.extension.storage.*

class LibraryFeedClientImpl : LibraryFeedClient {

    override suspend fun loadLibraryFeed(): Feed<Shelf> {
        val settings = SaavnDependencies.settings
            ?: return emptyList<Shelf>().toFeed()

        return buildLikedFeed(settings).toFeed()
    }

    private fun buildLikedFeed(settings: Settings): List<Shelf> {
        val shelves = mutableListOf<Shelf>()

        val recent = LocalRecentStore.getAll(settings)
        if (recent.isNotEmpty()) {
            // TODO: Change to Lists.Tracks when No item found issue is fixed in Echo
            shelves.add(Shelf.Lists.Items(
                id = "recent_tracks",
                title = "Recently Played",
                list = recent,
                subtitle = "${recent.size} tracks"
            ))
        }

        val myPlaylists = LocalPlaylistStore.getAll(settings)
        if (myPlaylists.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "my_playlists",
                title = "My Playlists",
                list = myPlaylists,
                subtitle = "${myPlaylists.size} playlists"
            ))
        }

        val tracks = LocalLikedTracksStore.getAll(settings)
        if (tracks.isNotEmpty()) {
            // TODO: Change to Lists.Tracks when No item found issue is fixed in Echo
            shelves.add(Shelf.Lists.Items(
                id = "liked_tracks",
                title = "Liked Songs",
                list = tracks,
                subtitle = "${tracks.size} tracks"
            ))
        }

        val albums = LocalLikedAlbumsStore.getAll(settings)
        if (albums.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "liked_albums",
                title = "Liked Albums",
                list = albums,
                subtitle = "${albums.size} albums"
            ))
        }

        val artists = LocalLikedArtistsStore.getAll(settings)
        if (artists.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "liked_artists",
                title = "Liked Artists",
                list = artists,
                subtitle = "${artists.size} artists"
            ))
        }

        val playlists = LocalLikedPlaylistsStore.getAll(settings)
        if (playlists.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "liked_playlists",
                title = "Liked Playlists",
                list = playlists,
                subtitle = "${playlists.size} playlists"
            ))
        }

        return shelves
    }
}
