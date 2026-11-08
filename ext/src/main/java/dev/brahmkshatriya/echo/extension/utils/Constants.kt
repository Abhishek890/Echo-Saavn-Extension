package dev.brahmkshatriya.echo.extension.utils

val LANGUAGES = listOf(
    "Hindi",
    "English",
    "Punjabi",
    "Tamil",
    "Telugu",
    "Marathi",
    "Gujarati",
    "Bengali",
    "Kannada",
    "Bhojpuri",
    "Malayalam",
    "Sanskrit",
    "Haryanvi",
    "Rajasthani",
    "Odia",
    "Assamese"
)

/**
 * JioSaavn API context values (`ctx` query parameter).
 *
 * The `ctx` parameter selects which client context the server responds as.
 * Different contexts expose different endpoints and response shapes.
 */
object Ctx {
    /** Web player context. Used by most endpoints. */
    const val WEB = "web6dot0"

    /** Android app context. Required by radio & track recommendations endpoints. */
    const val ANDROID = "android"
}

object Extras {
    const val PERMA_URL = "permaUrl"
    const val ARTIST_MAP = "artistMapJson"
    const val STATION_ID = "stationId"
}
