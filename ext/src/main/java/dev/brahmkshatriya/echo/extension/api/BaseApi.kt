package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.Ctx

/**
 * Base class for all JioSaavn API wrappers.
 *
 * Each concrete `*Api` class (Track, Album, Artist, Playlist, Home, Search, Auth, Library,
 * Follow, PlaylistEdit) extends this and exposes typed methods that call [executeRequest].
 *
 * ### Response shape handling
 *
 * JioSaavn's `api.php` returns either a JSON object or a bare JSON array at the top level.
 * [executeRequest] normalises both:
 * - `{...}` → returned as-is
 * - `[...]` → wrapped as `{"results": [...]}`
 *
 * Every parser that reads `obj["results"]` relies on this normalisation.
 *
 * ### Authentication
 *
 * `BaseApi` itself is unauthenticated — no cookie is sent. For endpoints requiring a
 * session cookie, extend [AuthenticatedBaseApi] instead. Auth failures (401/403) are
 * NOT distinguished here; callers see a generic exception. `AuthenticatedBaseApi`
 * handles the "no cookie" case via `requireCookie()`.
 *
 * ### Overridable defaults
 *
 * - [defaultCtx] — the `ctx` query param. Defaults to `web6dot0` (web player context).
 * - [defaultApiVersion] — the `api_version` query param. `null` omits the param entirely.
 *   Some endpoints (e.g. the quick-search "search all" call) behave differently with
 *   vs. without `api_version`, so it's per-class overridable and per-call overridable.
 */
open class BaseApi {

    /** `ctx` query parameter. Some endpoints return different data per context. */
    protected open val defaultCtx: String = Ctx.WEB

    /**
     * `api_version` query parameter.
     * `null` omits the parameter — some endpoints (notably quick-search "all") give
     * different results when this is present, so subclasses can opt out.
     */
    protected open val defaultApiVersion: String? = "4"

    /** Lenient JSON parser — tolerates unknown keys, malformed values, and coerced types. */
    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Shared HTTP client.
     *
     * Headers mimic a desktop Chrome browser because JioSaavn blocks or degrades
     * responses for bare/empty user agents.
     */
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

    /**
     * Executes a `__call=...` request against [BASE_URL] and returns the parsed JSON.
     *
     * ### Parameters
     * - [call] — the JioSaavn API method name, e.g. `"song.getDetails"`, `"playlist.add"`.
     * - [params] — additional query parameters specific to this call. Merged on top of
     *   the default `_format`/`_marker`/`api_version`/`ctx` params, so a caller can
     *   override a default by passing the same key.
     * - [headers] — request headers. Notably `Cookie` for authenticated calls (though
     *   most callers should use [AuthenticatedBaseApi] instead).
     * - [apiVersion] — overrides [defaultApiVersion] for this call. Pass `null` to
     *   omit the `api_version` param entirely.
     *
     * ### Return value
     * - A [JsonObject] representing the response body.
     * - If the top-level response is a JSON array, it is wrapped as `{"results": [...]}`.
     *
     * ### Throws
     * - `Exception` on non-2xx HTTP status, empty body, or unexpected JSON type.
     *   Auth failures (401/403) are NOT special-cased here; use [AuthenticatedBaseApi]
     *   if you need `ClientException.LoginRequired` on missing cookies.
     */
    protected suspend fun executeRequest(
        call: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        apiVersion: String? = defaultApiVersion,
        ctx: String? = defaultCtx,
    ): JsonObject {
        // Build defaults first, then let caller-provided `params` override.
        val baseParams = buildMap {
            put("_format", "json")
            put("_marker", "0")
            // Some endpoints reject or behave differently when api_version is present.
            if (apiVersion != null) put("api_version", apiVersion)
            put("ctx", ctx)
        }
        val allParams = baseParams + params

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

        // Normalise top-level JSON arrays into `{"results": [...]}` so that every
        // parser can consistently read `obj["results"]` without shape-branching.
        return when (element) {
            is JsonObject -> element
            is JsonArray -> buildJsonObject { put("results", element) }
            else -> throw Exception("Unexpected JSON type: ${element::class.simpleName}")
        }
    }

    companion object {
        /** JioSaavn's unified API endpoint. Every `*Api` class calls this. */
        const val BASE_URL = "https://www.jiosaavn.com/api.php"

        /** Default page size for `search.getResults`-style calls. */
        const val DEFAULT_SEARCH_LIMIT = 20

        /** Default track count for detail/shelf calls. */
        const val DEFAULT_SONG_COUNT = 10

        /** Default album count for detail/shelf calls. */
        const val DEFAULT_ALBUM_COUNT = 10
    }
}
