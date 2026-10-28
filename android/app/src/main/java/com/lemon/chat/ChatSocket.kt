package com.lemon.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── 实时消息门铃：服务器有新消息就敲一下（WebSocket）──
// 收到铃响的界面自己决定要不要刷新，发消息仍走 HTTP（稳）
object ChatSocket {
    // 每条新消息发出一个 Pair(发送者id, 接收者id)
    val events = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 16)

    private var ws: WebSocket? = null
    private var connectedUserId = 0
    @Volatile var online = false   // 门铃是否在线（在线时界面可以不轮询）

    @Synchronized
    fun start(userId: Int) {
        if (connectedUserId == userId && ws != null) return
        connectedUserId = userId
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (true) {
                try {
                    val req = Request.Builder().url("${Api.WS_BASE}/ws?userId=$userId").build()
                    val client = Api.client.newBuilder()
                        .readTimeout(0, TimeUnit.SECONDS)           // 长连接不超时
                        .pingInterval(25, TimeUnit.SECONDS)         // 保活心跳
                        .build()
                    ws = client.newWebSocket(req, object : WebSocketListener() {
                        override fun onOpen(w: WebSocket, resp: Response) { online = true }
                        override fun onMessage(w: WebSocket, text: String) {
                            try {
                                val j = JSONObject(text)
                                if (j.optString("type") == "new_message") {
                                    events.tryEmit(Pair(j.getInt("from"), j.getInt("to")))
                                }
                            } catch (_: Exception) {}
                        }
                        override fun onFailure(w: WebSocket, t: Throwable, resp: Response?) { online = false; ws = null }
                        override fun onClosed(w: WebSocket, code: Int, reason: String) { online = false; ws = null }
                        override fun onClosing(w: WebSocket, code: Int, reason: String) { online = false }
                    })
                    // 连着就等，断了再重连
                    while (ws != null) delay(1000)
                } catch (_: Exception) {}
                delay(3000) // 断线 3 秒后重连
            }
        }
    }
}
