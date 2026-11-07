package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.common.helpers.ClientException

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.SaavnSession
import dev.brahmkshatriya.echo.extension.Saavn
import dev.brahmkshatriya.echo.extension.SaavnSettings
import dev.brahmkshatriya.echo.extension.storage.*
import dev.brahmkshatriya.echo.extension.utils.Logger

class LibraryFeedClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser,
) : LibraryFeedClient {

    override suspend fun loadLibraryFeed(): Feed<Shelf> {
        val tabs = buildList {
            add(Tab(id = "local", title = "Local"))
            if (SaavnSession.cookie != null) {
                add(Tab(id = "saavn", title = "JioSaavn"))
            }
        }

        return Feed(tabs) { tab ->
            // Record the active tab so createPlaylist can honour the user's context.
            Saavn.currentLibraryTabId = tab?.id ?: "local"
            when (tab?.id) {
                "saavn" -> loadSaavnTab()
                else -> loadLocalTab()
            }
        }
    }

    // ====== LOCAL TAB ======

    private fun loadLocalTab(): Feed.Data<Shelf> {
        val settings = SaavnSettings.settings
        val shelves = if (settings != null) buildLocalShelves(settings) else emptyList()
        return shelves.toFeedData()
    }

    private fun ensureSavedTracksPlaylistExists(settings: Settings) {
        val existing = LocalPlaylistStore.getAll(settings)
        if (existing.none { it.id == Prefixes.SAVED_TRACKS_PLAYLIST_ID }) {
            LocalPlaylistStore.add(settings, Playlist(
                id = Prefixes.SAVED_TRACKS_PLAYLIST_ID,
                title = "Saved Songs",
                isEditable = false,
            ))
        }
    }

    private fun buildLocalShelves(settings: Settings): List<Shelf> {
        val shelves = mutableListOf<Shelf>()

        val recent = LocalRecentStore.getAll(settings)
        if (recent.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "recent_tracks",
                title = "Recently Played",
                list = recent,
                subtitle = "${recent.size} tracks"
            ))
        }

        ensureSavedTracksPlaylistExists(settings)
        val myPlaylists = LocalPlaylistStore.getAll(settings)
        if (myPlaylists.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "my_playlists",
                title = "My Playlists",
                list = myPlaylists,
                subtitle = "${myPlaylists.size} playlists"
            ))
        }

        val tracks = LocalSavedTracksStore.getAll(settings)
        if (tracks.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "Saved_tracks",
                title = "Saved Songs",
                list = tracks,
                subtitle = "${tracks.size} tracks"
            ))
        }

        val albums = LocalSavedAlbumsStore.getAll(settings)
        if (albums.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "Saved_albums",
                title = "Saved Albums",
                list = albums,
                subtitle = "${albums.size} albums"
            ))
        }

        val artists = LocalSavedArtistsStore.getAll(settings)
        if (artists.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "Saved_artists",
                title = "Saved Artists",
                list = artists,
                subtitle = "${artists.size} artists"
            ))
        }

        val playlists = LocalSavedPlaylistsStore.getAll(settings)
        if (playlists.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "Saved_playlists",
                title = "Saved Playlists",
                list = playlists,
                subtitle = "${playlists.size} playlists"
            ))
        }

        return shelves
    }

    // ===== SAAVN TAB =====

    private suspend fun loadSaavnTab(): Feed.Data<Shelf> {
        return try {
            buildSaavnShelves().toFeedData()
        } catch (e: ClientException.LoginRequired) {
            throw e
        } catch (e: Exception) {
            Logger.e("LibraryFeed", "Failed to load Saavn tab", e)
            emptyList<Shelf>().toFeedData()
        }
    }

    private suspend fun buildSaavnShelves(): List<Shelf> {
        val shelves = mutableListOf<Shelf>()
        val library = api.library.getAll()

        // ---- Liked Songs ----
        val songIds = parser.library.parseSongIds(library).asReversed()
        paginatedShelf(
            shelfId = "saavn_liked_tracks",
            shelfTitle = "Liked Songs",
            ids = songIds,
            fetcher = { fetchSongDetailsChunked(it) },
        )?.let(shelves::add)

        // ---- Liked Albums ----
        val albumIds = parser.library.parseAlbumIds(library).asReversed()
        paginatedShelf(
            shelfId = "saavn_liked_albums",
            shelfTitle = "Liked Albums",
            ids = albumIds,
            fetcher = { fetchAlbumDetailsChunked(it) },
        )?.let(shelves::add)

        // ---- Followed Artists ----
        val artistIds = parser.library.parseArtistIds(library).asReversed()
        paginatedShelf(
            shelfId = "saavn_followed_artists",
            shelfTitle = "Followed Artists",
            ids = artistIds,
            fetcher = { fetchArtistDetailsChunked(it) },
        )?.let(shelves::add)

        // ---- Playlists ----
        val playlistIds = parser.library.parsePlaylistIds(library).asReversed()
        paginatedShelf(
            shelfId = "saavn_playlists",
            shelfTitle = "Playlists",
            ids = playlistIds,
            fetcher = { fetchPlaylistDetailsChunked(it) },
        )?.let(shelves::add)

        return shelves
    }

    private suspend fun <T : EchoMediaItem> paginatedShelf(
        shelfId: String,
        shelfTitle: String,
        ids: List<String>,
        previewSize: Int = BATCH_SIZE,
        pageSize: Int = BATCH_SIZE,
        fetcher: suspend (List<String>) -> List<T>,
    ): Shelf? {
        if (ids.isEmpty()) return null

        val preview = try {
            fetcher(ids.take(previewSize))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("LibraryFeed", "Failed to load preview for $shelfId", e)
            return null
        }
        if (preview.isEmpty()) return null

        val more = Feed(emptyList()) { _ ->
            PagedData.Continuous<Shelf> { continuation ->
                val page = continuation?.toIntOrNull() ?: 1
                val start = (page - 1) * pageSize
                val end = minOf(start + pageSize, ids.size)

                if (start >= ids.size) {
                    Page(emptyList<Shelf>(), null)
                } else {
                    try {
                        val items = fetcher(ids.subList(start, end)).map { it.toShelf() }
                        val next = if (end < ids.size) (page + 1).toString() else null
                        Page(items, next)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e("LibraryFeed", "Failed to load page $page for $shelfId", e)
                        Page(emptyList<Shelf>(), null)
                    }
                }
            }.toFeedData()
        }

        return Shelf.Lists.Items(
            id = shelfId,
            title = shelfTitle,
            list = preview,
            subtitle = "${ids.size} items",
            more = if (ids.size > previewSize) more else null,
        )
    }

    // ===== Chunked fetchers =====

    private suspend fun fetchSongDetailsChunked(ids: List<String>): List<Track> {
        val result = mutableListOf<Track>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                val resp = api.library.getDetails(chunk, "song", chunk.size)
                result.addAll(parser.track.parseSongDetails(resp))
            } catch (e: Exception) {
                Logger.e("LibraryFeed", "Failed to fetch ${chunk.size} songs", e)
            }
        }
        return result
    }

    private suspend fun fetchAlbumDetailsChunked(ids: List<String>): List<Album> {
        val result = mutableListOf<Album>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                val resp = api.library.getDetails(chunk, "album", chunk.size)
                result.addAll(parser.album.parseAlbumResults(resp))
            } catch (e: Exception) {
                Logger.e("LibraryFeed", "Failed to fetch ${chunk.size} albums", e)
            }
        }
        return result
    }

    private suspend fun fetchArtistDetailsChunked(ids: List<String>): List<Artist> {
        val result = mutableListOf<Artist>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                val resp = api.library.getDetails(chunk, "artist", chunk.size)
                result.addAll(parser.artist.parseArtistSearchResults(resp))
            } catch (e: Exception) {
                Logger.e("LibraryFeed", "Failed to fetch ${chunk.size} artists", e)
            }
        }
        return result
    }

    private suspend fun fetchPlaylistDetailsChunked(ids: List<String>): List<Playlist> {
        val result = mutableListOf<Playlist>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                val resp = api.library.getDetails(chunk, "playlist", chunk.size)
                result.addAll(parser.playlist.parsePlaylistResults(resp))
            } catch (e: Exception) {
                Logger.e("LibraryFeed", "Failed to fetch ${chunk.size} playlists", e)
            }
        }
        return result
    }

    companion object {
        private const val BATCH_SIZE = 50
    }
}
