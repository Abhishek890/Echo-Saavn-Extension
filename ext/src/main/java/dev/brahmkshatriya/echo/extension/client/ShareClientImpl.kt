package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem

import dev.brahmkshatriya.echo.extension.utils.Extras

class ShareClientImpl : ShareClient {
    override suspend fun onShare(item: EchoMediaItem): String =
        item.extras[Extras.PERMA_URL] ?: "JioSaavn"
}
