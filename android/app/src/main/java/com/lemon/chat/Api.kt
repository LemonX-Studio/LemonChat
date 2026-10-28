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
// 2026-10-28 起后端迁至 Cloudflare Workers（EdgeOne 实名不互通弃用），带 WebSocket 实时推送
const val BASE_URL = "https://lemonchat-server.xxbeimc.kdns.fr/api"

object Api {
    // WebSocket 地址：由 BASE_URL 推导（wss://域名/ws）
    val WS_BASE: String get() = BASE_URL.removePrefix("https://").removePrefix("http://")
        .let { if (it.endsWith("/api")) it.dropLast(4) else it }
        .let { "wss://$it" }

    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    data class User(val id: Int, val username: String, val nickname: String, val bio: String = "", val isOfficial: Boolean = false, val avatar: String = "")
    data class Conversation(val peerId: Int, val nickname: String, val lastMsg: String, val lastFromMe: Boolean, val isOfficial: Boolean = false)
    data class Message(val id: Int, val fromMe: Boolean, val content: String, val time: Long,
        val senderId: Int = 0, val nickname: String = "", val avatar: String = "", val isOfficial: Boolean = false)
    data class AdminUser(val id: Int, val username: String, val nickname: String, val passHash: String,
        val regIp: String, val regUa: String, val online: Boolean, val createdAt: String, val isOfficial: Boolean = false)
    data class AdminStats(val total: Int, val online: Int, val users: List<AdminUser>)
    data class Group(val id: Int, val name: String, val owner: String, val ownerId: Int, val members: Int)
    data class GroupMessage(val id: Int, val senderId: Int, val content: String, val nickname: String, val time: Long,
        val avatar: String = "", val isOfficial: Boolean = false)

    // 带错误码的异常：NOT_FRIEND / BLOCKED 等
    class ApiError(val code: String, message: String) : Exception(message)

    // 头像内存缓存
    object AvatarStore {
        private val cache = HashMap<Int, String>()
        suspend fun get(userId: Int): String {
            cache[userId]?.let { return it }
            return withContext(Dispatchers.IO) {
                runCatching {
                    val r = get("avatar?userId=$userId")
                    val a = if (r.optBoolean("ok")) r.optString("avatar") else ""
                    cache[userId] = a
                    a
                }.getOrDefault("")
            }
        }
        fun put(userId: Int, avatar: String) { cache[userId] = avatar }
    }

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
                        m.getString("content"), m.optLong("created_at") / 1000,
                        m.getInt("sender_id"), m.optString("nickname"), m.optString("avatar"), m.optBoolean("is_official"))
                }
            }
        }

    suspend fun send(senderId: Int, receiverId: Int, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("messages", JSONObject()
                    .put("senderId", senderId).put("receiverId", receiverId).put("content", content))
                if (!r.optBoolean("ok")) throw ApiError(r.optString("code", ""), r.optString("error", "发送失败"))
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
                        u.optString("created_at", ""), u.optBoolean("is_official"))
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

    // ── 群聊 ──
    suspend fun groups(userId: Int): Result<List<Group>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("groups?userId=$userId")
                if (!r.optBoolean("ok")) error(r.optString("error", "拉群列表失败"))
                val arr: JSONArray = r.getJSONArray("groups")
                List(arr.length()) { i ->
                    val g = arr.getJSONObject(i)
                    Group(g.getInt("id"), g.getString("name"), g.getString("owner"),
                        g.getInt("owner_id"), g.getInt("members"))
                }
            }
        }

    suspend fun createGroup(userId: Int, name: String): Result<Group> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("groups", JSONObject().put("userId", userId).put("name", name))
                if (!r.optBoolean("ok")) error(r.optString("error", "建群失败"))
                val g = r.getJSONObject("group")
                Group(g.getInt("id"), g.getString("name"), "", userId, 1)
            }
        }

    suspend fun joinGroup(userId: Int, groupId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("groups/join", JSONObject().put("userId", userId).put("groupId", groupId))
                if (!r.optBoolean("ok")) error(r.optString("error", "加群失败"))
            }
        }

    suspend fun groupMessages(groupId: Int, after: Int = 0): Result<List<GroupMessage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("groupmessages?groupId=$groupId&after=$after")
                if (!r.optBoolean("ok")) error(r.optString("error", "拉群消息失败"))
                val arr: JSONArray = r.getJSONArray("messages")
                List(arr.length()) { i ->
                    val m = arr.getJSONObject(i)
                    GroupMessage(m.getInt("id"), m.getInt("senderId"), m.getString("content"),
                        m.getString("nickname"), m.getLong("time"))
                }
            }
        }

    suspend fun sendGroup(groupId: Int, senderId: Int, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("groupmessages", JSONObject()
                    .put("groupId", groupId).put("senderId", senderId).put("content", content))
                if (!r.optBoolean("ok")) throw ApiError(r.optString("code", ""), r.optString("error", "发送失败"))
            }
        }

    suspend fun setAvatar(userId: Int, dataUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("profile", JSONObject().put("userId", userId).put("avatar", dataUrl))
                if (!r.optBoolean("ok")) error(r.optString("error", "设置头像失败"))
                AvatarStore.put(userId, dataUrl)
            }
        }

    suspend fun deleteFriend(userId: Int, friendId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("friends", JSONObject().put("action", "delete").put("userId", userId).put("friendId", friendId))
                if (!r.optBoolean("ok")) error(r.optString("error", "删除失败"))
            }
        }

    suspend fun blockUser(userId: Int, targetId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("friends", JSONObject().put("action", "block").put("userId", userId).put("targetId", targetId))
                if (!r.optBoolean("ok")) error(r.optString("error", "拉黑失败"))
            }
        }

    suspend fun unblockUser(userId: Int, targetId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("friends", JSONObject().put("action", "unblock").put("userId", userId).put("targetId", targetId))
                if (!r.optBoolean("ok")) error(r.optString("error", "解除失败"))
            }
        }

    suspend fun listBlocked(userId: Int): Result<List<User>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("friends", JSONObject().put("action", "listBlocked").put("userId", userId))
                if (!r.optBoolean("ok")) error("拉黑名单失败")
                val arr: JSONArray = r.getJSONArray("blocked")
                List(arr.length()) { i ->
                    val u = arr.getJSONObject(i)
                    User(u.getInt("id"), u.getString("username"), u.getString("nickname"), "", u.optBoolean("is_official"))
                }
            }
        }

    suspend fun sendFriendRequest(fromId: Int, username: String, message: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("friendrequests", JSONObject()
                    .put("fromId", fromId).put("username", username).put("message", message))
                if (!r.optBoolean("ok")) throw ApiError(r.optString("code", ""), r.optString("error", "发送申请失败"))
            }
        }

    data class FriendRequest(val id: Int, val fromId: Int, val username: String, val nickname: String, val message: String, val isOfficial: Boolean)

    suspend fun friendRequests(userId: Int): Result<List<FriendRequest>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = get("friendrequests?userId=$userId")
                if (!r.optBoolean("ok")) error("拉申请失败")
                val arr: JSONArray = r.getJSONArray("requests")
                List(arr.length()) { i ->
                    val x = arr.getJSONObject(i)
                    FriendRequest(x.getInt("id"), x.getInt("from_id"), x.getString("username"),
                        x.getString("nickname"), x.optString("message"), x.optBoolean("is_official"))
                }
            }
        }

    suspend fun acceptFriendRequest(userId: Int, requestId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("friendrequests/accept", JSONObject().put("userId", userId).put("requestId", requestId))
                if (!r.optBoolean("ok")) error(r.optString("error", "通过失败"))
            }
        }

    suspend fun rejectFriendRequest(userId: Int, requestId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("friendrequests/reject", JSONObject().put("userId", userId).put("requestId", requestId))
                if (!r.optBoolean("ok")) error(r.optString("error", "拒绝失败"))
            }
        }

    suspend fun groupInvite(userId: Int, groupId: Int, username: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("groups", JSONObject().put("action", "invite")
                    .put("userId", userId).put("groupId", groupId).put("username", username))
                if (!r.optBoolean("ok")) error(r.optString("error", "邀请失败"))
            }
        }

    suspend fun groupLeave(userId: Int, groupId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("groups", JSONObject().put("action", "leave").put("userId", userId).put("groupId", groupId))
                if (!r.optBoolean("ok")) error(r.optString("error", "退出失败"))
            }
        }

    suspend fun groupDissolve(userId: Int, groupId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("groups", JSONObject().put("action", "dissolve").put("userId", userId).put("groupId", groupId))
                if (!r.optBoolean("ok")) error(r.optString("error", "解散失败"))
            }
        }

    suspend fun adminBan(adminId: Int, targetId: Int, days: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("admin/ban", JSONObject().put("adminId", adminId).put("targetId", targetId).put("days", days))
                if (!r.optBoolean("ok")) error(r.optString("error", "封禁失败"))
            }
        }

    suspend fun adminUnban(adminId: Int, targetId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("admin/unban", JSONObject().put("adminId", adminId).put("targetId", targetId))
                if (!r.optBoolean("ok")) error(r.optString("error", "解封失败"))
            }
        }

    suspend fun adminKick(adminId: Int, targetId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = post("admin/kick", JSONObject().put("adminId", adminId).put("targetId", targetId))
                if (!r.optBoolean("ok")) error(r.optString("error", "下线失败"))
            }
        }
}
