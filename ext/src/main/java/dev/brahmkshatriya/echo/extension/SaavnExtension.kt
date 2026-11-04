package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.*
import dev.brahmkshatriya.echo.common.settings.*
import dev.brahmkshatriya.echo.common.models.TrackDetails

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import dev.brahmkshatriya.echo.extension.client.*
import dev.brahmkshatriya.echo.extension.utils.LANGUAGES
import dev.brahmkshatriya.echo.extension.storage.LocalRecentStore
import dev.brahmkshatriya.echo.extension.service.RadioService
import dev.brahmkshatriya.echo.extension.service.AlbumService
import dev.brahmkshatriya.echo.extension.utils.Logger


/**
 * Session state for the current JioSaavn login.
 *
 * Holds the auth cookie and the numeric user id. Written by `LoginClientImpl`
 * on login/logout restore, read by `AuthenticatedBaseApi` and client code that
 * needs to know who's logged in (e.g. playlist ownership checks).
 *
 * Both fields are cleared together on logout — they're never independent.
 */
object SaavnSession {
    var cookie: String? = null
    var userId: String? = null

    fun clear() {
        cookie = null
        userId = null
    }
}


/**
 * Composition root for the extension — app-wide singletons and shared services.
 *
 * Everything here is instantiated lazily and shared across all clients. Clients
 * receive these by constructor injection (from [SaavnExtension]'s delegations),
 * so this object is only read at wiring time.
 *
 * ### What belongs here
 *
 * - Stateless API wrappers ([api], [parser]) — one instance is enough.
 * - The shared coroutine scope ([appScope]) — all async work runs on it so that
 *   lifetime and threading are consistent.
 * - Stateful services shared by multiple clients ([radioService], [albumService]).
 *
 * ### What does NOT belong here
 *
 * - Session state → [SaavnSession].
 * - User settings → [SaavnSettings].
 * - Per-client state (caches owned by a single client) → that client's class.
 *
 * Keeping this object small and dependency-only is what makes the composition
 * root easy to reason about. Add new shared services here; do not add per-client
 * state.
 */
object Saavn {
    /** JioSaavn API wrappers. Shared by every client. */
    val api by lazy { JioSaavnApi() }

    /** JSON parsers. Shared by every client. */
    val parser by lazy { JioSaavnParser() }

    /**
     * Long-lived coroutine scope for async work that must outlive individual
     * client calls — e.g. in-flight dedup in services, radio track fetching.
     * Uses [Dispatchers.IO] because every operation is network-backed.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Endless-queue radio service, shared by `TrackClientImpl` and `RadioClientImpl`. */
    val radioService by lazy { RadioService(api, parser, appScope) }

    /** Album cache + loader, shared by `AlbumClientImpl` and `TrackClientImpl`. */
    val albumService by lazy { AlbumService(api, parser, appScope) }
}


/**
 * The extension entry point — instantiated by Echo on startup.
 *
 * Echo's plugin model requires each extension to implement one or more client
 * interfaces via property delegation. This class is mostly wiring: it delegates
 * each interface to its implementation, passing the shared dependencies from
 * [Saavn].
 *
 * ### Why delegation
 *
 * Echo's `ExtensionClient` expects all implemented clients to be available as
 * properties. Kotlin's `by` delegation lets us declare that with one line per
 * client, without needing to write forwarding methods. Any client not listed
 * here is simply unsupported.
 *
 * ### Constructor arguments
 *
 * Each delegate receives only what it needs:
 * - Most clients take `api` + `parser`.
 * - Clients with shared state take the relevant service (e.g. `TrackClientImpl`
 *   receives both `radioService` and `albumService`).
 * - Clients with coroutine work take `appScope`.
 * - `SaveClientImpl` takes nothing — it only touches local stores.
 *
 * ### Settings and tracking
 *
 * [getSettingItems] / [setSettings] expose user-facing toggles to Echo. See
 * [SaavnSettings] for accessors, [SettingsKeys] for the persistence keys.
 *
 * [onTrackChanged] / [onPlayingStateChanged] are lifecycle hooks Echo calls
 * during playback. [onTrackChanged] records the track in `LocalRecentStore`;
 * the state-change hook is unimplemented (nothing depends on it).
 */
class SaavnExtension : ExtensionClient,

    // ===== Search =====
    QuickSearchClient by QuickSearchClientImpl(Saavn.api, Saavn.parser),

    // ===== Home feed =====
    HomeFeedClient by HomeFeedClientImpl(Saavn.api, Saavn.parser),

    // ===== Track (uses both radio for "similar tracks" and album for "more from album") =====
    TrackClient by TrackClientImpl(
        Saavn.api, Saavn.parser, Saavn.radioService, Saavn.albumService, Saavn.appScope
    ),

    // ===== Album =====
    AlbumClient by AlbumClientImpl(Saavn.api, Saavn.parser, Saavn.albumService),

    // ===== Artist =====
    ArtistClient by ArtistClientImpl(Saavn.api, Saavn.parser),

    // ===== Library feed (Local + JioSaavn tabs) =====
    LibraryFeedClient by LibraryFeedClientImpl(Saavn.api, Saavn.parser),

    // ===== Playlist editing (local + JioSaavn playlists) =====
    PlaylistEditClient by PlaylistEditClientImpl(Saavn.api, Saavn.parser),

    // ===== Radio (endless queue) =====
    RadioClient by RadioClientImpl(Saavn.radioService),

    // ===== Playback tracking (onTrackChanged hook below) =====
    TrackerClient,

    // ===== Local "Saved" storage (no auth required) =====
    SaveClient by SaveClientImpl(),

    // ===== Share (URL or CSV depending on settings) =====
    ShareClient by ShareClientImpl(Saavn.api, Saavn.parser),

    // ===== Login (WebView-based) =====
    LoginClient.WebView by LoginClientImpl(Saavn.api),

    // ===== Follow artists/playlists (auth required) =====
    FollowClient by FollowClientImpl(Saavn.api, Saavn.parser),

    // ===== Like tracks/albums (auth required) =====
    LikeClient by LikeClientImpl(Saavn.api, Saavn.parser, Saavn.appScope) {


    /**
     * Declares the user-facing settings this extension exposes.
     *
     * Keys match [SettingsKeys]; defaults here must match the fallbacks in
     * [SaavnSettings] — otherwise the setting UI shows one value while the
     * accessor returns another before the user changes it.
     */
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

    /**
     * Called by Echo when the settings instance becomes available (and on
     * config changes). Forwards to [SaavnSettings.init] so accessors can read
     * values without needing a `Settings` reference at every call site.
     */
    override fun setSettings(settings: Settings) {
        SaavnSettings.init(settings)
    }

    /**
     * Called by Echo when the currently-playing track changes.
     *
     * Records the track in `LocalRecentStore` for the "Recently Played" shelf.
     * Silently no-ops if settings aren't initialised (early startup) or if the
     * event carries no track (e.g. playback stopped).
     */
    override suspend fun onTrackChanged(details: TrackDetails?) {
        val settings = SaavnSettings.settings ?: return
        val track = details?.track ?: return
        LocalRecentStore.add(settings, track)
    }

    /**
     * Called by Echo when play/pause state changes.
     *
     * Unused — "Recently Played" only cares about track changes, not pause
     * state. Implement here if a future feature needs to track listening
     * duration or play counts.
     */
    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {
        // Not needed for recently played
    }

}
