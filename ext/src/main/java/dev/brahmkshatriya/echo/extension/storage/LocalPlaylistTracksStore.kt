package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Settings
import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.Extras

object LocalPlaylistTracksStore {
    private const val KEY_PREFIX = StorageKeys.LOCAL_PLAYLIST_TRACKS_PREFIX
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun getTracks(settings: Settings, playlistId: String): List<Track> {
        val jsonString = settings.getString(KEY_PREFIX + playlistId) ?: return emptyList()
        return try {
            json.parseToJsonElement(jsonString).jsonArray.mapNotNull {
                deserializeTrack(it.jsonObject)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setTracks(settings: Settings, playlistId: String, tracks: List<Track>) {
        val jsonString = buildJsonArray {
            tracks.forEach { add(serializeTrack(it)) }
        }.toString()
        settings.putString(KEY_PREFIX + playlistId, jsonString)
    }

    fun addTracks(settings: Settings, playlistId: String, newTracks: List<Track>, index: Int) {
        val current = getTracks(settings, playlistId).toMutableList()
        val safeIndex = index.coerceIn(0, current.size)
        current.addAll(safeIndex, newTracks)
        setTracks(settings, playlistId, current)
    }

    fun removeTracks(settings: Settings, playlistId: String, indexes: List<Int>) {
        val current = getTracks(settings, playlistId).toMutableList()
        indexes.sortedDescending().forEach {
            if (it in current.indices) current.removeAt(it)
        }
        setTracks(settings, playlistId, current)
    }

    fun moveTrack(settings: Settings, playlistId: String, from: Int, to: Int) {
        val current = getTracks(settings, playlistId).toMutableList()
        if (from !in current.indices) return
        val target = to.coerceIn(0, current.size - 1)
        val item = current.removeAt(from)
        current.add(target, item)
        setTracks(settings, playlistId, current)
    }

    fun delete(settings: Settings, playlistId: String) {
        settings.putString(KEY_PREFIX + playlistId, null)
    }

    // ===== SERIALIZATION =====

    private fun serializeTrack(track: Track): JsonObject = buildJsonObject {
        putCommonFields(track)
    }

    private fun deserializeTrack(obj: JsonObject): Track? {
        val common = obj.parseCommonFields() ?: return null
        return Track(
            id = common.id,
            title = common.title,
            subtitle = common.subtitle,
            cover = common.cover,
            extras = mapOf(Extras.PERMA_URL to common.permaUrl),
            streamables = emptyList()
        )
    }
}
