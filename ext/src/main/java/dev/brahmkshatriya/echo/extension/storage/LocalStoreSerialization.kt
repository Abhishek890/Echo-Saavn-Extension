package dev.brahmkshatriya.echo.extension.storage

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.utils.Extras

internal fun JsonObjectBuilder.putCommonFields(item: EchoMediaItem) {
    put("id", item.id)
    put("title", item.title)
    item.subtitle?.let { put("subtitle", it) }
    put("permaUrl", item.extras[Extras.PERMA_URL] ?: "")
    (item.cover as? ImageHolder.NetworkRequestImageHolder)?.request?.url?.let {
        put("coverUrl", it)
    }
}

internal data class CommonFields(
    val id: String,
    val title: String,
    val subtitle: String?,
    val cover: ImageHolder?,
    val permaUrl: String
)

internal fun JsonObject.parseCommonFields(): CommonFields? {
    val id = this["id"]?.jsonPrimitive?.content ?: return null
    val title = this["title"]?.jsonPrimitive?.content ?: ""
    val subtitle = this["subtitle"]?.jsonPrimitive?.content
    val coverUrl = this["coverUrl"]?.jsonPrimitive?.content
    val permaUrl = this["permaUrl"]?.jsonPrimitive?.content ?: ""

    return CommonFields(
        id = id,
        title = title,
        subtitle = subtitle,
        cover = coverUrl?.toImageHolder(),
        permaUrl = permaUrl
    )
}
