package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings

/**
 * Keys for the extension's user-facing settings.
 *
 * These strings are the persistence keys Echo uses to store and restore
 * `Setting` values. They're declared separately from `SaavnSettings` so the
 * same constant can be referenced by both the [Setting] definitions in
 * `SaavnExtension.getSettingItems()` and the accessor methods below —
 * renaming a key means changing it in one place.
 *
 * Changing a key value is a breaking change: existing user preferences stored
 * under the old key become orphaned and won't be read.
 */
object SettingsKeys {
    const val DEFAULT_HOME_LANGUAGES = "default_home_languages"
    const val AUTO_RADIO_ENABLED = "auto_radio_enabled"
    const val RICH_SHARE_ENABLED = "rich_share_enabled"
    const val CREATE_ON_SAAVN = "create_on_saavn"
}

/**
 * Centralised accessors for the extension's user settings.
 *
 * Echo hands the extension a [Settings] instance once via
 * `ExtensionClient.setSettings(...)`. That instance is stored here so the rest
 * of the extension can read preferences without threading `Settings` through
 * every constructor or call site.
 *
 * ### Defaults
 *
 * Every accessor returns a sensible default when [settings] is `null` (i.e.
 * before [init] runs, or if Echo hasn't provided a settings instance yet).
 * Callers never need to null-check the result.
 *
 * ### Lifetime
 *
 * [settings] is written once by [init] and never replaced. It's exposed as
 * read-only so no other code can overwrite it. The instance itself is mutable
 * internally (Echo persists values into it), but that's Echo's concern — this
 * object only *reads*.
 *
 * ### Adding a new setting
 *
 * 1. Add a key to [SettingsKeys].
 * 2. Add a `Setting` definition (e.g. `SettingSwitch`) to
 *    `SaavnExtension.getSettingItems()` using that key.
 * 3. Add a corresponding accessor method here, with a default that matches
 *    the `Setting`'s `defaultValue`.
 */
object SaavnSettings {

    /**
     * The [Settings] instance provided by Echo.
     *
     * `null` before [init] is called. Accessors handle this by returning their
     * default value — callers don't need to check.
     *
     * Read-only from outside this object — Echo's settings instance must not be
     * replaced, only [init] can set it.
     */
    var settings: Settings? = null
        private set

    /**
     * Stores the [Settings] instance provided by Echo.
     *
     * Called once from `SaavnExtension.setSettings(...)`. Subsequent calls would
     * overwrite the instance — if that ever happens, it's a bug in Echo or in
     * this extension, not an expected flow.
     */
    fun init(settings: Settings) {
        this.settings = settings
    }

    /**
     * Returns the languages the "Default" tab on the Home feed mixes together.
     *
     * The Home feed has a "Default" tab plus one tab per supported language
     * (see `LANGUAGES`). The Default tab combines shelves from multiple
     * languages, controlled by this setting.
     *
     * @return the configured language list, or `["hindi"]` if unset.
     */
    fun defaultLanguages(): List<String> {
        val value = settings?.getStringSet(SettingsKeys.DEFAULT_HOME_LANGUAGES)
        return value?.toList() ?: listOf("hindi")
    }

    /**
     * Whether the queue automatically extends with similar tracks when it ends.
     *
     * When enabled, `TrackClientImpl` asks `RadioService` for suggested tracks
     * once the current queue is exhausted, keeping playback going indefinitely.
     *
     * @return `true` if auto-radio is on, `false` otherwise. Defaults to `true`.
     */
    fun isAutoRadioEnabled(): Boolean =
        settings?.getBoolean(SettingsKeys.AUTO_RADIO_ENABLED) ?: true

    /**
     * Whether "Share" produces rich output (formatted details / CSV) instead of
     * just the item's URL.
     *
     * When enabled:
     * - Tracks, albums, artists → URL
     * - Playlists → full track list as CSV (see `ShareClientImpl`)
     *
     * When disabled, all types share only their JioSaavn URL.
     *
     * @return `true` if rich share is on, `false` otherwise. Defaults to `false`
     *   (URL-only is the safer default for compatibility).
     */
    fun isRichShareEnabled(): Boolean =
        settings?.getBoolean(SettingsKeys.RICH_SHARE_ENABLED) ?: false

    /**
     * Whether new playlists should be created on the user's JioSaavn account
     * instead of locally.
     *
     * Only effective when logged in — creation falls back to local when
     * [SaavnSession.userId] is null, regardless of this setting.
     *
     * @return `true` if create-on-Saavn is enabled, `false` otherwise. Defaults to
     *   `false` (local creation is the safer default — it works offline and doesn't
     *   require an account).
     */
    fun createOnSaavn(): Boolean =
        settings?.getBoolean(SettingsKeys.CREATE_ON_SAAVN) ?: false
}
