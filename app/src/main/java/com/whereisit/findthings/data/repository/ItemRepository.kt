package com.whereisit.findthings.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.whereisit.findthings.data.model.*
import com.whereisit.findthings.data.network.ApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

class ItemRepository(
    private val apiFactory: ApiFactory,
    private val sessionRepository: SessionRepository,
    private val contentResolver: ContentResolver,
    private val gson: Gson = Gson()
) {
    private companion object {
        const val MAX_UPLOAD_IMAGE_BYTES = 900 * 1024
        const val SPEC_MAX_LONG_EDGE = 1600
        const val SPEC_JPEG_QUALITY = 82
        const val MIN_LONG_EDGE = 720
    }

    @Volatile
    private var runtimeAuth: RuntimeAuth? = null

    fun setRuntimeAuth(baseUrl: String, token: String) {
        runtimeAuth = RuntimeAuth(baseUrl = baseUrl, token = token)
    }

    fun clearRuntimeAuth() {
        runtimeAuth = null
    }

    suspend fun login(username: String, password: String, baseUrl: String): TokenResponse {
        return guard {
            val raw = apiFactory.service(baseUrl).login(LoginRequest(username, password))
            parseLoginToken(raw)
        }
    }

    suspend fun checkHealth(baseUrl: String): Boolean {
        return try {
            val raw = apiFactory.service(baseUrl).health()
            val data = unwrapData(raw)
            val status = when {
                data.isJsonObject -> data.asJsonObject.get("status")?.asStringOrNull()
                raw.isJsonObject -> raw.asJsonObject.get("status")?.asStringOrNull()
                else -> null
            }
            status.equals("ok", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun changePassword(newPassword: String): MessageResponse {
        val (baseUrl, auth) = authInfo()
        return guard {
            val raw = apiFactory.service(baseUrl).updateMe(
                auth = auth,
                payload = ProfileUpdateRequest(password = newPassword)
            )
            parseMessageResponse(raw)
        }
    }

    suspend fun me(): UserMe {
        val (baseUrl, auth) = authInfo()
        return guard { fromJson(unwrapData(apiFactory.service(baseUrl).me(auth)), UserMe::class.java) }
    }

    suspend fun meta(): MetaPack {
        val (baseUrl, auth) = authInfo()
        val api = apiFactory.service(baseUrl)
        return guard {
            val housesRaw = unwrapData(api.houses(auth))
            val roomsRaw = unwrapData(api.rooms(auth))
            val categoriesRaw = unwrapData(api.categories(auth))
            val tagsRaw = unwrapData(api.tags(auth))
            MetaPack(
                houses = fromJsonList(housesRaw, houseListType()),
                rooms = fromJsonList(roomsRaw, roomListType()),
                categories = fromJsonList(categoriesRaw, categoryListType()),
                tags = fromJsonList(tagsRaw, tagListType())
            )
        }
    }

    suspend fun listItems(filter: ItemFilter, page: Int, pageSize: Int): PagedItems {
        val (baseUrl, auth) = authInfo()
        return guard {
            val primaryTagId = filter.tagIds.singleOrNull()
            val raw = apiFactory.service(baseUrl).items(
                auth = auth,
                q = filter.keyword.takeIf { it.isNotBlank() },
                categoryId = filter.categoryId,
                houseId = filter.houseId,
                roomId = filter.roomId,
                tagId = primaryTagId,
                page = page,
                pageSize = pageSize,
                sortBy = "updated_at",
                sortOrder = "desc"
            )

            val parsed = parsePagedItems(unwrapData(raw), page, pageSize)
            if (filter.tagIds.size <= 1) {
                parsed
            } else {
                val filtered = parsed.items.filter { item ->
                    val owned = item.tags.map { it.id }.toSet()
                    filter.tagIds.all { it in owned }
                }
                parsed.copy(items = filtered, total = filtered.size)
            }
        }
    }

    suspend fun createItem(payload: ItemCreatePayload, imageEntries: List<PendingItemImage>): ItemDto {
        val (baseUrl, auth) = authInfo()
        return guard {
            val api = apiFactory.service(baseUrl)
            val data = gson.toJson(payload).toRequestBody("text/plain".toMediaType())
            val files = imageEntries.mapIndexed { index, entry -> uriToPart(index, entry.uri) }
            val fileKeys = imageEntries.map { it.fileKey.toRequestBody("text/plain".toMediaType()) }
            fromJson(unwrapData(api.createItem(auth, data, files, fileKeys)), ItemDto::class.java)
        }
    }

    suspend fun updateItem(
        itemId: Int,
        payload: ItemCreatePayload,
        imageEntries: List<PendingItemImage>,
        removeImageIds: List<Int>
    ) {
        val (baseUrl, auth) = authInfo()
        guard {
            val api = apiFactory.service(baseUrl)
            removeImageIds.forEach { parseMessageResponse(api.deleteItemImage(auth, itemId, it)) }
            val data = gson.toJson(payload).toRequestBody("text/plain".toMediaType())
            val files = imageEntries.mapIndexed { index, entry -> uriToPart(index, entry.uri) }
            val fileKeys = imageEntries.map { it.fileKey.toRequestBody("text/plain".toMediaType()) }
            fromJson(unwrapData(api.updateItem(auth, itemId, data, files, fileKeys)), ItemDto::class.java)
        }
    }

    suspend fun deleteItem(itemId: Int) {
        val (baseUrl, auth) = authInfo()
        guard { parseMessageResponse(apiFactory.service(baseUrl).deleteItem(auth, itemId)) }
    }

    fun fullImageUrl(baseUrl: String, raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val host = baseUrl.removeSuffix("/")
        val path = if (raw.startsWith('/')) raw else "/$raw"
        return "$host$path"
    }

    private fun parsePagedItems(raw: JsonElement, requestPage: Int, requestPageSize: Int): PagedItems {
        if (raw.isJsonNull) {
            return PagedItems(
                items = emptyList(),
                total = 0,
                page = requestPage.coerceAtLeast(1),
                pageSize = requestPageSize.coerceAtLeast(1)
            )
        }

        if (raw.isJsonArray) {
            val list = gson.fromJson<List<ItemDto>>(raw, itemListType())
            val sorted = list.sortedByDescending { it.updatedAt }
            return PagedItems(
                items = sorted,
                total = sorted.size,
                page = 1,
                pageSize = maxOf(sorted.size, 1)
            )
        }

        val obj = raw.asJsonObject
        val listElement = findItemsArray(obj)
        val list = gson.fromJson<List<ItemDto>>(listElement ?: JsonArray(), itemListType())
            .sortedByDescending { it.updatedAt }

        val total = pickInt(obj, "total", "count", "total_count") ?: list.size
        val page = pickInt(obj, "page", "current_page") ?: requestPage
        val pageSize = pickInt(obj, "page_size", "size", "per_page") ?: requestPageSize

        return PagedItems(
            items = list,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    private fun findItemsArray(obj: JsonObject): JsonElement? {
        val direct = sequenceOf("items", "data", "list", "results")
            .mapNotNull { key -> obj.get(key)?.takeIf { it.isJsonArray } }
            .firstOrNull()
        if (direct != null) return direct

        val nestedData = obj.get("data")
        if (nestedData != null && nestedData.isJsonObject) {
            return findItemsArray(nestedData.asJsonObject)
        }
        return null
    }

    private fun pickInt(obj: JsonObject, vararg keys: String): Int? {
        for (key in keys) {
            val element = obj.get(key) ?: continue
            val value = element.asIntOrNull()
            if (value != null) return value
        }

        val nestedData = obj.get("data")
        if (nestedData != null && nestedData.isJsonObject) {
            for (key in keys) {
                val element = nestedData.asJsonObject.get(key) ?: continue
                val value = element.asIntOrNull()
                if (value != null) return value
            }
        }
        return null
    }

    private fun JsonElement.asIntOrNull(): Int? {
        return try {
            if (!isJsonPrimitive) null else asInt
        } catch (_: Exception) {
            null
        }
    }

    private fun itemListType() = object : TypeToken<List<ItemDto>>() {}.type
    private fun houseListType() = object : TypeToken<List<HouseDto>>() {}.type
    private fun roomListType() = object : TypeToken<List<RoomDto>>() {}.type
    private fun categoryListType() = object : TypeToken<List<CategoryDto>>() {}.type
    private fun tagListType() = object : TypeToken<List<TagDto>>() {}.type

    private fun parseLoginToken(raw: JsonElement): TokenResponse {
        val tokenContainer = unwrapData(raw).takeIf { it.isJsonObject }?.asJsonObject
            ?: throw AppError.Business("登录返回格式错误")
        val token = tokenContainer.get("access_token")?.asStringOrNull().orEmpty()
        val tokenType = tokenContainer.get("token_type")?.asStringOrNull().orEmpty().ifBlank { "bearer" }
        if (token.isBlank()) {
            val rootObj = raw.takeIf { it.isJsonObject }?.asJsonObject
            val envelopeMessage = rootObj?.get("message")?.asStringOrNull()
            throw AppError.Business(envelopeMessage ?: "登录返回缺少 access_token")
        }
        return TokenResponse(accessToken = token, tokenType = tokenType)
    }

    private fun parseMessageResponse(raw: JsonElement): MessageResponse {
        val data = unwrapData(raw)
        if (data.isJsonObject && data.asJsonObject.has("message")) {
            return fromJson(data, MessageResponse::class.java)
        }
        if (raw.isJsonObject) {
            val rootMsg = raw.asJsonObject.get("message")?.asStringOrNull()
            if (!rootMsg.isNullOrBlank()) return MessageResponse(rootMsg)
        }
        return MessageResponse("操作成功")
    }

    private fun <T> fromJson(raw: JsonElement, cls: Class<T>): T {
        return gson.fromJson(raw, cls)
    }

    private fun <T> fromJsonList(raw: JsonElement, type: java.lang.reflect.Type): List<T> {
        if (!raw.isJsonArray) return emptyList()
        return gson.fromJson(raw, type)
    }

    private fun unwrapData(raw: JsonElement): JsonElement {
        if (!raw.isJsonObject) return raw
        val obj = raw.asJsonObject
        val hasEnvelopeFields = obj.has("code") && obj.has("message") && obj.has("data")
        if (!hasEnvelopeFields) return raw

        val code = obj.get("code")?.asIntOrNull()
        val message = obj.get("message")?.asStringOrNull()
        if (code != null && code != 0) {
            throw AppError.Business(message ?: "请求失败($code)")
        }
        return obj.get("data") ?: JsonNull.INSTANCE
    }

    private suspend fun authInfo(): Pair<String, String> {
        runtimeAuth?.let {
            if (it.baseUrl.isNotBlank() && it.token.isNotBlank()) {
                return it.baseUrl to "Bearer ${it.token}"
            }
        }
        val settings = sessionRepository.current()
        val base = settings.activeBaseUrl()
        if (base.isBlank()) throw AppError.Validation("请先配置服务器地址")
        if (settings.token.isBlank()) throw AppError.Unauthorized()
        return base to "Bearer ${settings.token}"
    }

    private suspend fun uriToPart(index: Int, uri: Uri): MultipartBody.Part = withContext(Dispatchers.IO) {
        val bytes = compressToUploadJpeg(uri)
        val body: RequestBody = bytes.toRequestBody("image/jpeg".toMediaType())
        MultipartBody.Part.createFormData("files", "upload_${System.currentTimeMillis()}_$index.jpg", body)
    }

    private fun compressToUploadJpeg(uri: Uri): ByteArray {
        val sourceBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw AppError.Validation("无法读取图片")

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            throw AppError.Validation("图片格式不支持")
        }

        val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, SPEC_MAX_LONG_EDGE)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, decodeOptions)
            ?: throw AppError.Validation("无法解析图片")

        // 无论原图是否已满足尺寸要求，都先走一遍统一缩放流程，再做 JPEG 压缩
        val targetLongEdge = max(decoded.width, decoded.height).coerceAtMost(SPEC_MAX_LONG_EDGE)
        val scaled = resizeLongEdge(decoded, targetLongEdge)
        if (scaled !== decoded) {
            decoded.recycle()
        }

        val normalized = if (scaled.hasAlpha()) {
            val opaque = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(opaque)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            scaled.recycle()
            opaque
        } else {
            scaled
        }

        val output = ByteArrayOutputStream()
        var quality = SPEC_JPEG_QUALITY
        var current = normalized
        var result: ByteArray

        while (true) {
            output.reset()
            current.compress(Bitmap.CompressFormat.JPEG, quality, output)
            result = output.toByteArray()
            if (result.size <= MAX_UPLOAD_IMAGE_BYTES) break

            if (quality > 56) {
                quality -= 8
                continue
            }

            val currentLongEdge = max(current.width, current.height)
            if (currentLongEdge <= MIN_LONG_EDGE) break

            val nextTarget = (currentLongEdge * 0.85f).toInt().coerceAtLeast(MIN_LONG_EDGE)
            val downsized = resizeLongEdge(current, nextTarget)
            if (downsized !== current) {
                current.recycle()
                current = downsized
                quality = SPEC_JPEG_QUALITY
                continue
            }
            break
        }

        current.recycle()

        if (result.size > MAX_UPLOAD_IMAGE_BYTES) {
            throw AppError.Validation("图片压缩后仍超过 900KB，请换一张图片")
        }
        return result
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetLongEdge: Int): Int {
        val longEdge = max(width, height)
        if (longEdge <= targetLongEdge) return 1
        var sample = 1
        while (longEdge / sample > targetLongEdge) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun resizeLongEdge(bitmap: Bitmap, targetLongEdge: Int): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val srcLong = max(srcW, srcH)

        val scale = targetLongEdge.toFloat() / srcLong.toFloat()
        val dstW = (srcW * scale).toInt().coerceAtLeast(1)
        val dstH = (srcH * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
    }

    private suspend fun <T> guard(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: AppError) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401) throw AppError.Unauthorized()
            val msg = e.response()?.errorBody()?.string().orEmpty()
            val parsed = runCatching { JsonParser.parseString(msg) }.getOrNull()
            val envelopeMessage = parsed
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("message")
                ?.asStringOrNull()
            val detail = parsed
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("detail")
                ?.asStringOrNull()
            throw AppError.Business(envelopeMessage ?: detail ?: "请求失败(${e.code()})")
        } catch (_: IOException) {
            throw AppError.Network()
        } catch (e: Exception) {
            throw AppError.Unknown(e.message ?: "未知错误")
        }
    }
}

private data class RuntimeAuth(
    val baseUrl: String,
    val token: String
)

data class MetaPack(
    val houses: List<HouseDto>,
    val rooms: List<RoomDto>,
    val categories: List<CategoryDto>,
    val tags: List<TagDto>
)

data class PagedItems(
    val items: List<ItemDto>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

data class ItemFilter(
    val keyword: String = "",
    val houseId: Int? = null,
    val roomId: Int? = null,
    val categoryId: Int? = null,
    val tagIds: Set<Int> = emptySet()
)

data class PendingItemImage(
    val uri: Uri,
    val fileKey: String = "img_${UUID.randomUUID().toString().replace("-", "")}"
)

private fun JsonElement.asStringOrNull(): String? {
    return try {
        if (!isJsonPrimitive) null else asString
    } catch (_: Exception) {
        null
    }
}

