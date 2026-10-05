package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Buttons
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.SaavnDependencies
import dev.brahmkshatriya.echo.extension.service.RadioService
import dev.brahmkshatriya.echo.extension.service.AlbumService
import dev.brahmkshatriya.echo.extension.utils.decryptUrl
import dev.brahmkshatriya.echo.extension.utils.getToken
import dev.brahmkshatriya.echo.extension.utils.Extras

class TrackClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser,
    private val radioService: RadioService,
    private val albumService: AlbumService
) : TrackClient {


    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        // Cache hit
        if (SaavnDependencies.cachedTrackId == track.id && SaavnDependencies.cachedTrack != null) {
            return SaavnDependencies.cachedTrack!!
        }

        // In-flight check + start
        val deferred = SaavnDependencies.inflightTrackLoads.computeIfAbsent(track.id) {
            SaavnDependencies.appScope.async {
                val token = track.getToken()
                val response = api.track.getDetails(token)
                val parsed = parser.track.parseSongDetails(response).firstOrNull() ?: track

                SaavnDependencies.cachedTrackId = track.id
                SaavnDependencies.cachedTrack = parsed

                parsed
            }
        }

        return try {
            deferred.await()
        } finally {
            SaavnDependencies.inflightTrackLoads.remove(track.id)
        }
    }

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        return when (streamable.type) {
            Streamable.MediaType.Server -> {
                val encryptedUrl = streamable.extras["encryptedMediaUrl"]
                    ?: throw Exception("No encrypted URL found")
                
                // Decrypt on demand
                val urls = decryptUrl(encryptedUrl)
                    ?: throw Exception("Failed to decrypt URL")
                
                val sources = mutableListOf<Streamable.Source.Http>()
                
                urls["veryHigh"]?.let {
                    sources.add(Streamable.Source.Http(
                        request = it.toGetRequest(),
                        type = Streamable.SourceType.Progressive,
                        quality = 320,
                        title = "320kbps"
                    ))
                }
                
                urls["high"]?.let {
                    sources.add(Streamable.Source.Http(
                        request = it.toGetRequest(),
                        type = Streamable.SourceType.Progressive,
                        quality = 160,
                        title = "160kbps"
                    ))
                }
                
                urls["medium"]?.let {
                    sources.add(Streamable.Source.Http(
                        request = it.toGetRequest(),
                        type = Streamable.SourceType.Progressive,
                        quality = 96,
                        title = "96kbps"
                    ))
                }
                
                urls["low"]?.let {
                    sources.add(Streamable.Source.Http(
                        request = it.toGetRequest(),
                        type = Streamable.SourceType.Progressive,
                        quality = 48,
                        title = "48kbps"
                    ))
                }
                
                if (sources.isEmpty()) {
                    throw Exception("No valid stream URLs available")
                }
                
                Streamable.Media.Server(sources, false)
            }
            Streamable.MediaType.Background -> {
                throw Exception("Background streamables not supported")
            }
            Streamable.MediaType.Subtitle -> {
                throw Exception("Subtitles not supported")
            }
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf> {
        return try {
            val shelves = mutableListOf<Shelf>()

            // ===== OTHER ARTISTS SHELF =====
            val otherArtists = parser.artist.parseOtherArtistsFromExtras(
                track.extras[Extras.OTHER_ARTISTS]
            )
            if (otherArtists.isNotEmpty()) {
                shelves.add(
                    Shelf.Lists.Items(
                        id = "other_artists",
                        title = "Other Artists",
                        list = otherArtists,
                        subtitle = "${otherArtists.size} artists"
                    )
                )
            }

            // ===== SIMILAR TRACKS SHELF =====
            val songId = track.id
            val songs = radioService.getRadioTracks(songId, limit = 20)

            if (songs.isNotEmpty()) {
                shelves.add(
                    // TODO: Change to Lists.Tracks when No item found issue is fixed in Echo
                    Shelf.Lists.Items(
                        id = "similar_tracks",
                        title = "Similar Tracks",
                        list = songs,
                        subtitle = "You might also like"
                    )
                )
            }

            // ===== MORE FROM ALBUM SHELF =====
            val album = track.album
            if (album != null) {
                // Ensure album is loaded
                try {
                    albumService.loadAlbum(album)
                } catch (e: Exception) {
                    // logged inside service
                }

                val albumTracks = albumService.getCachedTracks(album.id) ?: emptyList()
                val otherTracks = albumTracks.filter { it.id != track.id }

                if (otherTracks.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "more_from_album",
                            title = "More from ${album.title}",
                            list = otherTracks,
                            subtitle = "${otherTracks.size} tracks"
                        )
                    )
                }
            }

            shelves.toFeed(buttons = Buttons.EMPTY)
        } catch (e: Exception) {
            emptyList<Shelf>().toFeed()
        }
    }
}
