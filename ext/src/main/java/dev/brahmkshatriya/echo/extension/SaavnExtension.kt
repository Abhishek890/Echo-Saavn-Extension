package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.*
import dev.brahmkshatriya.echo.common.settings.*
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.TrackDetails
import dev.brahmkshatriya.echo.common.models.Album

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import dev.brahmkshatriya.echo.extension.client.*
import dev.brahmkshatriya.echo.extension.utils.LANGUAGES
import dev.brahmkshatriya.echo.extension.storage.LocalRecentStore
import dev.brahmkshatriya.echo.extension.service.RadioService
import dev.brahmkshatriya.echo.extension.service.AlbumService
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.SettingsKeys

object Saavn {
    val api by lazy { JioSaavnApi() }
    val parser by lazy { JioSaavnParser() }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val radioService by lazy { RadioService(api, parser, appScope) }
    val albumService by lazy { AlbumService(api, parser, appScope) }

    var sessionCookie: String? = null
    var currentUserId: String? = null

}

class SaavnExtension : ExtensionClient,
    QuickSearchClient by QuickSearchClientImpl(Saavn.api, Saavn.parser),
    HomeFeedClient by HomeFeedClientImpl(Saavn.api, Saavn.parser),
    TrackClient by TrackClientImpl(Saavn.api, Saavn.parser, Saavn.radioService, Saavn.albumService, Saavn.appScope),
    AlbumClient by AlbumClientImpl(Saavn.api, Saavn.parser, Saavn.albumService),
    ArtistClient by ArtistClientImpl(Saavn.api, Saavn.parser),
    LibraryFeedClient by LibraryFeedClientImpl(Saavn.api, Saavn.parser),
    PlaylistEditClient by PlaylistEditClientImpl(Saavn.api, Saavn.parser),
    RadioClient by RadioClientImpl(Saavn.radioService),
    TrackerClient,
    SaveClient by SaveClientImpl(),
    ShareClient by ShareClientImpl(Saavn.api, Saavn.parser),
    LoginClient.WebView by LoginClientImpl(),
    FollowClient by FollowClientImpl(Saavn.api, Saavn.parser),
    LikeClient by LikeClientImpl(Saavn.api, Saavn.parser, Saavn.appScope) {

    private lateinit var settings: Settings

    override suspend fun getSettingItems(): List<Setting> {
        return listOf(
            SettingMultipleChoice(
                title = "Default Home Languages",
                key = SettingsKeys.DEFAULT_HOME_LANGUAGES,
                summary = "Languages for the Default tab",
                entryTitles = LANGUAGES,
                entryValues = LANGUAGES.map { it.lowercase() },
                defaultEntryIndices = setOf(0)
            ),
            SettingSwitch(
                title = "Auto-Radio",
                key = SettingsKeys.AUTO_RADIO_ENABLED,
                summary = "Automatically play similar tracks when the queue ends",
                defaultValue = true
            ),
            SettingSwitch(
                title = "Rich Share",
                key = SettingsKeys.RICH_SHARE_ENABLED,
                summary = "Share formatted details instead of just the link",
                defaultValue = false
            )
        )
    }

    override fun setSettings(settings: Settings) {
        SaavnSettings.init(settings)
    }

    override suspend fun onTrackChanged(details: TrackDetails?) {
        val settings = SaavnSettings.settings ?: return
        val track = details?.track ?: return
        LocalRecentStore.add(settings, track)
    }

    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {
        // Not needed for recently played
    }

}
