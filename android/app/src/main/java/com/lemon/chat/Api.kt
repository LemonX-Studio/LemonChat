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
// TODO 部署后把下面地址换成你的 Vercel 域名
const val BASE_URL = "https://LEMON-你的名字.vercel.app/api"

object Api {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    data class User(val id: Int, val username: String, val nickname: String)
    data class Conversation(val peerId: Int, val nickname: String, val lastMsg: String, val lastFromMe: Boolean)
    data class Message(val id: Int, val fromMe: Boolean, val content: String, val time: Long)

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
            val body2 = resp.body?.string() ?: "{}"
            return JSONObject(body2)
        }
    }

    suspend fun register(username: String, password: String, nickname: String, secQ: String, secA: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("register", JSONObject()
                    .put("username", username).put("password", password)
                    .put("nickname", nickname).put("secQ", secQ).put("secA", secA))
                if (!r.optBoolean("ok")) error(r.optString("error", "注册失败"))
                User(r.getJSONObject("user").getInt("id"), username, r.getJSONObject("user").getString("nickname"))
            }
        }

    suspend fun login(username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("login", JSONObject()
                    .put("username", username).put("password", password))
                if (!r.optBoolean("ok")) error(r.optString("error", "登录失败"))
                User(r.getJSONObject("user").getInt("id"), username, r.getJSONObject("user").getString("nickname"))
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
                    User(u.getInt("id"), u.getString("username"), u.getString("nickname"))
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
                    User(u.getInt("id"), u.getString("username"), u.getString("nickname"))
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
                        c.optString("lastMsg", ""), c.optBoolean("lastFromMe"))
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
}
