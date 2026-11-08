package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn follow/unfollow endpoints (authenticated).
 *
 * Used by `FollowClientImpl` to follow artists and playlists. Unlike "like" (which
 * goes through [LibraryApi.add] / [LibraryApi.delete]), following uses dedicated
 * `social.follow` / `social.unfollow` calls, then updates the user's library index
 * implicitly (the followed items appear in [LibraryApi.getAll]'s `artist` and
 * `playlist` arrays).
 *
 * ### Which types support follow
 *
 * - **`"artist"`** — followable. Standard Saavn concept.
 * - **`"playlist"`** — followable. Same endpoint, different `type` param.
 * - **`"song"`, `"album"`** — NOT followable. Use [LibraryApi.add]/[delete] instead.
 *
 * This split is why Echo has two separate client interfaces: `LikeClient` for
 * tracks/albums (via `library.add`), `FollowClient` for artists/playlists (via
 * `social.follow`). See `FollowClientImpl` for the client-side routing.
 *
 * @see dev.brahmkshatriya.echo.extension.client.FollowClientImpl
 * @see LibraryApi
 */
class FollowApi : AuthenticatedBaseApi() {

    /**
     * Follows an artist or playlist.
     *
     * @param entityId the entity's id. Numeric id (e.g. `"568565"` for artists,
     *   `"1304962086"` for playlists) — both are what `FollowClientImpl` passes.
     * @param entityType `"artist"` or `"playlist"`.
     * @return a response with `{"status": "success"}` on success.
     */
    suspend fun follow(entityId: String, entityType: String): JsonObject =
        executeAuthed(
            call = "social.follow",
            params = mapOf(
                "entity_id" to entityId,
                "type" to entityType,
            ),
        )

    /**
     * Unfollows an artist or playlist.
     *
     * Symmetric with [follow] — same params, same response shape.
     *
     * @param entityId the entity's id.
     * @param entityType `"artist"` or `"playlist"`.
     * @return a response with `{"status": "success"}` on success.
     */
    suspend fun unfollow(entityId: String, entityType: String): JsonObject =
        executeAuthed(
            call = "social.unfollow",
            params = mapOf(
                "entity_id" to entityId,
                "type" to entityType,
            ),
        )
}
