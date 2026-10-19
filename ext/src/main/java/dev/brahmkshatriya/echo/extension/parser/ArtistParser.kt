package dev.brahmkshatriya.echo.extension.parser

import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Playlist

import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.Logger

class ArtistParser(
    private val trackParser: TrackParser,
    private val albumParser: AlbumParser,
    private val playlistParser: PlaylistParser
) : BaseParser() {

    fun parseArtistToArtist(obj: JsonObject): Artist? {
        return parseArtistFromJson(obj)
    }

    // ===== TOP SONGS =====
    fun parseArtistTopSongs(obj: JsonObject): List<Track> {
        return obj["topSongs"]?.jsonArray?.mapNotNull {
            trackParser.parseSongToTrack(it.jsonObject)
        } ?: emptyList()
    }

    // ===== TOP ALBUMS =====
    fun parseArtistTopAlbums(obj: JsonObject): List<Album> {
        return obj["topAlbums"]?.jsonArray?.mapNotNull {
            albumParser.parseAlbumToAlbum(it.jsonObject)
        } ?: emptyList()
    }

    fun parseArtistSingles(obj: JsonObject): List<Album> {
        return obj["singles"]?.jsonArray?.mapNotNull {
            albumParser.parseAlbumToAlbum(it.jsonObject)
        } ?: emptyList()
    }

    fun parseArtistDedicatedPlaylists(obj: JsonObject): List<Playlist> {
        return obj["dedicated_artist_playlist"]?.jsonArray?.mapNotNull {
            playlistParser.parsePlaylistToPlaylist(it.jsonObject)
        } ?: emptyList()
    }

    fun parseArtistFeaturedPlaylists(obj: JsonObject): List<Playlist> {
        return obj["featured_artist_playlist"]?.jsonArray?.mapNotNull {
            playlistParser.parsePlaylistToPlaylist(it.jsonObject)
        } ?: emptyList()
    }

    fun parseArtistSimilarArtists(obj: JsonObject): List<Artist> {
        val similar = obj["similarArtists"]?.jsonArray ?: return emptyList()
        return similar.mapNotNull { parseArtistFromJson(it.jsonObject) }
    }

    // ===== SEARCH RESULTS =====
    fun parseArtistSearchResults(obj: JsonObject): List<Artist> {
        val results = obj["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { parseArtistToArtist(it.jsonObject) }
    }

    // ===== DETAILS =====
    fun parseArtistDetails(obj: JsonObject): Artist? {
        return try {
            parseArtistToArtist(obj)
        } catch (e: Exception) {
            Logger.e("ArtistParser", "Failed to parse artist details: ${e.message}", e)
            null
        }
    }

    // ===== MORE SONGS (from artist.getArtistMoreSong) =====
    fun parseArtistMoreSongs(obj: JsonObject): List<Track> {
        return obj["topSongs"]?.jsonObject?.get("songs")?.jsonArray?.mapNotNull {
            trackParser.parseSongToTrack(it.jsonObject)
        } ?: emptyList()
    }

    // ===== MORE ALBUMS (from artist.getArtistMoreAlbum) =====
    fun parseArtistMoreAlbums(obj: JsonObject): List<Album> {
        return obj["topAlbums"]?.jsonObject?.get("albums")?.jsonArray?.mapNotNull {
            albumParser.parseAlbumToAlbum(it.jsonObject)
        } ?: emptyList()
    }

}
