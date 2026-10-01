package com.zhiyin.data

import com.zhiyin.logic.net.ApiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class StickerPack(
    val id: Int,
    val name: String,
    val description: String,
    val cover: String,
    val price: Double,
    val isDefault: Boolean,
    val itemCount: Int,
    val owned: Boolean,
) {
    companion object {
        fun from(j: JSONObject): StickerPack = StickerPack(
            id = j.optInt("id"), name = j.optString("name"), description = j.optString("description"),
            cover = j.optString("cover"), price = j.optDouble("price"), isDefault = j.optBoolean("is_default"),
            itemCount = j.optInt("item_count"), owned = j.optBoolean("owned"),
        )
    }
}

data class StickerPackDetail(
    val pack: StickerPack,
    val items: List<Pair<String, String>>,
)

object StickerShopApi {

    suspend fun packs(): Result<List<StickerPack>> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/sticker-packs", "GET", null, ApiGateway.getToken(null)))
            val arr = j.optJSONArray("packs") ?: JSONArray()
            Result.success((0 until arr.length()).map { StickerPack.from(arr.optJSONObject(it)) })
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun detail(id: Int): Result<StickerPackDetail> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/sticker-packs/$id", "GET", null, ApiGateway.getToken(null)))
            val arr = j.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).map {
                val itj = arr.optJSONObject(it)
                itj.optString("name") to itj.optString("description")
            }
            Result.success(StickerPackDetail(StickerPack.from(j), items))
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun acquire(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/sticker-packs/$id/acquire", "POST", "{}", ApiGateway.getToken(null))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun unacquire(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/sticker-packs/$id/unacquire", "DELETE", null, ApiGateway.getToken(null))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }
}

data class NotifyItem(
    val id: Int,
    val title: String,
    val content: String,
    val type: String,
    val read: Boolean,
    val personaId: Int?,
    val createdAt: String,
)

object NotificationApi {

    suspend fun list(page: Int, limit: Int = 20): Result<Pair<Int, List<NotifyItem>>> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/notifications?page=$page&limit=$limit", "GET", null, ApiGateway.getToken(null)))
            val arr = j.optJSONArray("items") ?: JSONArray()
            val list = (0 until arr.length()).map {
                val it = arr.optJSONObject(it)
                val extra = it.optJSONObject("extra")
                NotifyItem(
                    id = it.optInt("id"), title = it.optString("title"), content = it.optString("content"),
                    type = it.optString("type"), read = it.optBoolean("read"),
                    personaId = extra?.optInt("persona_id")?.takeIf { v -> v > 0 },
                    createdAt = it.optString("created_at"),
                )
            }
            Result.success(j.optInt("total") to list)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun unreadCount(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/notifications/unread-count", "GET", null, ApiGateway.getToken(null)))
            Result.success(j.optInt("unread"))
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun markRead(id: Int? = null, all: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                if (all) put("all", true) else if (id != null) put("id", id)
            }
            ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/notifications/read", "POST", body.toString(), ApiGateway.getToken(null))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }
}

object PreferenceApi {
    suspend fun get(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/user/preferences", "GET", null, ApiGateway.getToken(null)))
            val arr = j.optJSONArray("tags") ?: JSONArray()
            Result.success((0 until arr.length()).map { arr.optString(it) })
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun save(tags: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("tags", JSONArray(tags)) }
            ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/user/preferences", "PUT", body.toString(), ApiGateway.getToken(null))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }
}
