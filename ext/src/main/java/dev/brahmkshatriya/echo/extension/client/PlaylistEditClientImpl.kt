package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.PlaylistEditPrivacyClient
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Date
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.settings.Settings

import kotlinx.serialization.json.JsonObject

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.SaavnSession
import dev.brahmkshatriya.echo.extension.SaavnSettings
import dev.brahmkshatriya.echo.extension.storage.LocalPlaylistStore
import dev.brahmkshatriya.echo.extension.storage.LocalPlaylistTracksStore
import dev.brahmkshatriya.echo.extension.storage.LocalSavedTracksStore
import dev.brahmkshatriya.echo.extension.storage.Prefixes
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.getToken
import dev.brahmkshatriya.echo.extension.utils.requireSuccess

class PlaylistEditClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser
) : PlaylistEditPrivacyClient {

    private val settings get() = SaavnSettings.settings

    // ===== PLAYLIST CLIENT =====

    private var cachedPlaylistId: String? = null
    private var cachedResponse: JsonObject? = null
    private var cachedPlaylist: Playlist? = null
    private var cachedTracks: List<Track>? = null

    // ===== LOAD PLAYLIST =====
    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        // Saved Tracks playlist — synthetic, read-only
        if (Prefixes.isSavedTracksPlaylist(playlist.id)) {
            val count = settings?.let { LocalSavedTracksStore.getAll(it).size.toLong() } ?: 0L
            return playlist.copy(
                title = "Saved Songs",
                isEditable = false,
                trackCount = count,
            )
        }

        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            return playlist.copy(isEditable = true)
        }

        // Cache hit
        if (cachedPlaylistId == playlist.id && cachedPlaylist != null) {
            return applyEditable(cachedPlaylist!!)
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

        return applyEditable(parsedPlaylist)
    }

    private fun isOwnedByCurrentUser(playlist: Playlist): Boolean {
        val currentUser = SaavnSession.userId ?: return false
        val uid = playlist.extras["uid"].orEmpty()
        return uid.isNotBlank() && uid == currentUser
    }

    private fun requireOwned(playlist: Playlist, operation: String) {
        if (!isOwnedByCurrentUser(playlist)) {
            throw ClientException.NotSupported(operation)
        }
    }

    private fun applyEditable(playlist: Playlist): Playlist {
        val editable = isOwnedByCurrentUser(playlist)
        return playlist.copy(
            isEditable = editable,
            // Owned playlists can't be followed (nothing to follow); others can.
            isFollowable = !editable,
        )
    }

    // ===== LOAD TRACKS =====
    override suspend fun loadTracks(playlist: Playlist): Feed<Track> {
        // Saved tracks playlist
        if (Prefixes.isSavedTracksPlaylist(playlist.id)) {
            val settings = settings ?: return emptyList<Track>().toFeed() as Feed<Track>
            return LocalSavedTracksStore.getAll(settings).toFeed() as Feed<Track>
        }

        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            val settings = settings ?: return emptyList<Track>().toFeed() as Feed<Track>
            val tracks = LocalPlaylistTracksStore.getTracks(settings, playlist.id)
            return tracks.toFeed() as Feed<Track>
        }

        // Cache check
        val cached = cachedTracks.takeIf { cachedPlaylistId == playlist.id }
        val totalCount = cachedPlaylist?.trackCount?.toInt()

        // All tracks cached — return static feed
        if (cached != null && totalCount != null && cached.size >= totalCount) {
            Logger.d("PlaylistClient", "All $totalCount tracks cached — static feed")
            return cached.toFeed() as Feed<Track>
        }

        // Need pagination
        val token = playlist.getToken()
        if (token.isBlank()) return emptyList<Track>().toFeed() as Feed<Track>

        val pagedData = PagedData.Concat(
            PagedData.Single { cached ?: emptyList() },
            PagedData.Continuous { continuation ->
                val page = continuation?.toIntOrNull() ?: 2
                try {
                    val response = api.playlist.getDetails(token, page = page, count = 50)
                    val tracks = parser.playlist.parsePlaylistTracks(response)
                    val next = if (tracks.size >= 50) (page + 1).toString() else null
                    Page(tracks, next)
                } catch (e: Exception) {
                    Logger.e("PlaylistClient", "Failed to load tracks page $page", e)
                    Page(emptyList(), null)
                }
            }
        )
        return pagedData.toFeed()
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
        val language = parser.playlist.parseTrendingLanguage(response)

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

        val result = buildList {
            addAll(loadLocalEditablePlaylists(settings, track))
            addAll(loadSaavnEditablePlaylists(track))
        }

        Logger.d("PlaylistEdit", "listEditablePlaylists: ${result.size} total")
        return result
    }

    private fun loadLocalEditablePlaylists(
        settings: Settings,
        track: Track?,
    ): List<Pair<Playlist, Boolean>> =
        LocalPlaylistStore.getAll(settings)
            .filter { it.isEditable }
            .map { playlist ->
                val contains = track != null &&
                        LocalPlaylistTracksStore.getTracks(settings, playlist.id)
                            .any { it.id == track.id }
                playlist to contains
            }

    private suspend fun loadSaavnEditablePlaylists(
        track: Track?,
    ): List<Pair<Playlist, Boolean>> {
        val currentUser = SaavnSession.userId ?: return emptyList()

        return try {
            val response = api.playlistEdit.listMyPlaylists()
            parser.playlist.parsePlaylistResults(response)
                // uid filter maybe unnecessary
                .filter { it.extras["uid"] == currentUser }
                .map { playlist ->
                    val contents = playlist.extras["contents"].orEmpty()
                    val contains = track != null &&
                            contents.split(",").any { it.trim() == track.id }
                    playlist.copy(isEditable = true) to contains
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("PlaylistEdit", "Failed to fetch Saavn playlists", e)
            emptyList()
        }
    }

    override suspend fun createPlaylist(title: String, description: String?): Playlist {
        // Decide where to create. Saavn requires login; fall back to local when
        // logged out even if the setting is on.
        val useSaavn = SaavnSettings.createOnSaavn() && SaavnSession.userId != null

        if (!useSaavn) {
            // ---- Local ----
            val settings = settings ?: throw Exception("Settings unavailable")
            val playlist = Playlist(
                id = Prefixes.newLocalId(),
                title = title,
                isEditable = true,
                description = description,
                creationDate = Date(System.currentTimeMillis()),
            )
            LocalPlaylistStore.add(settings, playlist)
            LocalPlaylistTracksStore.setTracks(settings, playlist.id, emptyList())
            Logger.d("PlaylistEdit", "createPlaylist (local): ${playlist.id} - $title")
            return playlist
        }

        // ---- Saavn ----
        if (!description.isNullOrBlank()) {
            Logger.w("PlaylistEdit", "Saavn playlists don't support descriptions — ignoring")
        }

        Logger.d("PlaylistEdit", "createPlaylist (saavn): title=$title")

        val response = try {
            api.playlistEdit.create(name = title, contents = "", share = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("PlaylistEdit", "Failed to create Saavn playlist", e)
            throw e
        }

        response.requireSuccess("playlist.create")

        return parser.playlist.parseCreatedPlaylist(
            response = response,
        ) ?: throw Exception("playlist.create: failed to parse new playlist")

    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            val settings = settings ?: return
            LocalPlaylistStore.remove(settings, playlist.id)
            LocalPlaylistTracksStore.delete(settings, playlist.id)
            Logger.d("PlaylistEdit", "deletePlaylist (local): ${playlist.id}")
            return
        }

        // Saavn playlist

        // CSRF-protected endpoint — requires a user-provided X-Request-Key
        val xRequestKey = SaavnSettings.xRequestKey()
            ?: throw ClientException.NotSupported("deletePlaylist")

        Logger.d("PlaylistEdit", "deletePlaylist (saavn): ${playlist.id}")

        try {
            val response = api.playlistEdit.delete(playlist.id, xRequestKey)
            response.requireSuccess("playlist.delete")
        } catch (e: ClientException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("PlaylistEdit", "Failed to delete playlist ${playlist.id}", e)
            throw e
        } finally {
            invalidateCache()
        }
    }

    // Seems like playlist metadata editing is not fully supported in Echo
    override suspend fun editPlaylistMetadata(playlist: Playlist, title: String, description: String?) {
        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            val settings = settings ?: return
            val updated = playlist.copy(title = title, description = description)
            LocalPlaylistStore.add(settings, updated)
            Logger.d("PlaylistEdit", "editPlaylistMetadata (local): ${playlist.id} -> $title")
            return
        }

        // Saavn playlist
        Logger.d("PlaylistEdit", "editPlaylistMetadata (saavn): ${playlist.id} -> $title")

        try {
            val response = api.playlistEdit.rename(playlist.id, title)
            response.requireSuccess("playlist.rename")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("PlaylistEdit", "Failed to rename playlist ${playlist.id}", e)
            throw e
        } finally {
            invalidateCache()
        }

        // Note: `playlist.rename` only handles the title. JioSaavn has no
        // equivalent endpoint for editing a playlist's description, so the
        // `description` parameter is ignored for Saavn playlists.
    }

    private fun encodeTracks(tracks: List<Track>): String =
        tracks.joinToString("^") { track ->
            "~~${track.id}~${track.extras["language"].orEmpty()}"
        }

    private fun invalidateCache() {
        cachedPlaylistId = null
        cachedResponse = null
        cachedPlaylist = null
        cachedTracks = null
    }

    override suspend fun addTracksToPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        index: Int,
        new: List<Track>
    ) {
        // Local playlist — existing behavior
        if (Prefixes.isLocal(playlist.id)) {
            val settings = settings ?: return
            LocalPlaylistTracksStore.addTracks(settings, playlist.id, new, index)
            Logger.d("PlaylistEdit", "addTracksToPlaylist (local): ${playlist.id}, +${new.size} at $index")
            return
        }

        // Saavn playlist
        requireOwned(playlist, "Not authorized to edit this playlist")

        Logger.d("PlaylistEdit", "addTracksToPlaylist (saavn): ${playlist.id}, +${new.size}")

        val response = api.playlistEdit.add(playlist.id, encodeTracks(new))

        response.requireSuccess("playlist.add")

        // Invalidate cache so next open re-fetches with the new tracks
        invalidateCache()

        Logger.d("PlaylistEdit", "addTracksToPlaylist (saavn): success, cache invalidated")
    }

    override suspend fun removeTracksFromPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        indexes: List<Int>
    ) {
        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            val settings = settings ?: return
            LocalPlaylistTracksStore.removeTracks(settings, playlist.id, indexes)
            Logger.d("PlaylistEdit", "removeTracksFromPlaylist (local): ${playlist.id}, $indexes")
            return
        }

        // Saavn playlist — must be owned
        requireOwned(playlist, "removeTracksFromPlaylist")

        val pids = indexes.mapNotNull { idx -> tracks.getOrNull(idx)?.id }.distinct()
        if (pids.isEmpty()) return

        Logger.d("PlaylistEdit", "removeTracksFromPlaylist (saavn): ${playlist.id}, removing ${pids.size}")

        try {
            pids.forEach { pid ->
                api.playlistEdit.remove(playlist.id, pid)
            }
        } finally {
            invalidateCache()
        }
    }

    override suspend fun moveTrackInPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        fromIndex: Int,
        toIndex: Int
    ) {
        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            val settings = settings ?: return
            LocalPlaylistTracksStore.moveTrack(settings, playlist.id, fromIndex, toIndex)
            Logger.d("PlaylistEdit", "moveTrackInPlaylist (local): ${playlist.id}, $fromIndex -> $toIndex")
            return
        }

        // Saavn playlist — must be owned
        requireOwned(playlist, "moveTrackInPlaylist")

        if (fromIndex == toIndex) return
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices) {
            Logger.e("PlaylistEdit", "moveTrackInPlaylist: index out of range $fromIndex -> $toIndex (size=${tracks.size})")
            return
        }

        val newOrder = tracks.toMutableList()
        val moved = newOrder.removeAt(fromIndex)
        newOrder.add(toIndex, moved)

        val minN = maxOf(fromIndex, toIndex) + 1
        val pageN = ((minN + 49) / 50) * 50
        val n = minOf(pageN, tracks.size)

        val contents = encodeTracks(newOrder.take(n))

        Logger.d("PlaylistEdit", "moveTrackInPlaylist (saavn): ${playlist.id}, $fromIndex -> $toIndex, n=$n")

        try {
            api.playlistEdit.save(playlist.id, n, contents)
        } finally {
            invalidateCache()
        }
    }

    // ===== PLAYLIST EDIT PRIVACY CLIENT =====
    // Note: this is just placeholder since it is not implemented in Echo

    override suspend fun setPrivacy(playlist: Playlist, isPrivate: Boolean) {
        // Liked Songs — read-only
        if (Prefixes.isSavedTracksPlaylist(playlist.id)) return

        // Local playlist
        if (Prefixes.isLocal(playlist.id)) {
            return
        }

        // Saavn playlist — must be owned
        val currentUser = SaavnSession.userId
        val uid = playlist.extras["uid"].orEmpty()
        if (currentUser == null || uid.isBlank() || uid != currentUser) {
            throw ClientException.NotSupported("setPrivacy")
        }

        Logger.d("PlaylistEdit", "setPrivacy (saavn): ${playlist.id} -> private=$isPrivate")

        try {
            val response = if (isPrivate) {
                api.playlistEdit.makePrivate(playlist.id)
            } else {
                api.playlistEdit.makePublic(playlist.id)
            }
            // Both endpoints return {"status": "success"} (not "ok").
            response.requireSuccess(
                if (isPrivate) "playlist.makePrivate" else "playlist.makePublic"
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("PlaylistEdit", "Failed to set privacy for ${playlist.id}", e)
            throw e
        } finally {
            invalidateCache()
        }
    }

}
