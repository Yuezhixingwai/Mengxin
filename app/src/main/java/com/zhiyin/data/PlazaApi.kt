package com.zhiyin.data

import android.content.Context
import android.util.Base64
import com.zhiyin.logic.net.ApiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

data class PersonaLight(
    val id: Int,
    val name: String,
    val slogan: String,
    val keywords: String,
    val descriptionLight: String,
    val avatarUrl: String,
    val coverUrl: String,
    val backgroundUrl: String,
    val tags: List<String>,
    val category: String,
    val isPublic: Boolean,
    val isOfficial: Boolean,
    val authorId: Int,
    val likesCount: Int,
    val favsCount: Int,
    val commentsCount: Int,
    val hot: Int,
    val liked: Boolean = false,
    val faved: Boolean = false,
    val rank: Int = 0,
) {
    companion object {
        fun from(j: JSONObject): PersonaLight = PersonaLight(
            id = j.optInt("id"),
            name = j.optString("name"),
            slogan = j.optString("slogan"),
            keywords = j.optString("keywords"),
            descriptionLight = j.optString("description_light"),
            avatarUrl = j.optString("avatar_url"),
            coverUrl = j.optString("cover_url"),
            backgroundUrl = j.optString("background_url"),
            tags = j.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            category = j.optString("category", "其他"),
            isPublic = j.optBoolean("is_public"),
            isOfficial = j.optBoolean("is_official"),
            authorId = j.optInt("author_id"),
            likesCount = j.optInt("likes_count"),
            favsCount = j.optInt("favs_count"),
            commentsCount = j.optInt("comments_count"),
            hot = j.optInt("hot"),
            liked = j.optBoolean("liked"),
            faved = j.optBoolean("faved"),
            rank = j.optInt("rank"),
        )
    }
}

data class PersonaDetail(
    val light: PersonaLight,
    val descriptionFull: String,
    val pat: String,
    val coverHash: String,
    val avatarHash: String,
    val backgroundHash: String,
    val author: PlazaAuthor?,
    val createdAt: String,
)

data class PlazaAuthor(
    val id: Int,
    val nickname: String,
    val avatar: String,
    val followers: Int,
    val following: Boolean = false,
)

data class PlazaComment(
    val id: Int,
    val content: String,
    val createdAt: String,
    val userId: Int,
    val nickname: String,
    val avatar: String,
    val mine: Boolean,
)

data class MyStats(val follows: Int, val followers: Int, val likesReceived: Int, val favsReceived: Int)

object PlazaApi {

    fun gzipB64(text: String): String {
        val def = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        def.setInput(text.toByteArray(Charsets.UTF_8))
        def.finish()
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!def.finished()) bos.write(buf, 0, def.deflate(buf))
        def.end()
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun gunzipB64(b64: String): String {
        if (b64.isBlank()) return ""
        return try {
            val inf = Inflater(false)
            inf.setInput(Base64.decode(b64, Base64.NO_WRAP))
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && inf.needsInput()) break
                bos.write(buf, 0, n)
            }
            inf.end()
            String(bos.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun list(json: String): List<PersonaLight> {
        val arr = JSONObject(json).optJSONArray("list") ?: return emptyList()
        return (0 until arr.length()).map { PersonaLight.from(arr.optJSONObject(it)) }
    }

    private fun api(path: String, method: String = "GET", body: String? = null): String =
        ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + path, method, body, ApiGateway.getToken(null))

    private fun jbody(vararg pairs: Pair<String, Any?>): String {
        val j = JSONObject()
        pairs.forEach { (k, v) ->
            when (v) {
                null -> {}
                is List<*> -> j.put(k, JSONArray(v))
                is Boolean -> j.put(k, v)
                else -> j.put(k, v)
            }
        }
        return j.toString()
    }

    suspend fun categories(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val arr = JSONObject(api("/api/personas/categories")).optJSONArray("categories") ?: JSONArray()
            Result.success((0 until arr.length()).map { arr.optString(it) })
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun hot(limit: Int = 20): Result<List<PersonaLight>> = withContext(Dispatchers.IO) {
        try { Result.success(list(api("/api/personas/hot?limit=$limit"))) }
        catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun recommend(limit: Int = 12): Result<List<PersonaLight>> = withContext(Dispatchers.IO) {
        try { Result.success(list(api("/api/personas/recommend?limit=$limit"))) }
        catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun plaza(page: Int, limit: Int = 20, category: String = "", sort: String = "hot", q: String = ""): Result<Pair<Int, List<PersonaLight>>> = withContext(Dispatchers.IO) {
        try {
            val url = "/api/personas?page=$page&limit=$limit&category=${java.net.URLEncoder.encode(category, "UTF-8")}&sort=$sort&q=${java.net.URLEncoder.encode(q, "UTF-8")}"
            val j = JSONObject(api(url))
            Result.success(j.optInt("total") to list(j.toString()))
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun mine(): Result<List<PersonaLight>> = withContext(Dispatchers.IO) {
        try { Result.success(list(api("/api/personas/mine"))) }
        catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun favorites(): Result<List<PersonaLight>> = withContext(Dispatchers.IO) {
        try { Result.success(list(api("/api/personas/favorites"))) }
        catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun detail(id: Int): Result<PersonaDetail> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(api("/api/personas/$id"))
            val light = PersonaLight.from(j)
            val au = j.optJSONObject("author")
            Result.success(
                PersonaDetail(
                    light = light,
                    descriptionFull = j.optString("description_full"),
                    pat = j.optString("pat"),
                    coverHash = j.optString("cover_hash"),
                    avatarHash = j.optString("avatar_hash"),
                    backgroundHash = j.optString("background_hash"),
                    author = au?.let {
                        PlazaAuthor(
                            id = it.optInt("id"),
                            nickname = it.optString("nickname"),
                            avatar = it.optString("avatar"),
                            followers = it.optInt("followers"),
                        )
                    },
                    createdAt = j.optString("created_at"),
                )
            )
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun create(
        name: String, slogan: String, keywords: String, descriptionLight: String, descriptionFull: String,
        pat: String, tags: List<String>, category: String, isPublic: Boolean,
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val body = jbody(
                "name" to name, "slogan" to slogan, "keywords" to keywords, "description_light" to descriptionLight,
                "description_full_gz" to (if (descriptionFull.isBlank()) "" else gzipB64(descriptionFull)),
                "pat" to pat, "tags" to tags, "category" to category, "is_public" to isPublic,
            )
            val j = JSONObject(api("/api/personas", "POST", body))
            val id = j.optInt("id", 0)
            if (id <= 0) Result.failure(Exception(j.optString("error", "创建失败"))) else Result.success(id)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun update(
        id: Int, name: String, slogan: String, keywords: String, descriptionLight: String, descriptionFull: String,
        pat: String, tags: List<String>, category: String, isPublic: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = jbody(
                "name" to name, "slogan" to slogan, "keywords" to keywords, "description_light" to descriptionLight,
                "description_full_gz" to (if (descriptionFull.isBlank()) "" else gzipB64(descriptionFull)),
                "pat" to pat, "tags" to tags, "category" to category, "is_public" to isPublic,
            )
            api("/api/personas/$id", "PUT", body)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun delete(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try { api("/api/personas/$id", "DELETE"); Result.success(Unit) }
        catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun addToContacts(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try { api("/api/personas/$id/add", "POST", "{}"); Result.success(Unit) }
        catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun like(id: Int): Result<Pair<Boolean, Int>> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(api("/api/personas/$id/like", "POST", "{}"))
            Result.success(j.optBoolean("liked") to j.optInt("likes_count"))
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun favorite(id: Int): Result<Pair<Boolean, Int>> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(api("/api/personas/$id/favorite", "POST", "{}"))
            Result.success(j.optBoolean("faved") to j.optInt("favs_count"))
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun follow(personaId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(api("/api/personas/$personaId/follow", "POST", "{}"))
            Result.success(j.optBoolean("following"))
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun comments(id: Int, page: Int, limit: Int = 20): Result<Pair<Int, List<PlazaComment>>> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(api("/api/personas/$id/comments?page=$page&limit=$limit"))
            val arr = j.optJSONArray("comments") ?: JSONArray()
            val list = (0 until arr.length()).map {
                val c = arr.optJSONObject(it)
                PlazaComment(
                    id = c.optInt("id"), content = c.optString("content"), createdAt = c.optString("created_at"),
                    userId = c.optInt("user_id"), nickname = c.optString("nickname"), avatar = c.optString("avatar"),
                    mine = c.optBoolean("mine"),
                )
            }
            Result.success(j.optInt("total") to list)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun comment(id: Int, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try { api("/api/personas/$id/comment", "POST", jbody("content" to content)); Result.success(Unit) }
        catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun deleteComment(id: Int, cid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try { api("/api/personas/$id/comment/$cid", "DELETE"); Result.success(Unit) }
        catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun author(userId: Int): Result<Pair<PlazaAuthor, List<PersonaLight>>> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(api("/api/personas/author/$userId"))
            val au = j.optJSONObject("author")
            val a = au?.let {
                PlazaAuthor(
                    id = it.optInt("id"), nickname = it.optString("nickname"),
                    avatar = it.optString("avatar"), followers = it.optInt("followers"),
                    following = it.optBoolean("following"),
                )
            }
            Result.success((a ?: PlazaAuthor(userId, "", "", 0)) to list(j.toString()))
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun myStats(): Result<MyStats> = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(api("/api/personas/my-stats"))
            Result.success(MyStats(j.optInt("follows"), j.optInt("followers"), j.optInt("likesReceived"), j.optInt("favsReceived")))
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }

    suspend fun uploadImage(ctx: Context, personaId: Int, field: String, bytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiGateway.postMultipart("/api/personas/$personaId/$field", "file", bytes, ctx)
            Result.success(resp)
        } catch (e: Exception) { Result.failure(Exception(extractError(e))) }
    }
}
