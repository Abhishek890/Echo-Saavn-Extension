package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

class SearchApi : BaseApi() {
    suspend fun all(query: String): JsonObject = executeRequest(
        call = "autocomplete.get",
        params = mapOf("query" to query)
    )
}
