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

object Extras {
    const val PERMA_URL = "permaUrl"
    const val OTHER_ARTISTS = "otherArtistsJson"
    const val ARTIST_MAP = "artistMapJson"
    const val STATION_ID = "stationId"
}

object Prefixes {
    const val LOCAL = "local_"
    
    fun newLocalId(): String = "$LOCAL${System.currentTimeMillis()}"
    
    fun isLocal(id: String): Boolean = id.startsWith(LOCAL)
}
