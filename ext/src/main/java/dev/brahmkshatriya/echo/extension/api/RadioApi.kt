package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject
import dev.brahmkshatriya.echo.extension.utils.Ctx

/**
 * JioSaavn radio ("stations") endpoints.
 *
 * Used to build endless queues: given a seed song, the server returns a station id,
 * then repeated [getSongSuggestions] calls return similar songs in order.
 *
 * ### Non-default context
 *
 * Radio endpoints only respond correctly under the `android` context
 * ([defaultCtx] override), not the default `web6dot0`. This matches JioSaavn's own
 * app behavior — the web player's radio is served via a different endpoint set.
 *
 * @see dev.brahmkshatriya.echo.extension.service.RadioService
 */
class RadioApi : BaseApi() {

    /**
     * Radio endpoints require the `android` context. Overriding here so every call
     * in this class picks it up automatically.
     */
    override val defaultCtx: String = Ctx.ANDROID

    /**
     * Creates a new radio station seeded from a single song.
     *
     * The response contains a `stationid`, which subsequent [getSongSuggestions]
     * calls use to fetch similar tracks.
     *
     * ### `entity_id` is a JSON-encoded string
     *
     * The `entity_id` param is NOT a plain song id — it's a JSON array string
     * (`["<songId>"]`). The server expects the literal brackets and quotes, and
     * `BaseApi.executeRequest` will URL-encode the whole thing. This matches what
     * the JioSaavn web player sends.
     *
     * @param songId the numeric song id (e.g. `"qZtKBMZ_"`-style token won't work;
     *   this endpoint wants the numeric id, e.g. `"1234567"`). Radio is the only
     *   endpoint group that uses the numeric id here — most other song lookups use
     *   the permalink token via `Track.getToken()`.
     * @return the station response containing `stationid`.
     */
    suspend fun createSongStation(songId: String): JsonObject =
        executeRequest(
            call = "webradio.createEntityStation",
            params = mapOf(
                // JSON-array string wrapping the numeric id — see method docs.
                "entity_id" to "[\"$songId\"]",
                "entity_type" to "queue"
            )
        )

    /**
     * Fetches the next batch of songs from an existing station.
     *
     * Radio endpoints return tracks sequentially — each call advances the server-side
     * cursor, so repeated calls yield a continuous stream of similar songs. Callers
     * should NOT assume determinism across calls for the same station.
     *
     * @param stationId the station id from [createSongStation]'s response.
     * @param limit how many songs to request in this batch.
     * @return the response object. The top level is a map of index keys
     *   (`"1"`, `"2"`, ...) each containing `{"song": {...}}`, plus a `stationid`
     *   key. `parseSongSuggestions` iterates all keys except `stationid`.
     */
    suspend fun getSongSuggestions(stationId: String, limit: Int = DEFAULT_SEARCH_LIMIT): JsonObject =
        executeRequest(
            call = "webradio.getSong",
            params = mapOf(
                "stationid" to stationId,
                "k" to limit.toString(),
                // `next=1` advances the station cursor. Without it, the same
                // first batch is returned every time.
                "next" to "1"
            )
        )
}
