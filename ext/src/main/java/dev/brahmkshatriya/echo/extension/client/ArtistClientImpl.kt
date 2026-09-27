package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData

import kotlinx.serialization.json.JsonObject

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.api.ArtistApi.ArtistCategory
import dev.brahmkshatriya.echo.extension.utils.getToken
import dev.brahmkshatriya.echo.extension.utils.runSafe

class ArtistClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser
) : ArtistClient {

    private var cachedArtistId: String? = null
    private var cachedFeed: Feed<Shelf>? = null

    // ===== LOAD ARTIST =====
    // No API call - return as-is
    override suspend fun loadArtist(artist: Artist): Artist {
        return artist
    }

    // ===== LOAD FEED =====
    override suspend fun loadFeed(artist: Artist): Feed<Shelf> {
        // Cache hit
        if (cachedArtistId == artist.id && cachedFeed != null) {
            return cachedFeed!!
        }

        // Try to get token from extras
        var token = artist.getToken()

        if (token.isBlank()) {
            Logger.d("ArtistClient", "Token missing for ${artist.name} (id=${artist.id})")
            return emptyList<Shelf>().toFeed()
        }

        // Fetch details using token
        val response = api.artist.getDetails(
            token = token,
        )

        val feed = buildFeed(artist, response)
        cachedArtistId = artist.id
        cachedFeed = feed
        return feed
    }

    private fun buildFeed(artist: Artist, response: JsonObject): Feed<Shelf> {
        val shelves = mutableListOf<Shelf>()

        // Top Songs
        val topSongs = parser.artist.parseArtistTopSongs(response)
        if (topSongs.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Items(
                    id = "artist_songs",
                    title = "Top Songs",
                    list = topSongs,
                    subtitle = "${topSongs.size} songs",
                    more = createMoreFeed(artist, "songs")
                )
            )
        }

        // Top Albums
        val topAlbums = parser.artist.parseArtistTopAlbums(response)
        if (topAlbums.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Items(
                    id = "artist_albums",
                    title = "Top Albums",
                    list = topAlbums,
                    subtitle = "${topAlbums.size} albums",
                    more = createMoreFeed(artist, "albums")
                )
            )
        }

        // Singles
        val singles = parser.artist.parseArtistSingles(response)
        if (singles.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Items(
                    id = "artist_singles",
                    title = "Singles",
                    list = singles,
                    subtitle = "${singles.size} singles"
                )
            )
        }

        // Dedicated Playlists
        val dedicatedPlaylists = parser.artist.parseArtistDedicatedPlaylists(response)
        if (dedicatedPlaylists.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Items(
                    id = "artist_dedicated_playlists",
                    title = "Dedicated Playlists",
                    list = dedicatedPlaylists,
                    subtitle = "${dedicatedPlaylists.size} playlists"
                )
            )
        }

        // Featured Playlists
        val featuredPlaylists = parser.artist.parseArtistFeaturedPlaylists(response)
        if (featuredPlaylists.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Items(
                    id = "artist_featured_playlists",
                    title = "Featured In",
                    list = featuredPlaylists,
                    subtitle = "${featuredPlaylists.size} playlists"
                )
            )
        }

        // ===== SIMILAR ARTISTS =====
        val similarArtists = parser.artist.parseArtistSimilarArtists(response)
        if (similarArtists.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Items(
                    id = "similar_artists",
                    title = "Similar Artists",
                    list = similarArtists,
                    subtitle = "${similarArtists.size} artists"
                )
            )
        }

        return shelves.toFeed()
    }

    private fun createMoreFeed(
        artist: Artist,
        type: String
    ): Feed<Shelf> {
        val tabs = listOf(
            Tab("popular", "Popular"),
            Tab("latest", "Latest")
        )

        return Feed(tabs) { tab ->
            val category = when (tab?.id) {
                "latest" -> ArtistCategory.LATEST
                else -> ArtistCategory.POPULAR
            }

            val pagedData = buildMorePages(artist, type, category)
            pagedData.toFeedData()
        }
    }

    private fun buildMorePages(
        artist: Artist,
        type: String,
        category: ArtistCategory
    ): PagedData<Shelf> {
        Logger.d("ArtistClient", "buildMorePages: type=$type, category=$category, artist.id=${artist.id}")

        return PagedData.Continuous { continuation ->
            val page = continuation?.toIntOrNull() ?: 1
            Logger.d("ArtistClient", "buildMorePages.Continuous: page=$page, type=$type")

            runSafe("ArtistClient", Page(emptyList<Shelf>(), null)) {
                val response = when (type) {
                    "songs" -> api.artist.getMoreSongs(artist.id, page, category)
                    "albums" -> api.artist.getMoreAlbums(artist.id, page, category)
                    else -> {
                        Logger.e("ArtistClient", "buildMorePages: unknown type=$type")
                        return@runSafe Page(emptyList(), null)
                    }
                }

                val items = when (type) {
                    "songs" -> parser.artist.parseArtistMoreSongs(response).map { it.toShelf() }
                    "albums" -> parser.artist.parseArtistMoreAlbums(response).map { it.toShelf() }
                    else -> emptyList()
                }

                if (items.isEmpty()) {
                    return@runSafe Page(emptyList(), null)
                }

                val nextContinuation = if (items.size > 0) (page + 1).toString() else null
                Page(items, nextContinuation)
            }

        }
    }

}
