package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import dev.brahmkshatriya.echo.extension.utils.Logger

open class BaseApi {

    protected open val defaultCtx: String = "web6dot0"

    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    protected val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            builder.addHeader("Accept", "application/json")
            builder.addHeader("Accept-Language", "en-US,en;q=0.9")
            builder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            chain.proceed(builder.build())
        }
        .build()

    protected suspend fun executeRequest(
        call: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): JsonObject {
        val allParams = mapOf(
            "_format" to "json",
            "_marker" to "0",
            "api_version" to "4",
            "ctx" to defaultCtx
        ) + params

        val queryString = allParams.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        val url = "$BASE_URL?__call=$call&$queryString"

        Logger.d("API", "Request URL: $url")

        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.header(key, value) }
        val response = client.newCall(builder.build()).await()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }
        val body = response.body?.string() ?: throw Exception("Empty response body")
        val element = json.parseToJsonElement(body)

        // Wrap arrays in an object
        return when (element) {
            is JsonObject -> element
            is JsonArray -> buildJsonObject { put("results", element) }
            else -> throw Exception("Unexpected JSON type: ${element::class.simpleName}")
        }
    }

    companion object {
        const val BASE_URL = "https://www.jiosaavn.com/api.php"
        const val DEFAULT_SEARCH_LIMIT = 20
        const val DEFAULT_SONG_COUNT = 10
        const val DEFAULT_ALBUM_COUNT = 10
    }
}
