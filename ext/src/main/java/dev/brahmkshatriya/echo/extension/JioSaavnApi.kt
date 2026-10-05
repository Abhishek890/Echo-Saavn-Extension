package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.api.*

class JioSaavnApi {
    val track = TrackApi()
    val album = AlbumApi()
    val artist = ArtistApi()
    val playlist = PlaylistApi()
    val radio = RadioApi()
    val home = HomeApi()
    val search = SearchApi()
}
