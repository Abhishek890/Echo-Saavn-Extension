package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.*
import dev.brahmkshatriya.echo.common.settings.*
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.TrackDetails
import dev.brahmkshatriya.echo.common.models.Album

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap

import dev.brahmkshatriya.echo.extension.client.*
import dev.brahmkshatriya.echo.extension.utils.LANGUAGES
import dev.brahmkshatriya.echo.extension.storage.LocalRecentStore
import dev.brahmkshatriya.echo.extension.service.RadioService
import dev.brahmkshatriya.echo.extension.service.AlbumService
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.SettingsKeys

object SaavnDependencies {
    val api by lazy { JioSaavnApi() }
    val parser by lazy { JioSaavnParser() }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val radioService by lazy { RadioService(api, parser, appScope) }
    val albumService by lazy { AlbumService(api, parser, appScope) }

    var settings: Settings? = null

    fun getDefaultLanguages(): List<String> {
        val value = settings?.getStringSet(SettingsKeys.DEFAULT_HOME_LANGUAGES)
        return value?.toList() ?: listOf("hindi")
    }

    fun isAutoRadioEnabled(): Boolean =
        settings?.getBoolean(SettingsKeys.AUTO_RADIO_ENABLED) ?: true

    val inflightTrackLoads = ConcurrentHashMap<String, Deferred<Track>>()

    // Track cache
    var cachedTrackId: String? = null
    var cachedTrack: Track? = null
}

class SaavnExtension : ExtensionClient,
    QuickSearchClient by QuickSearchClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    HomeFeedClient by HomeFeedClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    TrackClient by TrackClientImpl(SaavnDependencies.api, SaavnDependencies.parser, SaavnDependencies.radioService, SaavnDependencies.albumService),
    AlbumClient by AlbumClientImpl(SaavnDependencies.api, SaavnDependencies.parser, SaavnDependencies.albumService),
    ArtistClient by ArtistClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    LibraryFeedClient by LibraryFeedClientImpl(),
    PlaylistEditClient by PlaylistEditClientImpl(SaavnDependencies.api, SaavnDependencies.parser),
    RadioClient by RadioClientImpl(SaavnDependencies.radioService),
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
            ),
            SettingSwitch(
                title = "Auto-Radio",
                key = "auto_radio_enabled",
                summary = "Automatically play similar tracks when the queue ends",
                defaultValue = true
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
