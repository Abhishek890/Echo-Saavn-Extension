package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.utils.Logger

class QuickSearchClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser
) : QuickSearchClient {

    // ===== SHARED FETCHER =====
    private data class SearchResults(
        val songs: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val playlists: List<Playlist>
    )

    private suspend fun fetchAll(query: String, limit: Int = 10): SearchResults = coroutineScope {
        val songsDeferred = async {
            try {
                val response = api.track.search(query, page = 1, limit = limit)
                parser.track.parseSongSearchResults(response)
            } catch (e: Exception) { emptyList() }
        }
        
        val albumsDeferred = async {
            try {
                val response = api.album.search(query, page = 1, limit = limit)
                parser.album.parseAlbumResults(response)
            } catch (e: Exception) { emptyList() }
        }
        
        val artistsDeferred = async {
            try {
                val response = api.artist.search(query, page = 1, limit = limit)
                parser.artist.parseArtistSearchResults(response)
            } catch (e: Exception) { emptyList() }
        }
        
        val playlistsDeferred = async {
            try {
                val response = api.playlist.search(query, page = 1, limit = limit)
                parser.playlist.parsePlaylistResults(response)
            } catch (e: Exception) { emptyList() }
        }
        
        SearchResults(
            songs = songsDeferred.await(),
            albums = albumsDeferred.await(),
            artists = artistsDeferred.await(),
            playlists = playlistsDeferred.await()
        )
    }


    // ===== QUICK SEARCH =====

    override suspend fun quickSearch(query: String): List<QuickSearchItem> {
        if (query.isBlank()) return emptyList()

        // 1. Try URL detection first
        val urlItem = parseJioSaavnUrl(query)
        if (urlItem != null) {
            Logger.d("QuickSearch", "URL detected: ${urlItem::class.simpleName}")
            return listOf(QuickSearchItem.Media(urlItem, false))
        }

        // 2. Normal search
        return try {
            val response = api.search.all(query)
            val result = parser.search.parseSearchAll(response)
            
            val items = mutableListOf<QuickSearchItem>()
            result.topQuery?.let { items.add(QuickSearchItem.Media(it, false)) }
            result.songs.take(2).forEach { items.add(QuickSearchItem.Media(it, false)) }
            result.albums.take(2).forEach { items.add(QuickSearchItem.Media(it, false)) }
            result.artists.take(2).forEach { items.add(QuickSearchItem.Media(it, false)) }
            result.playlists.take(2).forEach { items.add(QuickSearchItem.Media(it, false)) }

            items
        } catch (e: Exception) {
            Logger.e("QuickSearch", "Search failed: $query", e)
            emptyList()
        }
    }

    // ===== URL DETECTION =====

    private suspend fun parseJioSaavnUrl(query: String): EchoMediaItem? {
        return try {
            if (!query.contains("jiosaavn.com", ignoreCase = true)) return null

            val token = query
                .substringBefore("?")
                .substringBefore("#")
                .trimEnd('/')
                .substringAfterLast("/")
            if (token.isBlank()) return null

            when {
                query.contains("/song/") -> fetchTrack(token)
                query.contains("/album/") -> fetchAlbum(token)
                query.contains("/artist/") -> fetchArtist(token)
                query.contains("/featured/") -> fetchPlaylist(token)
                query.contains("/s/playlist/") -> fetchPlaylist(token)
                else -> null
            }
        } catch (e: Exception) {
            Logger.e("QuickSearch", "URL detection failed: $query", e)
            null
        }
    }

    // ===== FETCH HELPERS =====

    private suspend fun fetchTrack(token: String): Track? {
        return try {
            val response = api.track.getDetails(token)
            parser.track.parseSongDetails(response).firstOrNull()
        } catch (e: Exception) {
            Logger.e("QuickSearch", "Failed to fetch track: $token", e)
            null
        }
    }

    private suspend fun fetchAlbum(token: String): Album? {
        return try {
            val response = api.album.getDetails(token)
            parser.album.parseAlbumToAlbum(response)
        } catch (e: Exception) {
            Logger.e("QuickSearch", "Failed to fetch album: $token", e)
            null
        }
    }

    private suspend fun fetchArtist(token: String): Artist? {
        return try {
            val response = api.artist.getDetails(
                token = token,
                songCount = 0,
                albumCount = 0
            )
            parser.artist.parseArtistToArtist(response)
        } catch (e: Exception) {
            Logger.e("QuickSearch", "Failed to fetch artist: $token", e)
            null
        }
    }

    private suspend fun fetchPlaylist(token: String): Playlist? {
        return try {
            val response = api.playlist.getDetails(token)
            parser.playlist.parsePlaylistToPlaylist(response)
        } catch (e: Exception) {
            Logger.e("QuickSearch", "Failed to fetch playlist: $token", e)
            null
        }
    }


    override suspend fun deleteQuickSearch(item: QuickSearchItem) {
        // Not implemented
    }

    // ===== SEARCH FEED =====
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        if (query.isBlank()) return emptyList<Shelf>().toFeed()
        
        val results = fetchAll(query, limit = 10)
        
        val shelves = mutableListOf<Shelf>()
        
        if (results.songs.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "search_songs",
                title = "Songs",
                list = results.songs
            ))
        }
        
        if (results.albums.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "search_albums",
                title = "Albums",
                list = results.albums
            ))
        }
        
        if (results.artists.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "search_artists",
                title = "Artists",
                list = results.artists
            ))
        }
        
        if (results.playlists.isNotEmpty()) {
            shelves.add(Shelf.Lists.Items(
                id = "search_playlists",
                title = "Playlists",
                list = results.playlists
            ))
        }
        
        val tabs = listOf(
            Tab("all", "All"),
            Tab("songs", "Songs"),
            Tab("albums", "Albums"),
            Tab("artists", "Artists"),
            Tab("playlists", "Playlists")
        )
        
        return Feed(tabs) { tab ->
            when (tab?.id) {
                "songs" -> createSongsFeed(query).toFeedData()
                "albums" -> createAlbumsFeed(query).toFeedData()
                "artists" -> createArtistsFeed(query).toFeedData()
                "playlists" -> createPlaylistsFeed(query).toFeedData()
                else -> PagedData.Single { shelves }.toFeedData()
            }
        }
    }

    private fun createSongsFeed(query: String) = PagedData.Continuous<Shelf> { continuation ->
        val page = continuation?.toIntOrNull() ?: 1
        try {
            val response = api.track.search(query, page = page, limit = 20)
            val songs = parser.track.parseSongSearchResults(response)
            
            val items = songs.map { it.toShelf() }
            val nextContinuation = if (songs.size >= 20) (page + 1).toString() else null
            Page(items, nextContinuation)
        } catch (e: Exception) {
            Page(emptyList(), null)
        }
    }

    private fun createAlbumsFeed(query: String) = PagedData.Continuous<Shelf> { continuation ->
        val page = continuation?.toIntOrNull() ?: 1
        try {
            val response = api.album.search(query, page = page, limit = 20)
            val albums = parser.album.parseAlbumResults(response)
            
            val items = albums.map { it.toShelf() }
            val nextContinuation = if (albums.size >= 20) (page + 1).toString() else null
            Page(items, nextContinuation)
        } catch (e: Exception) {
            Page(emptyList(), null)
        }
    }

    private fun createArtistsFeed(query: String) = PagedData.Continuous<Shelf> { continuation ->
        val page = continuation?.toIntOrNull() ?: 1
        try {
            val response = api.artist.search(query, page = page, limit = 20)
            val artists = parser.artist.parseArtistSearchResults(response)
            
            val items = artists.map { it.toShelf() }
            val nextContinuation = if (artists.size >= 20) (page + 1).toString() else null
            Page(items, nextContinuation)
        } catch (e: Exception) {
            Page(emptyList(), null)
        }
    }

    private fun createPlaylistsFeed(query: String) = PagedData.Continuous<Shelf> { continuation ->
        val page = continuation?.toIntOrNull() ?: 1
        try {
            val response = api.playlist.search(query, page = page, limit = 20)
            val playlists = parser.playlist.parsePlaylistResults(response)
            
            val items = playlists.map { it.toShelf() }
            val nextContinuation = if (playlists.size >= 20) (page + 1).toString() else null
            Page(items, nextContinuation)
        } catch (e: Exception) {
            Page(emptyList(), null)
        }
    }
}
