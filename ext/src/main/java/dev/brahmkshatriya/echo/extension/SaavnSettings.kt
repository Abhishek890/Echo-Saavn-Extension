package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings

import dev.brahmkshatriya.echo.extension.utils.SettingsKeys

object SaavnSettings {
    var settings: Settings? = null
        private set

    fun init(settings: Settings) {
        this.settings = settings
    }

    fun defaultLanguages(): List<String> {
        val value = settings?.getStringSet(SettingsKeys.DEFAULT_HOME_LANGUAGES)
        return value?.toList() ?: listOf("hindi")
    }

    fun isAutoRadioEnabled(): Boolean =
        settings?.getBoolean(SettingsKeys.AUTO_RADIO_ENABLED) ?: true

    fun isRichShareEnabled(): Boolean =
        settings?.getBoolean(SettingsKeys.RICH_SHARE_ENABLED) ?: false
}
