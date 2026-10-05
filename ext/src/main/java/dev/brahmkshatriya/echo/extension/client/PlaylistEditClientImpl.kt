package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.PlaylistEditClient
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.SaavnDependencies
import dev.brahmkshatriya.echo.extension.storage.LocalPlaylistStore
import dev.brahmkshatriya.echo.extension.storage.LocalPlaylistTracksStore
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.getToken
import dev.brahmkshatriya.echo.extension.utils.Prefixes
import kotlinx.serialization.json.*

class PlaylistEditClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser
) : PlaylistEditClient {

    private val settings get() = SaavnDependencies.settings

    // ===== PLAYLIST CLIENT =====

    private var cachedPlaylistId: String? = null
    private var cachedResponse: JsonObject? = null
    private var cachedPlaylist: Playlist? = null
    private var cachedTracks: List<Track>? = null

    // ===== LOAD PLAYLIST =====
    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            return playlist.copy(isEditable = true)
        }

        // Cache hit
        if (cachedPlaylistId == playlist.id && cachedPlaylist != null) {
            return cachedPlaylist!!
        }

        // Fetch, parse, cache
        val token = playlist.getToken()
        val response = api.playlist.getDetails(token)
        val parsedPlaylist = parser.playlist.parsePlaylistToPlaylist(response)
            ?: throw Exception("Playlist not found")
        val tracks = parser.playlist.parsePlaylistTracks(response)

        cachedPlaylistId = playlist.id
        cachedResponse = response
        cachedPlaylist = parsedPlaylist
        cachedTracks = tracks

        return parsedPlaylist
    }

    // ===== LOAD TRACKS =====
    override suspend fun loadTracks(playlist: Playlist): Feed<Track> {
        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            val settings = settings ?: return emptyList<Track>().toFeed() as Feed<Track>
            val tracks = LocalPlaylistTracksStore.getTracks(settings, playlist.id)
            return tracks.toFeed() as Feed<Track>
        }

        if (cachedPlaylistId == playlist.id && cachedTracks != null) {
            return cachedTracks!!.toFeed() as Feed<Track>
        }
        return emptyList<Track>().toFeed() as Feed<Track>
    }

    // ===== LOAD FEED =====
    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? {
        val response = cachedResponse.takeIf { cachedPlaylistId == playlist.id }
            ?: return null

        val shelves = mutableListOf<Shelf>()

        // ===== RELATED PLAYLISTS =====
        try {
            val recoResponse = api.playlist.getPlaylistReco(playlist.id)
            val relatedPlaylists = parser.playlist.parsePlaylistResults(recoResponse)
                .filter { it.id != playlist.id }

            if (relatedPlaylists.isNotEmpty()) {
                shelves.add(
                    Shelf.Lists.Items(
                        id = "related_playlists",
                        title = "Related Playlists",
                        list = relatedPlaylists,
                        subtitle = "${relatedPlaylists.size} playlists"
                    )
                )
            }
        } catch (e: Exception) {
            Logger.e("PlaylistClient", "Failed to load related playlists", e)
        }

        // ===== TRENDING PLAYLISTS SHELF =====
        val language = response["modules"]?.jsonObject
            ?.get("currentlyTrendingPlaylists")?.jsonObject
            ?.get("source_params")?.jsonObject
            ?.get("entity_language")?.jsonPrimitive?.content

        if (!language.isNullOrBlank()) {
            try {
                val trendingResponse = api.home.getTrending("playlist", language)
                val trendingPlaylists = parser.playlist.parsePlaylistResults(trendingResponse)
                    .filter { it.id != playlist.id }

                if (trendingPlaylists.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "trending_playlists",
                            title = "Trending Playlists",
                            list = trendingPlaylists,
                            subtitle = "${trendingPlaylists.size} playlists"
                        )
                    )
                }
            } catch (e: Exception) {
                Logger.e("PlaylistClient", "Failed to load trending playlists", e)
            }
        }

        // ===== ARTISTS SHELF =====
        val artists = parser.playlist.parsePlaylistArtists(response)
        if (artists.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Items(
                    id = "playlist_artists",
                    title = "Artists",
                    list = artists,
                    subtitle = "${artists.size} artists"
                )
            )
        }

        return if (shelves.isEmpty()) null else shelves.toFeed()
    }


    // ===== PLAYLIST EDIT CLIENT =====

    override suspend fun listEditablePlaylists(track: Track?): List<Pair<Playlist, Boolean>> {
        val settings = settings ?: return emptyList()
        val playlists = LocalPlaylistStore.getAll(settings)
        Logger.d("PlaylistEdit", "listEditablePlaylists: ${playlists.size} playlists")

        return playlists.map { playlist ->
            val isInPlaylist = track != null &&
                    LocalPlaylistTracksStore.getTracks(settings, playlist.id)
                        .any { it.id == track.id }
            playlist to isInPlaylist
        }
    }

    override suspend fun createPlaylist(title: String, description: String?): Playlist {
        val settings = settings ?: throw Exception("Settings unavailable")
        val playlist = Playlist(
            id = Prefixes.newLocalId(),
            title = title,
            isEditable = true,
            isPrivate = false,
            description = description,
            creationDate = Date(System.currentTimeMillis()),
            cover = null,
            authors = emptyList(),
            trackCount = 0,
            duration = null,
            background = null,
            subtitle = null,
            extras = emptyMap()
        )
        LocalPlaylistStore.add(settings, playlist)
        LocalPlaylistTracksStore.setTracks(settings, playlist.id, emptyList())
        Logger.d("PlaylistEdit", "createPlaylist: ${playlist.id} - $title")
        return playlist
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        val settings = settings ?: return
        LocalPlaylistStore.remove(settings, playlist.id)
        LocalPlaylistTracksStore.delete(settings, playlist.id)
        Logger.d("PlaylistEdit", "deletePlaylist: ${playlist.id}")
    }

    override suspend fun editPlaylistMetadata(playlist: Playlist, title: String, description: String?) {
        val settings = settings ?: return
        val updated = playlist.copy(title = title, description = description)
        LocalPlaylistStore.add(settings, updated)
        Logger.d("PlaylistEdit", "editPlaylistMetadata: ${playlist.id} -> $title")
    }

    override suspend fun addTracksToPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        index: Int,
        new: List<Track>
    ) {
        val settings = settings ?: return
        LocalPlaylistTracksStore.addTracks(settings, playlist.id, new, index)
        Logger.d("PlaylistEdit", "addTracksToPlaylist: ${playlist.id}, +${new.size} at $index")
    }

    override suspend fun removeTracksFromPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        indexes: List<Int>
    ) {
        val settings = settings ?: return
        LocalPlaylistTracksStore.removeTracks(settings, playlist.id, indexes)
        Logger.d("PlaylistEdit", "removeTracksFromPlaylist: ${playlist.id}, $indexes")
    }

    override suspend fun moveTrackInPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        fromIndex: Int,
        toIndex: Int
    ) {
        val settings = settings ?: return
        LocalPlaylistTracksStore.moveTrack(settings, playlist.id, fromIndex, toIndex)
        Logger.d("PlaylistEdit", "moveTrackInPlaylist: ${playlist.id}, $fromIndex -> $toIndex")
    }
}
