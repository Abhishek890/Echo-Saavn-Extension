package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

import dev.brahmkshatriya.echo.extension.utils.Logger

/**
 * JioSaavn user-library endpoints (authenticated).
 *
 * Covers the user's liked/followed items and their metadata:
 * - [getAll] — the full library index (ids only, no metadata)
 * - [getDetails] — metadata for a batch of ids from the index
 * - [add] / [delete] — like/unlike or follow/unfollow
 *
 * ### The two-step read pattern
 *
 * `library.getAll` returns only **ids** (with a small amount of metadata like
 * playlists' `ts`). To display items, callers fetch ids via [getAll], then batch-fetch
 * metadata via [getDetails]. This is why `LikeClientImpl` and `FollowClientImpl`
 * can cheaply check "is X liked?" — one [getAll] call, membership test on the ids.
 *
 * ### `entityType` values
 *
 * Valid values: `"song"`, `"album"`, `"artist"`, `"playlist"`, `"show"`. The
 * corresponding key in [getAll]'s response uses the same name, but the shape
 * differs per type — `song`/`album`/`artist` are string arrays, `playlist` is an
 * array of `{"id": "...", "ts": "..."}` objects. `LibraryParser.parseIds` handles
 * the branching.
 *
 * ### Which operations are "like" vs. "follow"
 *
 * Despite the generic API surface, semantically:
 * - `song`, `album` → **like** (see `LikeClientImpl`)
 * - `artist`, `playlist` → **follow** (see `FollowClientImpl`)
 *
 * Both routes call [add]/[delete] under the hood — the distinction is in the Echo
 * client interface, not the JioSaavn API.
 *
 * @see dev.brahmkshatriya.echo.extension.client.LikeClientImpl
 * @see dev.brahmkshatriya.echo.extension.client.FollowClientImpl
 */
class LibraryApi : AuthenticatedBaseApi() {

    /**
     * Fetches the full library index — all liked songs, albums, artists, and followed
     * playlists, as ids only.
     *
     * Used by:
     * - `LikeClientImpl.isItemLiked` / `FollowClientImpl.isFollowing` — membership test
     * - `LibraryFeedClientImpl` — the JioSaavn tab's shelves
     *
     * @return the library response with per-type id arrays under keys
     *   `song`, `album`, `artist`, `playlist`, `show`, plus a `user` object.
     */
    suspend fun getAll(): JsonObject {
        Logger.d("LibraryApi", "Fetching library")
        return executeAuthed(call = "library.getAll")
    }

    /**
     * Fetches metadata for a batch of entity ids.
     *
     * Pairs with [getAll] (which returns ids). Callers batch ids into a single
     * request rather than issuing one call per item — `LibraryFeedClientImpl`
     * chunks ids in groups of 50 to stay within the server's per-call limit.
     *
     * @param entityIds ids to fetch. Joined into a comma-separated string internally.
     *   At most ~50 per call in practice; batch larger sets client-side.
     * @param entityType `"song"`, `"album"`, `"artist"`, or `"playlist"`.
     * @param count maximum items to return. Defaults to 50 — matches the practical
     *   server limit per call.
     * @return the response shape differs by [entityType]:
     *   - `"song"` → object with a server-provided `songs` array
     *   - other types → bare array, wrapped by [executeRequest] as `{"results": [...]}`
     */ 
    suspend fun getDetails(
        entityIds: List<String>,
        entityType: String = "song",
        count: Int = 50,
    ): JsonObject {
        Logger.d("LibraryApi", "Fetching details for ${entityIds.size} $entityType(s)")
        return executeAuthed(
            call = "library.getDetails",
            params = mapOf(
                "entity_ids" to entityIds.joinToString(","),
                "entity_type" to entityType,
                "n" to count.toString(),
            ),
        )
    }

    /**
     * Adds an entity to the user's library (like/follow depending on type).
     *
     * @param entityId the entity id. Numeric id for playlists; token or numeric id
     *   for other types (both work).
     * @param entityType `"song"`, `"album"`, `"artist"`, or `"playlist"`.
     * @return a response with `{"status": "success"}` on success.
     */
    suspend fun add(entityId: String, entityType: String): JsonObject =
        executeAuthed(
            call = "library.add",
            params = mapOf(
                "entity_ids" to entityId,
                "entity_type" to entityType,
            ),
        )

    /**
     * Removes an entity from the user's library (unlike/unfollow depending on type).
     *
     * Symmetric with [add] — same params, same response shape.
     *
     * @param entityId the entity id.
     * @param entityType `"song"`, `"album"`, `"artist"`, or `"playlist"`.
     * @return a response with `{"status": "success"}` on success.
     */
    suspend fun delete(entityId: String, entityType: String): JsonObject =
        executeAuthed(
            call = "library.delete",
            params = mapOf(
                "entity_ids" to entityId,
                "entity_type" to entityType,
            ),
        )

}
