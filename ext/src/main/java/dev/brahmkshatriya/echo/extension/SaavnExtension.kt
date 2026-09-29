package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.*
import dev.brahmkshatriya.echo.common.settings.*
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.TrackDetails

import kotlinx.serialization.json.JsonObject

import dev.brahmkshatriya.echo.extension.client.*
import dev.brahmkshatriya.echo.extension.utils.LANGUAGES
import dev.brahmkshatriya.echo.extension.storage.LocalRecentStore
import dev.brahmkshatriya.echo.extension.utils.Logger

object SaavnDependencies {
    val api by lazy { JioSaavnApi() }
    val parser by lazy { JioSaavnParser() }

    var settings: Settings? = null

    fun getDefaultLanguages(): List<String> {
        val value = settings?.getStringSet("default_home_languages")
        return value?.toList() ?: listOf("hindi")
    }

    // Album cache
    var cachedAlbumId: String? = null
    var cachedAlbumResponse: JsonObject? = null
    var cachedAlbumTracks: List<Track>? = null
}

class SaavnExtension : ExtensionClient,
    QuickSearchClient by QuickSearchClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    HomeFeedClient by HomeFeedClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    TrackClient by TrackClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    AlbumClient by AlbumClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    ArtistClient by ArtistClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    LibraryFeedClient by LibraryFeedClientImpl(),
    PlaylistEditClient by PlaylistEditClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    RadioClient by RadioClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    TrackerClient,
    LikeClient by LikeClientImpl(),
    ShareClient by ShareClientImpl() {

    private lateinit var settings: Settings


    override suspend fun getSettingItems(): List<Setting> {
        return listOf(
            SettingMultipleChoice(
                title = "Default Home Languages",
                key = "default_home_languages",
                summary = "Languages for the Default tab",
                entryTitles = LANGUAGES,
                entryValues = LANGUAGES.map { it.lowercase() },
                defaultEntryIndices = setOf(0)
            )
        )
    }

    override fun setSettings(settings: Settings) {
        SaavnDependencies.settings = settings
    }

    override suspend fun onTrackChanged(details: TrackDetails?) {
        val settings = SaavnDependencies.settings ?: return
        val track = details?.track ?: return
        LocalRecentStore.add(settings, track)
    }

    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {
        // Not needed for recently played
    }

}
