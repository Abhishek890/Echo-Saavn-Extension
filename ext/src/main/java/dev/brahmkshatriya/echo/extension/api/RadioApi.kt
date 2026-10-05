package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

class RadioApi : BaseApi() {

    override val defaultCtx: String = "android"

    suspend fun createSongStation(songId: String): JsonObject =
        executeRequest(
            call = "webradio.createEntityStation",
            params = mapOf(
                "entity_id" to "[\"$songId\"]",
                "entity_type" to "queue"
            )
        )

    suspend fun getSongSuggestions(stationId: String, limit: Int = DEFAULT_SEARCH_LIMIT): JsonObject =
        executeRequest(
            call = "webradio.getSong",
            params = mapOf(
                "stationid" to stationId,
                "k" to limit.toString(),
                "next" to "1"
            )
        )
}
