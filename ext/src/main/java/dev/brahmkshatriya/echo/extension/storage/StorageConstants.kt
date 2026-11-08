package dev.brahmkshatriya.echo.extension.storage

object StorageKeys {
    const val SAVED_TRACKS = "saved_tracks"
    const val SAVED_ALBUMS = "saved_albums"
    const val SAVED_ARTISTS = "saved_artists"
    const val SAVED_PLAYLISTS = "saved_playlists"
    const val RECENT_TRACKS = "recent_tracks"
    const val LOCAL_PLAYLISTS = "local_playlists"
    const val LOCAL_PLAYLIST_TRACKS_PREFIX = "local_playlist_tracks_"
}

object Prefixes {
    const val LOCAL = "local_"

    fun newLocalId(): String = "$LOCAL${System.currentTimeMillis()}"

    fun isLocal(id: String): Boolean = id.startsWith(LOCAL)

    const val SAVED_TRACKS_PLAYLIST_ID = "saved_songs"
    fun isSavedTracksPlaylist(id: String) = id == SAVED_TRACKS_PLAYLIST_ID
}

object StorageLimits {
    const val RECENT_MAX = 20
}
