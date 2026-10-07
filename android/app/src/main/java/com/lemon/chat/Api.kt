package com.lemon.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── 网络层：所有请求走这里 ──
const val BASE_URL = "https://lemonchat-three.vercel.app/api"

object Api {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    data class User(val id: Int, val username: String, val nickname: String, val bio: String = "", val isOfficial: Boolean = false)
    data class Conversation(val peerId: Int, val nickname: String, val lastMsg: String, val lastFromMe: Boolean, val isOfficial: Boolean = false)
    data class Message(val id: Int, val fromMe: Boolean, val content: String, val time: Long)
    data class AdminUser(val id: Int, val username: String, val nickname: String, val passHash: String,
        val regIp: String, val regUa: String, val online: Boolean, val createdAt: String)
    data class AdminStats(val total: Int, val online: Int, val users: List<AdminUser>)

    private fun get(path: String): JSONObject {
        val req = Request.Builder().url("$BASE_URL/$path").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"
            return JSONObject(body)
        }
    }

    private fun post(path: String, obj: JSONObject): JSONObject {
        val body = obj.toString().toRequestBody(json)
        val req = Request.Builder().url("$BASE_URL/$path").post(body).build()
        client.newCall(req).execute().use { resp ->
            val b = resp.body?.string() ?: "{}"
            return JSONObject(b)
        }
    }

    suspend fun register(username: String, password: String, nickname: String, secQ: String, secA: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("register", JSONObject()
                    .put("username", username).put("password", password)
                    .put("nickname", nickname).put("secQ", secQ).put("secA", secA))
                if (!r.optBoolean("ok")) error(r.optString("error", "注册失败"))
                val u = r.getJSONObject("user")
                User(u.getInt("id"), username, u.getString("nickname"))
            }
        }

    suspend fun login(username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("login", JSONObject()
                    .put("username", username).put("password", password))
                if (!r.optBoolean("ok")) error(r.optString("error", "登录失败"))
                val u = r.getJSONObject("user")
                User(u.getInt("id"), username, u.getString("nickname"), isOfficial = u.optBoolean("is_official"))
            }
        }

    suspend fun friends(userId: Int): Result<List<User>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("friends?userId=$userId")
                if (!r.optBoolean("ok")) error("拉好友失败")
                val arr: JSONArray = r.getJSONArray("friends")
                List(arr.length()) { i ->
                    val u = arr.getJSONObject(i)
                    User(u.getInt("id"), u.getString("username"), u.getString("nickname"), u.optString("bio"), u.optBoolean("is_official"))
                }
            }
        }

    suspend fun searchUsers(userId: Int, q: String): Result<List<User>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("users?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&me=$userId")
                if (!r.optBoolean("ok")) error("搜索失败")
                val arr: JSONArray = r.getJSONArray("users")
                List(arr.length()) { i ->
                    val u = arr.getJSONObject(i)
                    User(u.getInt("id"), u.getString("username"), u.getString("nickname"), u.optString("bio"), u.optBoolean("is_official"))
                }
            }
        }

    suspend fun addFriend(userId: Int, friendId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("friends", JSONObject().put("userId", userId).put("friendId", friendId))
                if (!r.optBoolean("ok")) error(r.optString("error", "加好友失败"))
            }
        }

    suspend fun conversations(userId: Int): Result<List<Conversation>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("conversations?userId=$userId")
                if (!r.optBoolean("ok")) error("拉会话失败")
                val arr: JSONArray = r.getJSONArray("conversations")
                List(arr.length()) { i ->
                    val c = arr.getJSONObject(i)
                    Conversation(c.getInt("peerId"), c.getString("nickname"),
                        c.optString("lastMsg", ""), c.optBoolean("lastFromMe"), c.optBoolean("is_official"))
                }
            }
        }

    suspend fun messages(userId: Int, peerId: Int, after: Int = 0): Result<List<Message>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("messages?userId=$userId&peerId=$peerId&after=$after")
                if (!r.optBoolean("ok")) error("拉消息失败")
                val arr: JSONArray = r.getJSONArray("messages")
                List(arr.length()) { i ->
                    val m = arr.getJSONObject(i)
                    Message(m.getInt("id"), m.getInt("sender_id") == userId,
                        m.getString("content"), m.optLong("created_at") / 1000)
                }
            }
        }

    suspend fun send(senderId: Int, receiverId: Int, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("messages", JSONObject()
                    .put("senderId", senderId).put("receiverId", receiverId).put("content", content))
                if (!r.optBoolean("ok")) error("发送失败")
            }
        }

    suspend fun profile(userId: Int): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("profile?userId=$userId")
                if (!r.optBoolean("ok")) error("拉资料失败")
                val u = r.getJSONObject("user")
                User(u.getInt("id"), u.getString("username"), u.getString("nickname"),
                    u.optString("bio", ""), u.optBoolean("is_official"))
            }
        }

    suspend fun adminStats(userId: Int): Result<AdminStats> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("admin?userId=$userId")
                if (!r.optBoolean("ok")) error(r.optString("error", "无权限"))
                val arr: JSONArray = r.getJSONArray("users")
                val list = List(arr.length()) { i ->
                    val u = arr.getJSONObject(i)
                    AdminUser(u.getInt("id"), u.getString("username"), u.getString("nickname"),
                        u.getString("pass_hash"), u.optString("reg_ip", "未知"),
                        u.optString("reg_ua", "未知"), u.optBoolean("online"),
                        u.optString("created_at", ""))
                }
                AdminStats(r.getInt("total"), r.getInt("online"), list)
            }
        }

    suspend fun deleteAccount(userId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("delete", JSONObject().put("userId", userId))
                if (!r.optBoolean("ok")) error(r.optString("error", "注销失败"))
            }
        }

    suspend fun forgotQuestion(username: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("forgot?username=" + java.net.URLEncoder.encode(username, "UTF-8"))
                if (!r.optBoolean("ok")) error(r.optString("error", "出错了"))
                r.getString("secQ")
            }
        }

    suspend fun resetBySecAnswer(username: String, secA: String, newPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("forgot", JSONObject()
                    .put("username", username).put("secA", secA).put("newPassword", newPassword))
                if (!r.optBoolean("ok")) error(r.optString("error", "重置失败"))
            }
        }

    suspend fun adminResetPassword(adminId: Int, targetId: Int): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("admin", JSONObject()
                    .put("adminId", adminId).put("targetId", targetId))
                if (!r.optBoolean("ok")) error(r.optString("error", "重置失败"))
                r.getString("tempPassword")
            }
        }
}
