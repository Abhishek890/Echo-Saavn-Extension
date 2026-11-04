package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab

import dev.brahmkshatriya.echo.extension.JioSaavnApi
import dev.brahmkshatriya.echo.extension.JioSaavnParser
import dev.brahmkshatriya.echo.extension.SaavnSettings
import dev.brahmkshatriya.echo.extension.parser.HomeSection
import dev.brahmkshatriya.echo.extension.parser.MoreInfo
import dev.brahmkshatriya.echo.extension.utils.LANGUAGES
import dev.brahmkshatriya.echo.extension.utils.runSafe

/**
 * Home feed for the JioSaavn tab.
 *
 * Renders a multi-tab feed:
 * - **Default** — mixes shelves from languages configured in [SaavnSettings.defaultLanguages]
 * - **Per-language** — one tab per entry in [LANGUAGES] (Hindi, Telugu, English, …)
 *
 * Each tab's response is a set of sections (e.g. "New Releases", "Top Charts").
 * Sections with a `more` hint support pagination — tapping "See more" opens a
 * paged feed that lazily fetches further pages from the shelf's own endpoint.
 *
 * ### Language selection
 *
 * The `language` argument sent to [JioSaavnApi.getHomeData] is:
 * - For "Default": a comma-separated list (e.g. `"hindi,telugu"`) — see
 *   [SaavnSettings.defaultLanguages].
 * - For a specific tab: the tab's id (e.g. `"hindi"`).
 *
 * JioSaavn uses this value as the `L=<languages>` cookie, not a query param.
 */
class HomeFeedClientImpl(
    private val api: JioSaavnApi,
    private val parser: JioSaavnParser
) : HomeFeedClient {

    /**
     * Builds the home feed's tabs and returns a [Feed] whose `getPagedData`
     * loads the initial shelves for the selected tab.
     *
     * The "Default" tab is always first; language tabs follow in [LANGUAGES]
     * order. If [SaavnSettings.defaultLanguages] is empty or unset, the
     * Default tab falls back to `["hindi"]`.
     */
    override suspend fun loadHomeFeed(): Feed<Shelf> {
        val defaultLanguages = SaavnSettings.defaultLanguages()

        val tabs = listOf(Tab(id = "default", title = "Default")) +
                LANGUAGES.map { Tab(id = it.lowercase(), title = it) }

        return Feed(tabs) { tab ->
            runSafe("HomeClient", emptyList<Shelf>().toFeedData()) {
                val language = when (tab?.id) {
                    // "Default" (or no tab) → comma-separated languages.
                    "default", null -> defaultLanguages.joinToString(",")
                    // Specific tab → that language only.
                    else -> tab.id
                }
                val response = api.home.getHomeData(language)
                val sections = parser.home.parseHomeSections(response)
                // Each section becomes a shelf; `language` is carried into each
                // shelf so the "See more" feed can scope its paginated requests.
                val shelves = sections.map { section -> buildShelf(section, language) }

                shelves.toFeedData()
            }
        }
    }

    /**
     * Converts one home section into a [Shelf].
     *
     * If the section advertises a `more` hint ([MoreInfo] present), the shelf
     * gets a paginated "See more" feed via [createMoreFeed]. Otherwise it's a
     * fixed list (e.g. "New Releases" with no pagination).
     *
     * @param section the parsed section from the home response.
     * @param language the language scope this section belongs to, passed down
     *   so pagination requests use the same scope as the initial fetch.
     */
    private fun buildShelf(section: HomeSection, language: String): Shelf {
        val more = section.moreInfo?.let { info ->
            createMoreFeed(info, language)
        }

        return Shelf.Lists.Items(
            id = section.id,
            title = section.title,
            list = section.items,
            subtitle = section.subtitle,
            more = more
        )
    }

    /**
     * Builds the paginated "See more" feed for one home shelf.
     *
     * Pagination metadata (endpoint call name, page/size param names, page size)
     * comes from [MoreInfo], which the server provides per shelf. Not all shelves
     * use the same call or params — some use `p`/`n`, others `page`/`size`, etc.
     *
     * ### Continuation
     *
     * Continuations are numeric strings (`"1"`, `"2"`, …). Pagination stops when
     * a page returns fewer items than [MoreInfo.defaultSize], signalling the end
     * of the shelf.
     */
    private fun createMoreFeed(info: MoreInfo, language: String): Feed<Shelf> {
        return Feed(emptyList()) { _ ->
            Feed.Data(

                PagedData.Continuous<Shelf> { continuation ->
                    val page = continuation?.toIntOrNull() ?: 1
                    runSafe("HomeClient", Page(emptyList<Shelf>(), null)) {
                        val response = api.home.getMore(
                            api = info.api,
                            page = page,
                            size = info.defaultSize,
                            pageParam = info.pageParam,
                            sizeParam = info.sizeParam,
                            language = language
                        )
                        val items = parser.home.parseMoreResponse(response)
                        // A short page means end of shelf — no next continuation.
                        val nextContinuation = if (items.size >= info.defaultSize) (page + 1).toString() else null
                        Page(items, nextContinuation)
                    }
                }

            )
        }
    }

}
