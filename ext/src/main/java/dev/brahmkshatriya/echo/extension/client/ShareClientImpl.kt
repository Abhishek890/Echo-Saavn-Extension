package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.SaavnSettings
import dev.brahmkshatriya.echo.extension.storage.LocalPlaylistTracksStore
import dev.brahmkshatriya.echo.extension.storage.LocalSavedTracksStore
import dev.brahmkshatriya.echo.extension.storage.Prefixes
import dev.brahmkshatriya.echo.extension.utils.Extras
import dev.brahmkshatriya.echo.extension.utils.Logger
import dev.brahmkshatriya.echo.extension.utils.getToken

class ShareClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser,
) : ShareClient {

    override suspend fun onShare(item: EchoMediaItem): String {
        val url = item.extras[Extras.PERMA_URL].orEmpty()

        if (!SaavnSettings.isRichShareEnabled()) {
            return url.ifBlank { "JioSaavn" }
        }

        return when (item) {
            is Playlist -> {
                val tracks = fetchPlaylistTracks(item)
                buildPlaylistCsv(item, tracks)
            }
            else -> url.ifBlank { "JioSaavn" }
        }
    }

    // ===== Track fetching =====

    private suspend fun fetchPlaylistTracks(playlist: Playlist): List<Track> {

        if (Prefixes.isSavedTracksPlaylist(playlist.id)) {
            val settings = SaavnSettings.settings ?: return emptyList()
            return LocalSavedTracksStore.getAll(settings)
        }

        if (Prefixes.isLocal(playlist.id)) {
            val settings = SaavnSettings.settings ?: return emptyList()
            return LocalPlaylistTracksStore.getTracks(settings, playlist.id)
        }

        val token = playlist.getToken()
        if (token.isBlank()) return emptyList()

        val tracks = mutableListOf<Track>()
        val expectedTotal = playlist.trackCount?.toInt()
        var page = 1

        try {
            while (true) {
                val response = api.playlist.getDetails(token, page = page, count = PAGE_SIZE)
                val pageTracks = parser.playlist.parsePlaylistTracks(response)
                if (pageTracks.isEmpty()) break
                tracks.addAll(pageTracks)

                if (pageTracks.size < PAGE_SIZE) break
                if (expectedTotal != null && tracks.size >= expectedTotal) break
                page++
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("ShareClient", "Failed to fetch playlist ${playlist.id}", e)
        }

        return tracks
    }

    // ===== CSV building =====

    private fun buildPlaylistCsv(playlist: Playlist, tracks: List<Track>): String {
        val header = CSV_COLUMNS
        return buildString {
            append('\uFEFF')
            appendCsvRow(CSV_COLUMNS.map { it.header })
            tracks.forEach { track ->
                appendCsvRow(CSV_COLUMNS.map { it.extractor(track) })
            }
        }
    }

    private fun StringBuilder.appendCsvRow(fields: List<String>) {
        fields.forEachIndexed { i, field ->
            if (i > 0) append(',')
            append(encodeCsvField(field))
        }
        append("\r\n")
    }

    private fun encodeCsvField(value: String): String {
        val sanitized = value
            .replace("\u0000", "")
            .replace("\r\n", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .let(::escapeFormulaPrefix)

        return "\"${sanitized.replace("\"", "\"\"")}\""
    }

    private fun escapeFormulaPrefix(value: String): String =
        if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'$value"
        else value

    // ===== Column definitions =====

    private data class CsvColumn(
        val header: String,
        val extractor: (Track) -> String,
    )

    companion object {
        private const val PAGE_SIZE = 100
        private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t')

        private val CSV_COLUMNS = listOf(
            CsvColumn("Title") { it.title },
            CsvColumn("Primary Artists") {
                it.artists.joinToString("; ") { a -> a.name }
                    .ifBlank { it.extras["artists_raw"].orEmpty() }
            },
            CsvColumn("Album") {
                it.album?.title.orEmpty()
                    .ifBlank { it.extras["album_raw"].orEmpty() }
            },
            CsvColumn("Duration (s)") {
                it.duration?.let { ms -> (ms / 1000).toString() }.orEmpty()
            },
            CsvColumn("Release Date") { it.extras["release_date_raw"].orEmpty() },
            CsvColumn("Year") { it.extras["year"].orEmpty() },
            CsvColumn("Label") {
                it.album?.label.orEmpty()
                    .ifBlank { it.extras["label_raw"].orEmpty() }
            },
            CsvColumn("Composer") { it.extras["composer"].orEmpty() },
            CsvColumn("Explicit") { it.isExplicit.toString() },
            CsvColumn("JioSaavn ID") { it.id },
            CsvColumn("JioSaavn URL") { it.extras[Extras.PERMA_URL].orEmpty() },
        )
    }
}
