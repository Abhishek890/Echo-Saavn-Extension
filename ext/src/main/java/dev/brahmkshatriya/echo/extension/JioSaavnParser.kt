package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.parser.*

class JioSaavnParser {
    val track = TrackParser()
    val album = AlbumParser(track)
    val playlist = PlaylistParser(track)
    val artist = ArtistParser(track, album, playlist)
    val home = HomeParser(track, album, artist, playlist)
    val radio = RadioParser(track)
    val search = SearchParser()
}
