package com.zhiyin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.zhiyin.logic.net.ApiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

fun extractError(e: Throwable): String {
    val msg = e.message ?: return "网络错误"
    val idx = msg.indexOf("{")
    if (idx >= 0) {
        try {
            val err = JSONObject(msg.substring(idx))
            return err.optString("error", "请求失败")
        } catch (_: Exception) {
        }
    }
    return if (msg.contains("HTTP ")) "请求失败" else msg
}

object AccountApi {

    data class LoginData(val token: String, val username: String, val userId: String)

    suspend fun login(username: String, password: String): Result<LoginData> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val resp = ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/auth/login", "POST", body.toString(), null)
            val json = JSONObject(resp)
            val token = json.optString("token", "")
            if (token.isEmpty()) {
                Result.failure(Exception(json.optString("error", "登录失败")))
            } else {
                val userObj = json.optJSONObject("user")
                Result.success(
                    LoginData(
                        token = token,
                        username = userObj?.optString("username", username) ?: username,
                        userId = userObj?.optInt("id", 0)?.toString() ?: "0"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(extractError(e)))
        }
    }

    suspend fun sendSmsCode(phone: String, action: String, captcha: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("phone", phone)
                put("action", action)
                put("captcha", captcha)
            }
            ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/auth/send-code", "POST", body.toString(), null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(extractError(e)))
        }
    }

    data class RegisterData(val bonusCoins: Double)

    suspend fun register(phone: String, username: String, password: String, code: String): Result<LoginData> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("phone", phone)
                    put("username", username)
                    put("password", password)
                    put("code", code)
                }
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/auth/phone-register", "POST", body.toString(), null
                )
                val json = JSONObject(resp)
                val token = json.optString("token", "")
                if (token.isEmpty()) {
                    Result.failure(Exception(json.optString("error", "注册失败")))
                } else {
                    val userObj = json.optJSONObject("user")
                    Result.success(
                        LoginData(
                            token = token,
                            username = userObj?.optString("username", username) ?: username,
                            userId = userObj?.optInt("id", 0)?.toString() ?: "0"
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception(extractError(e)))
            }
        }

    suspend fun resetPassword(phone: String, code: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("phone", phone)
                    put("code", code)
                    put("password", password)
                }
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/auth/reset-password", "POST", body.toString(), null
                )
                val json = JSONObject(resp)
                if (json.has("message")) Result.success(Unit)
                else Result.failure(Exception(json.optString("error", "重置失败")))
            } catch (e: Exception) {
                Result.failure(Exception(extractError(e)))
            }
        }

    suspend fun captchaBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app() ?: return@withContext null
            val data = ApiGateway.getRawWithCookie("/api/captcha?t=" + System.currentTimeMillis(), ctx)
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (_: Exception) {
            null
        }
    }

    data class UserInfo(
        val nickname: String,
        val username: String,
        val avatar: String,
        val gender: String,
        val birthday: String,
        val phone: String,
        val phoneMasked: String,
    )

    suspend fun getUserInfo(): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app()!!
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/user/info", "GET", null, ApiGateway.getToken(ctx)
            )
            val json = JSONObject(resp)
            Result.success(
                UserInfo(
                    nickname = json.optString("nickname", ""),
                    username = json.optString("username", ""),
                    avatar = json.optString("avatar", ""),
                    gender = json.optString("gender", ""),
                    birthday = json.optString("birthday", ""),
                    phone = json.optString("phone", ""),
                    phoneMasked = json.optString("phone_masked", ""),
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(extractError(e)))
        }
    }

    suspend fun saveUserInfo(nickname: String, gender: String, birthday: String, userProfile: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val ctx = com.zhiyin.logic.AppHolder.app()!!
                val body = JSONObject().apply {
                    put("nickname", nickname)
                    put("gender", gender)
                    put("birthday", birthday)
                }
                ApiGateway.put("/api/user/info", body.toString(), ctx)
                ctx.getSharedPreferences("zhiyin", Context.MODE_PRIVATE)
                    .edit().putString("user_nickname", nickname).apply()
                try {
                    val memUrl = ApiGateway.getMemoryServiceUrl()
                    val memUid = ApiGateway.getUserId(ctx)
                    if (!memUrl.isNullOrEmpty() && memUid.isNotEmpty()) {
                        val profBody = JSONObject().apply {
                            put("profile", JSONObject().apply {
                                put("nickname", nickname)
                                put("gender", gender)
                                put("birthday", birthday)
                                put("userProfile", userProfile)
                            })
                        }
                        ApiGateway.memoryRequestSync("$memUrl/api/profile", "POST", profBody.toString(), memUid)
                    }
                } catch (_: Exception) {
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception(extractError(e)))
            }
        }

    suspend fun fetchUserProfile9005(): String = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app()!!
            val memUrl = ApiGateway.getMemoryServiceUrl()
            val memUid = ApiGateway.getUserId(ctx)
            if (memUrl.isNullOrEmpty() || memUid.isEmpty()) return@withContext ""
            val resp = ApiGateway.memoryRequestSync("$memUrl/api/profile", "GET", null, memUid)
            JSONObject(resp).optJSONObject("profile")?.optString("userProfile", "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun changePasswordByOld(oldPwd: String, newPwd: String, captcha: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app()!!
            val body = JSONObject().apply {
                put("old_password", oldPwd)
                put("new_password", newPwd)
                if (captcha.isNotEmpty()) put("captcha", captcha)
            }
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/user/change-password", "POST", body.toString(), ApiGateway.getToken(ctx)
            )
            val json = JSONObject(resp)
            if (json.has("message") || json.optBoolean("success", false)) Result.success(Unit)
            else Result.failure(Exception(json.optString("error", "修改失败")))
        } catch (e: Exception) {
            Result.failure(Exception(extractError(e)))
        }
    }

    data class RealNameStatus(val verified: Boolean, val age: Int, val realName: String)

    suspend fun realNameStatus(): RealNameStatus? = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app() ?: return@withContext null
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/auth/real-name-status", "GET", null, ApiGateway.getToken(ctx)
            )
            val json = JSONObject(resp)
            RealNameStatus(
                verified = json.optBoolean("verified", false),
                age = json.optInt("age", 0),
                realName = json.optString("realName", ""),
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun changePhone(phone: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app()!!
            val body = JSONObject().apply {
                put("phone", phone)
                put("code", code)
            }
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/auth/change-phone", "POST", body.toString(), ApiGateway.getToken(ctx)
            )
            val json = JSONObject(resp)
            if (json.has("message")) Result.success(Unit)
            else Result.failure(Exception(json.optString("error", "换绑失败")))
        } catch (e: Exception) {
            Result.failure(Exception(extractError(e)))
        }
    }

    data class WalletTx(
        val type: String,
        val persona: String,
        val note: String,
        val amount: Double,
        val time: String,
    )

    data class WalletData(val balance: Double, val transactions: List<WalletTx>)

    suspend fun wallet(): Result<WalletData> = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app()!!
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/wallet", "GET", null, ApiGateway.getToken(ctx)
            )
            val json = JSONObject(resp)
            val txs = mutableListOf<WalletTx>()
            val arr: JSONArray? = json.optJSONArray("transactions")
            arr?.let {
                for (i in 0 until it.length()) {
                    val tx = it.getJSONObject(i)
                    txs.add(
                        WalletTx(
                            type = tx.optString("type", ""),
                            persona = tx.optString("persona_name", ""),
                            note = tx.optString("note", ""),
                            amount = tx.optDouble("amount", 0.0),
                            time = tx.optString("created_at", "").replace("T", " "),
                        )
                    )
                }
            }
            Result.success(WalletData(json.optDouble("balance", 0.0), txs))
        } catch (e: Exception) {
            Result.failure(Exception(extractError(e)))
        }
    }

    data class Announcement(val id: Int, val title: String, val content: String)

    suspend fun activeAnnouncement(): Announcement? = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app()!!
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/announcements/active", "GET", null, ApiGateway.getToken(ctx)
            )
            val arr = JSONObject(resp).optJSONArray("announcements") ?: return@withContext null
            if (arr.length() == 0) return@withContext null
            val latest = arr.getJSONObject(0)
            Announcement(latest.optInt("id"), latest.optString("title", "系统公告"), latest.optString("content", ""))
        } catch (_: Exception) {
            null
        }
    }

    fun isAnnouncementRead(ctx: Context, id: Int): Boolean =
        ctx.getSharedPreferences("zhiyin_announce", Context.MODE_PRIVATE).getBoolean("announce_read_$id", false)

    fun markAnnouncementRead(ctx: Context, id: Int) {
        ctx.getSharedPreferences("zhiyin_announce", Context.MODE_PRIVATE)
            .edit().putBoolean("announce_read_$id", true).apply()
    }

    data class FeedbackItem(val content: String, val reply: String, val time: String)

    suspend fun feedbackList(): List<FeedbackItem> = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app()!!
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/feedback", "GET", null, ApiGateway.getToken(ctx)
            )
            val arr = JSONObject(resp).optJSONArray("feedback") ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                FeedbackItem(
                    content = o.optString("content", ""),
                    reply = o.optString("reply", ""),
                    time = o.optString("created_at", "").replace("T", " ").let {
                        ApiGateway.toBeijingTime(o.optString("created_at", ""), "yyyy-MM-dd HH:mm")
                    },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun submitFeedback(content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app()!!
            val body = JSONObject().apply { put("content", content) }
            ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/feedback", "POST", body.toString(), ApiGateway.getToken(ctx)
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(extractError(e)))
        }
    }

    data class VersionInfo(val version: String, val changelog: String, val apkUrl: String)

    suspend fun versionLatest(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/version/latest", "GET", null, null
            )
            val json = JSONObject(resp)
            VersionInfo(
                version = json.optString("version", ""),
                changelog = json.optString("changelog", ""),
                apkUrl = json.optString("apk_url", ApiGateway.ZHIYIN_BASE + "/api/version/download"),
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchAgreement(type: String): String? = withContext(Dispatchers.IO) {
        try {
            ApiGateway.requestSync(ApiGateway.ZHIYIN_BASE + "/api/agreements/$type", "GET", null, null)
        } catch (_: Exception) {
            null
        }
    }

    data class RealNameCheck(
        val banned: Boolean,
        val required: Boolean,
        val verified: Boolean,
        val minorMode: Boolean,
        val banReason: String,
        val bannedUntil: String,
    )

    suspend fun realNameCheck(): RealNameCheck? = withContext(Dispatchers.IO) {
        try {
            val ctx = com.zhiyin.logic.AppHolder.app() ?: return@withContext null
            val resp = ApiGateway.requestSync(
                ApiGateway.ZHIYIN_BASE + "/api/auth/real-name-required", "GET", null, ApiGateway.getToken(ctx)
            )
            val json = JSONObject(resp)
            RealNameCheck(
                banned = json.optBoolean("banned", false),
                required = json.optBoolean("required", false),
                verified = json.optBoolean("verified", false),
                minorMode = json.optBoolean("minorMode", false),
                banReason = json.optString("ban_reason", ""),
                bannedUntil = json.optString("banned_until", ""),
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun verifyRealName(realName: String, idNumber: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val ctx = com.zhiyin.logic.AppHolder.app()!!
                val body = JSONObject().apply {
                    put("realName", realName)
                    put("idNumber", idNumber)
                }
                val resp = ApiGateway.requestSync(
                    ApiGateway.ZHIYIN_BASE + "/api/auth/real-name-verify", "POST", body.toString(),
                    ApiGateway.getToken(ctx)
                )
                val json = JSONObject(resp)
                if (json.optBoolean("verified", false)) Result.success(true)
                else Result.failure(Exception(json.optString("message", "认证失败，请检查信息")))
            } catch (e: Exception) {
                Result.failure(Exception(extractError(e)))
            }
        }
}
