package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.Track

import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.Extras

/**
 * Writes the rich (track-specific) fields that are not covered by [putCommonFields].
 * Every field is optional — a track with missing data simply omits the key.
 */
internal fun JsonObjectBuilder.putRichTrackFields(track: Track) {
    put("artists", track.artists.joinToString("; ") { it.name })
    track.album?.title?.let { put("album", it) }
    track.album?.label?.let { put("label", it) }
    track.duration?.let { put("duration", it) }
    put("explicit", track.isExplicit)

    track.extras["release_date_raw"]?.let { put("releaseDateRaw", it) }
    track.extras["year"]?.let { put("year", it) }
    track.extras["composer"]?.let { put("composer", it) }
}

/**
 * Rebuilds a [Track] from the common fields plus any rich fields present in this JSON object.
 * Absent keys fall back to safe defaults (empty extras, null album/duration, false explicit),
 * so pre-rich-storage stubs deserialize exactly as before.
 */
internal fun JsonObject.parseRichTrackFields(common: CommonFields): Track {
    val artistsRaw = this["artists"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val albumRaw = this["album"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val labelRaw = this["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val duration = this["duration"]?.jsonPrimitive?.longOrNull
    val explicit = this["explicit"]?.jsonPrimitive?.booleanOrNull ?: false
    val releaseDateRaw = this["releaseDateRaw"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val year = this["year"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val composer = this["composer"]?.jsonPrimitive?.contentOrNull.orEmpty()

    return Track(
        id = common.id,
        title = common.title,
        subtitle = common.subtitle,
        cover = common.cover,
        isExplicit = explicit,
        duration = duration,
        extras = buildMap {
            put(Extras.PERMA_URL, common.permaUrl)
            if (artistsRaw.isNotBlank()) put("artists_raw", artistsRaw)
            if (albumRaw.isNotBlank()) put("album_raw", albumRaw)
            if (labelRaw.isNotBlank()) put("label_raw", labelRaw)
            if (releaseDateRaw.isNotBlank()) put("release_date_raw", releaseDateRaw)
            if (year.isNotBlank()) put("year", year)
            if (composer.isNotBlank()) put("composer", composer)
        },
        streamables = emptyList(),
    )
}
