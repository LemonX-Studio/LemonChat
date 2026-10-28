package com.lemon.chat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.ModalBottomSheet
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemon.chat.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LemonChatTheme {
                val prefs = getSharedPreferences("lemon_session", MODE_PRIVATE)
                var session by remember {
                    val savedId = prefs.getInt("userId", -1)
                    mutableStateOf(
                        if (savedId > 0) Api.User(savedId, prefs.getString("username", "") ?: "",
                            prefs.getString("nickname", "") ?: "", "", prefs.getBoolean("isOfficial", false))
                        else null
                    )
                }
                AnimatedContent(session, transitionSpec = {
                    (slideInHorizontally { it } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { -it } + fadeOut(tween(300)))
                }) { user ->
                    if (user != null) MainScreen(me = user, onLogout = {
                        prefs.edit().clear().apply(); session = null
                    })
                    else LoginScreen(onLogin = {
                        prefs.edit().putInt("userId", it.id)
                            .putString("username", it.username)
                            .putString("nickname", it.nickname)
                            .putBoolean("isOfficial", it.isOfficial).apply()
                        session = it
                    })
                }
            }
        }
    }
}

// ══════════════ 登录 / 注册（已对接后端） ══════════════
@Composable
fun LoginScreen(onLogin: (Api.User) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var secQ by remember { mutableStateOf("") }
    var secA by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showForgot by remember { mutableStateOf(false) }
    var banDays by remember { mutableStateOf(0) }

    fun submit() {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            val result = if (isRegister)
                Api.register(username.trim(), password, nickname.trim(), secQ.trim(), secA.trim())
            else Api.login(username.trim(), password)
            busy = false
            result.onSuccess { onLogin(it) }
                .onFailure { e ->
                    val msg = e.message ?: "网络出错了"
                    if (msg.contains("已被封禁")) {
                        banDays = Regex("\\d+").find(msg)?.value?.toIntOrNull() ?: 7
                    } else error = msg
                }
        }
    }

    if (banDays > 0) {
        // 封禁提示页
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFD64545), Color(0xFF7A1F1F)))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Block, null, tint = Color.White, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(18.dp))
            Text("您的账号已被封禁", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("剩余 $banDays 天", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = { banDays = 0 },
                shape = LemonShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) { Text("返回登录", color = Color(0xFFD64545), fontWeight = FontWeight.SemiBold) }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LemonColors.GreenLight, LemonColors.Bg)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))
            Box(
                Modifier
                    .size(96.dp)
                    .clip(LemonShapes.card)
                    .background(Brush.linearGradient(listOf(LemonColors.Green, LemonColors.GreenDark))),
                contentAlignment = Alignment.Center
            ) { Text("🍋", fontSize = 48.sp) }
            Spacer(Modifier.height(14.dp))
            Text("Lemon Chat", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
            Text("新鲜的聊天体验", fontSize = 14.sp, color = LemonColors.TextSub)
            Spacer(Modifier.height(32.dp))

            LemonField("用户名", username) { username = it }
            if (isRegister) {
                LemonField("昵称", nickname) { nickname = it }
                LemonField("密保问题（如：我的小学名）", secQ) { secQ = it }
                LemonField("密保答案", secA) { secA = it }
            }
            LemonField("密码", password, isPassword = true) { password = it }
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("⚠️ $error", color = Color(0xFFD64545), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = LemonShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = LemonColors.Green)
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Text(if (isRegister) "注 册" else "登 录", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            if (!isRegister) {
                // 通行证登录：跳转通行证APP确认后自动登录官方账号
                val passLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    if (res.resultCode == android.app.Activity.RESULT_OK) {
                        val u = res.data?.getStringExtra("username") ?: return@rememberLauncherForActivityResult
                        val p = res.data?.getStringExtra("password") ?: return@rememberLauncherForActivityResult
                        busy = true; error = ""
                        scope.launch {
                            val result = Api.login(u, p)
                            busy = false
                            result.onSuccess { onLogin(it) }
                                .onFailure { e -> error = e.message ?: "通行证登录失败" }
                        }
                    }
                }
                TextButton(onClick = {
                    val intent = android.content.Intent("com.lemonchat.adminpassid.CONFIRM")
                        .setPackage("com.lemonchat.adminpassid")
                    try {
                        passLauncher.launch(intent)
                    } catch (_: Exception) {
                        Toast.makeText(ctx, "未安装 Lemon 通行证APP", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("🔑 通行证登录", color = LemonColors.Green, fontSize = 13.sp)
                }
                TextButton(onClick = { showForgot = true }) {
                    Text("忘记密码？", color = LemonColors.TextSub, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { isRegister = !isRegister; error = "" }) {
                Text(
                    if (isRegister) "已有账号？去登录" else "没有账号？去注册",
                    color = LemonColors.Green, fontSize = 14.sp
                )
            }
            if (showForgot) {
                ForgotPasswordDialog(onDismiss = { showForgot = false })
            }
        }
    }
}

// ── 蓝V官方徽章 ──
@Composable
fun OfficialBadge(size: Int = 14) {
    Icon(
        Icons.Filled.Verified, "官方认证",
        tint = Color(0xFF1D9BF0),
        modifier = Modifier.size(size.dp)
    )
}

@Composable
fun OfficialName(name: String, fontSize: Int = 15, badge: Int = 14, fontWeight: androidx.compose.ui.text.font.FontWeight = FontWeight.SemiBold) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = fontSize.sp, fontWeight = fontWeight, color = LemonColors.TextMain)
        Spacer(Modifier.width(4.dp))
        OfficialBadge(badge)
    }
}

@Composable
fun LemonField(hint: String, value: String, isPassword: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, fontSize = 14.sp, color = LemonColors.TextSub) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        shape = LemonShapes.input,
        singleLine = true,
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LemonColors.Green,
            unfocusedBorderColor = Color(0xFFDDE5E0),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

// ══════════════ 主界面 ══════════════
@Composable
fun MainScreen(me: Api.User, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var chattingWith by remember { mutableStateOf<Api.User?>(null) }
    var inGroupChat by remember { mutableStateOf<Api.Group?>(null) }
    var inAdmin by remember { mutableStateOf(false) }
    val ctxForKick = LocalContext.current
    LaunchedEffect(Unit) {
        ChatSocket.forceLogout.collect {
            Toast.makeText(ctxForKick, "您的账号已被管理员强制下线", Toast.LENGTH_LONG).show()
            onLogout()
        }
    }
    val tabs = listOf(
        TabItem(Icons.Filled.ChatBubble, "消息"),
        TabItem(Icons.Filled.PeopleAlt, "通讯录"),
        TabItem(Icons.Filled.Groups, "团队"),
        TabItem(Icons.Filled.Person, "我的")
    )

    Scaffold { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(LemonColors.Bg)
        ) {
            AnimatedContent(
                if (chattingWith != null) -2 else if (inGroupChat != null) -3 else tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { dir * it / 4 } + fadeIn(tween(250)))
                        .togetherWith(slideOutHorizontally { -dir * it / 4 } + fadeOut(tween(250)))
                }
            ) { t ->
                when {
                    inAdmin -> AdminPage(me = me, onBack = { inAdmin = false })
                    t == -2 && chattingWith != null -> ChatScreen(me = me, peer = chattingWith!!, onBack = { chattingWith = null })
                    t == -3 && inGroupChat != null -> GroupChatScreen(me = me, group = inGroupChat!!, onBack = { inGroupChat = null })
                    t == 0 -> MessagesPage(me = me, onOpenChat = { chattingWith = it })
                    t == 1 -> ContactsPage(me = me, onOpenChat = { chattingWith = it })
                    t == 2 -> TeamsPage(me = me, onOpenGroup = { inGroupChat = it })
                    else -> MePage(me = me, onLogout = onLogout, onOpenAdmin = { inAdmin = true })
                }
            }

            if (chattingWith == null && inGroupChat == null && !inAdmin) {
                FloatingTabBar(
                    tabs = tabs,
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 26.dp)
                )
            }
        }
    }
}

// ══════════════ 消息页：会话列表 ══════════════
@Composable
fun MessagesPage(me: Api.User, onOpenChat: (Api.User) -> Unit) {
    var conversations by remember { mutableStateOf<List<Api.Conversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun load() {
        loading = true
        kotlinx.coroutines.MainScope().launch {
            Api.conversations(me.id).onSuccess { conversations = it }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    // 实时门铃：收到新消息立刻刷新会话列表
    LaunchedEffect(Unit) {
        ChatSocket.events.collect { load() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, top = 18.dp, bottom = 10.dp, end = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("消息", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
            IconButton(onClick = { load() }) {
                Icon(Icons.Filled.Refresh, "刷新", tint = LemonColors.TextSub)
            }
        }
        if (loading && conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LemonColors.Green)
            }
        } else if (conversations.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = LemonColors.Green, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(14.dp))
                Text("还没有聊天", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
                Text("去「通讯录」加个好友吧 🍋", fontSize = 13.sp, color = LemonColors.TextSub)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(conversations) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(Api.User(c.peerId, "", c.nickname)) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(LemonShapes.pill).background(LemonColors.GreenLight),
                            contentAlignment = Alignment.Center
                        ) { Text(c.nickname.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LemonColors.Green) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (c.isOfficial) OfficialName(c.nickname) else Text(c.nickname, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LemonColors.TextMain)
                            Text(
                                (if (c.lastFromMe) "我：" else "") + c.lastMsg,
                                fontSize = 13.sp, color = LemonColors.TextSub, maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ══════════════ 聊天窗口 ══════════════
// ══════════════ 单聊窗口（新版） ══════════════
@Composable
fun ChatScreen(me: Api.User, peer: Api.User, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<Api.Message>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var myAvatar by remember { mutableStateOf("") }
    // 发送失败列表：(错误码, 内容)
    var errors by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showRequest by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { myAvatar = Api.AvatarStore.get(me.id) }
    LaunchedEffect(peer.id) {
        while (true) { delay(15000); if (!ChatSocket.online) tick++ }
    }
    LaunchedEffect(peer.id) {
        ChatSocket.start(me.id)
        ChatSocket.events.collect { (from, to) -> if (from == peer.id || to == peer.id) tick++ }
    }
    LaunchedEffect(peer.id, tick) {
        Api.messages(me.id, peer.id).onSuccess { msgs ->
            messages = msgs
            loaded = true
            if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
        }.onFailure { loaded = true }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LemonColors.GreenLight, LemonColors.Bg)))
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBackIosNew, "返回", tint = LemonColors.TextMain, modifier = Modifier.size(20.dp))
            }
            if (peer.isOfficial) OfficialName(peer.nickname, 18, 16, FontWeight.Bold)
            else Text(peer.nickname, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 14.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!loaded) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LemonColors.Green)
                    }
                }
            } else if (messages.isEmpty() && errors.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text("打个招呼吧 👋", color = LemonColors.TextSub, fontSize = 14.sp)
                    }
                }
            }
            items(messages) { m ->
                val mine = m.fromMe
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        if (!mine) {
                            AvatarImg(m.senderId, m.nickname, m.avatar, 34, m.isOfficial)
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(
                            Modifier
                                .widthIn(max = 250.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp, topEnd = 18.dp,
                                        bottomStart = if (mine) 18.dp else 4.dp,
                                        bottomEnd = if (mine) 4.dp else 18.dp
                                    )
                                )
                                .background(if (mine) LemonColors.Green else Color.White)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) { MsgContent(m.content, mine) }
                        if (mine) {
                            Spacer(Modifier.width(8.dp))
                            AvatarImg(me.id, me.nickname, myAvatar, 34, me.isOfficial)
                        }
                    }
                    if (!mine && !peer.isOfficial) {
                        Text(m.nickname, fontSize = 10.sp, color = LemonColors.TextSub, modifier = Modifier.padding(start = 42.dp, top = 1.dp))
                    }
                }
            }
            items(errors.size) { i ->
                val (code, content) = errors[i]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (code == "BLOCKED") "消息已发出，但被对方拒收了" else "您还不是对方的好友",
                            fontSize = 10.sp, color = Color(0xFFD64545))
                        if (code != "BLOCKED") {
                            Text("添加好友",
                                fontSize = 10.sp, color = LemonColors.Green,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                modifier = Modifier.clickable { showRequest = true })
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Error, "发送失败", tint = Color(0xFFD64545), modifier = Modifier.size(16.dp))
                }
            }
            item { Spacer(Modifier.height(6.dp)) }
        }

        ChatInputRow(
            sending = sending,
            onSendText = { text ->
                sending = true
                scope.launch {
                    Api.send(me.id, peer.id, text).onSuccess {
                        errors = emptyList()
                        Api.messages(me.id, peer.id).onSuccess { msgs ->
                            messages = msgs
                            if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
                        }
                    }.onFailure { e ->
                        errors = errors + ((e as? Api.ApiError)?.code ?: "" to text)
                    }
                    sending = false
                }
            },
            onSendImage = { data ->
                sending = true
                scope.launch {
                    Api.send(me.id, peer.id, data).onSuccess {
                        Api.messages(me.id, peer.id).onSuccess { msgs ->
                            messages = msgs
                            if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
                        }
                    }.onFailure { e ->
                        errors = errors + ((e as? Api.ApiError)?.code ?: "" to data)
                    }
                    sending = false
                }
            }
        )
    }

    if (showRequest) {
        FriendRequestDialog(me, peer.username, onDismiss = { showRequest = false })
    }
}

// ══════════════ 通讯录：好友列表 + 搜索加好友 ══════════════
@Composable
fun ContactsPage(me: Api.User, onOpenChat: (Api.User) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<Api.User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var viewProfile by remember { mutableStateOf<Api.User?>(null) }
    var showRequests by remember { mutableStateOf(false) }
    var reqCount by remember { mutableStateOf(0) }
    var selectedFriend by remember { mutableStateOf<Api.User?>(null) }
    var requestTarget by remember { mutableStateOf<Api.User?>(null) }

    fun load() {
        loading = true
        kotlinx.coroutines.MainScope().launch {
            Api.friends(me.id).onSuccess { friends = it }
            Api.friendRequests(me.id).onSuccess { reqCount = it.size }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, top = 18.dp, bottom = 10.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("通讯录", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
            TextButton(onClick = { showSearch = true }) {
                Icon(Icons.Filled.PersonAdd, null, tint = LemonColors.Green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("加好友", color = LemonColors.Green, fontSize = 14.sp)
            }
        }

        if (loading && friends.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LemonColors.Green)
            }
        } else if (friends.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.PeopleAlt, null, tint = LemonColors.Green, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(14.dp))
                Text("还没有好友", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
                Text("点右上角「加好友」，搜对方用户名", fontSize = 13.sp, color = LemonColors.TextSub)
            }
        } else {
            LazyColumn {
                // 申请消息入口（微信式）
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
                            .clip(LemonShapes.card).background(Color(0xFFEAF6FF))
                            .clickable { showRequests = true }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MarkEmailUnread, null, tint = Color(0xFF1D9BF0), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("申请消息", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D9BF0))
                        Spacer(Modifier.weight(1f))
                        if (reqCount > 0) {
                            Box(
                                Modifier.clip(CircleShape).background(Color(0xFFD64545)).padding(horizontal = 7.dp, vertical = 2.dp)
                            ) { Text("$reqCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = LemonColors.TextSub)
                    }
                }
                items(friends) { f ->
                    Row(
                        Modifier.fillMaxWidth()
                            .pointerInput(f.id) {
                                detectTapGestures(
                                    onTap = { viewProfile = f },
                                    onLongPress = { selectedFriend = f }
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarImg(f.id, f.nickname, f.avatar, 46, f.isOfficial)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            if (f.isOfficial) OfficialName(f.nickname) else Text(f.nickname, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LemonColors.TextMain)
                            Text("ID: ${f.username}", fontSize = 12.sp, color = LemonColors.TextSub)
                        }
                    }
                }
            }
        }
    }

    // 好友资料卡
    viewProfile?.let { p ->
        AlertDialog(
            onDismissRequest = { viewProfile = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(LemonShapes.pill).background(LemonColors.GreenLight),
                        contentAlignment = Alignment.Center) {
                        Text(p.nickname.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LemonColors.Green)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        if (p.isOfficial) OfficialName(p.nickname, 17) else Text(p.nickname, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("柠檬号 ${100000 + p.id}", fontSize = 11.sp, color = LemonColors.TextSub)
                    }
                }
            },
            text = {
                Column {
                    if (p.isOfficial) {
                        Text("LemonX_Studio工作室创始人、LemonChat官方、LemonChat开发者",
                            fontSize = 12.sp, color = Color(0xFF1D9BF0), fontWeight = FontWeight.SemiBold)
                    } else if (p.bio.isNotEmpty()) {
                        Text(p.bio, fontSize = 13.sp, color = LemonColors.TextSub)
                    } else {
                        Text("这个人很神秘，什么都没写", fontSize = 13.sp, color = LemonColors.TextSub)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewProfile = null; onOpenChat(p) },
                    shape = LemonShapes.pill,
                    colors = ButtonDefaults.buttonColors(containerColor = LemonColors.Green)) {
                    Text("发消息", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewProfile = null }) { Text("关闭", color = LemonColors.TextSub) }
            }
        )
    }

    if (showSearch) {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Api.User>>(emptyList()) }
        var searched by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("搜索加好友", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("输入对方用户名") },
                        shape = LemonShapes.input,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LemonColors.Green)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                Api.searchUsers(me.id, query.trim()).onSuccess { results = it; searched = true }
                            }
                        },
                        shape = LemonShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = LemonColors.Green),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("搜索") }
                    Spacer(Modifier.height(8.dp))
                    if (searched && results.isEmpty()) Text("没找到这个人 🤔", fontSize = 13.sp, color = LemonColors.TextSub)
                    results.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(38.dp).clip(LemonShapes.pill).background(LemonColors.GreenLight),
                                contentAlignment = Alignment.Center
                            ) { Text(u.nickname.take(1), fontSize = 16.sp, color = LemonColors.Green) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                if (u.isOfficial) OfficialName(u.nickname, 14) else Text(u.nickname, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LemonColors.TextMain)
                                Text(u.username, fontSize = 11.sp, color = LemonColors.TextSub)
                            }
                            TextButton(onClick = { requestTarget = u }) { Text("添加", color = LemonColors.Green, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSearch = false }) { Text("关闭", color = LemonColors.TextSub) }
            }
        )
    }

    // 好友申请列表
    if (showRequests) {
        var requests by remember { mutableStateOf<List<Api.FriendRequest>?>(null) }
        LaunchedEffect(Unit) {
            Api.friendRequests(me.id).onSuccess { requests = it; reqCount = it.size }
        }
        AlertDialog(
            onDismissRequest = { showRequests = false },
            title = { Text("申请消息", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (requests == null) {
                        CircularProgressIndicator(color = LemonColors.Green, modifier = Modifier.size(28.dp))
                    } else if (requests!!.isEmpty()) {
                        Text("暂无好友申请", fontSize = 14.sp, color = LemonColors.TextSub)
                    } else {
                        requests!!.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    if (r.isOfficial) OfficialName(r.nickname, 14)
                                    else Text(r.nickname, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LemonColors.TextMain)
                                    Text(r.message.ifEmpty { "请求加你为好友" }, fontSize = 12.sp, color = LemonColors.TextSub)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        Api.acceptFriendRequest(me.id, r.id).onSuccess {
                                            Toast.makeText(ctx, "已通过 ${r.nickname} 的申请", Toast.LENGTH_SHORT).show()
                                            load(); showRequests = false
                                        }.onFailure { e -> Toast.makeText(ctx, e.message ?: "失败", Toast.LENGTH_SHORT).show() }
                                    }
                                }) { Text("通过", color = LemonColors.Green, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = {
                                    scope.launch {
                                        Api.rejectFriendRequest(me.id, r.id).onSuccess { load() }
                                    }
                                }) { Text("拒绝", color = LemonColors.TextSub) }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showRequests = false }) { Text("关闭", color = LemonColors.TextSub) } }
        )
    }

    // 长按好友：删除 / 拉黑
    if (selectedFriend != null) {
        val f = selectedFriend!!
        AlertDialog(
            onDismissRequest = { selectedFriend = null },
            title = { Text(f.nickname, fontWeight = FontWeight.Bold) },
            text = { Text("选择要执行的操作", fontSize = 13.sp, color = LemonColors.TextSub) },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        scope.launch {
                            Api.deleteFriend(me.id, f.id).onSuccess {
                                Toast.makeText(ctx, "已删除好友", Toast.LENGTH_SHORT).show()
                                selectedFriend = null; load()
                            }.onFailure { e -> Toast.makeText(ctx, e.message ?: "失败", Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text("删除好友", color = Color(0xFFD64545)) }
                    TextButton(onClick = {
                        scope.launch {
                            Api.blockUser(me.id, f.id).onSuccess {
                                Toast.makeText(ctx, "已加入黑名单", Toast.LENGTH_SHORT).show()
                                selectedFriend = null; load()
                            }.onFailure { e -> Toast.makeText(ctx, e.message ?: "失败", Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text("加入黑名单", color = Color(0xFFD64545)) }
                    TextButton(onClick = { selectedFriend = null }) { Text("取消", color = LemonColors.TextSub) }
                }
            }
        )
    }

    // 发送好友申请
    if (requestTarget != null) {
        FriendRequestDialog(me, requestTarget!!.username, onDismiss = { requestTarget = null; load() })
    }
}

// ══════════════ 我的 ══════════════
@Composable
fun MePage(me: Api.User, onLogout: () -> Unit, onOpenAdmin: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        var myAvatar by remember { mutableStateOf(me.avatar) }
        LaunchedEffect(Unit) { myAvatar = Api.AvatarStore.get(me.id) }
        val avatarPicker = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri -> uri?.let { u ->
            val data = compressImage(ctx, u)
            if (data != null) {
                scope.launch {
                    Api.setAvatar(me.id, data).onSuccess {
                        myAvatar = data
                        Toast.makeText(ctx, "头像已更新", Toast.LENGTH_SHORT).show()
                    }.onFailure { e -> Toast.makeText(ctx, e.message ?: "设置失败", Toast.LENGTH_SHORT).show() }
                }
            } else Toast.makeText(ctx, "图片读取失败", Toast.LENGTH_SHORT).show()
        } }
        Box(
            Modifier.size(84.dp).clip(LemonShapes.pill).clickable {
                avatarPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.background(LemonColors.GreenLight),
            contentAlignment = Alignment.Center
        ) {
            AvatarImg(me.id, me.nickname, myAvatar, 84, me.isOfficial)
        }
        Text("点头像可更换", fontSize = 10.sp, color = LemonColors.TextSub)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(me.nickname, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = LemonColors.TextMain)
            if (me.isOfficial) { Spacer(Modifier.width(6.dp)); OfficialBadge(20) }
        }
        Text("账号：${me.username} · ID ${me.id}", fontSize = 13.sp, color = LemonColors.TextSub)
        Spacer(Modifier.height(6.dp))
        Text("柠檬号：${100000 + me.id}", fontSize = 13.sp, color = LemonColors.TextSub)
        Spacer(Modifier.height(10.dp))
        if (me.isOfficial) {
            // 官方专属认证简介卡
            Row(
                Modifier.fillMaxWidth().clip(LemonShapes.card).background(Color(0xFFEAF6FF)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OfficialBadge(18)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("LemonX_Studio工作室创始人、LemonChat官方、LemonChat开发者", fontSize = 12.sp, color = Color(0xFF1D9BF0), fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (me.bio.isNotEmpty()) {
            Text(me.bio, fontSize = 13.sp, color = LemonColors.TextSub, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))

        // 设置卡片
        var showAbout by remember { mutableStateOf(false) }
        var showSecInfo by remember { mutableStateOf(false) }
        var showBlacklist by remember { mutableStateOf(false) }
        val prefs = remember { ctx.getSharedPreferences("lemon_settings", android.content.Context.MODE_PRIVATE) }
        var notifyOn by remember { mutableStateOf(prefs.getBoolean("notify_enabled", true)) }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(LemonShapes.card)
                .background(Color.White).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Notifications, null, tint = LemonColors.Green, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("消息通知", fontSize = 15.sp, color = LemonColors.TextMain, modifier = Modifier.weight(1f))
            Switch(
                checked = notifyOn,
                onCheckedChange = {
                    notifyOn = it
                    prefs.edit().putBoolean("notify_enabled", it).apply()
                }
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(LemonShapes.card)
                .background(Color.White).clickable { showSecInfo = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Lock, null, tint = LemonColors.Green, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("账号与密保", fontSize = 15.sp, color = LemonColors.TextMain)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(LemonShapes.card)
                .background(Color.White).clickable { showAbout = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Info, null, tint = LemonColors.Green, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            val vName = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "?" } }
            Text("关于 Lemon Chat（v$vName）", fontSize = 15.sp, color = LemonColors.TextMain)
        }

        if (showSecInfo) {
            AlertDialog(
                onDismissRequest = { showSecInfo = false },
                confirmButton = { TextButton(onClick = { showSecInfo = false }) { Text("知道了") } },
                title = { Text("账号与密保", fontWeight = FontWeight.Bold) },
                text = { Text("· 忘记密码：退出后在登录页点「忘记密码」，答对密保问题即可重置\n· 账号注销：本页最下方「注销账号」\n· 密保问题注册时设置，暂时无法修改") },
            )
        }
        if (showAbout) {
            val vName2 = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "?" } }
            AlertDialog(
                onDismissRequest = { showAbout = false },
                confirmButton = { TextButton(onClick = { showAbout = false }) { Text("好的") } },
                title = { Text("Lemon Chat", fontWeight = FontWeight.Bold) },
                text = { Text("🍋 当前版本 v$vName2\n后端：Cloudflare Workers\n消息实时推送 · 单聊 · 群聊 · 好友 · 管理后台\n\n由 LemonX_Studio 出品") },
            )
        }
        // 黑名单列表
        if (showBlacklist) {
            var blocked by remember { mutableStateOf<List<Api.User>?>(null) }
            LaunchedEffect(Unit) { Api.listBlocked(me.id).onSuccess { blocked = it } }
            AlertDialog(
                onDismissRequest = { showBlacklist = false },
                title = { Text("黑名单", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (blocked == null) CircularProgressIndicator(color = LemonColors.Green, modifier = Modifier.size(28.dp))
                        else if (blocked!!.isEmpty()) Text("黑名单是空的", fontSize = 14.sp, color = LemonColors.TextSub)
                        else blocked!!.forEach { u ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(u.nickname, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = LemonColors.TextMain)
                                TextButton(onClick = {
                                    scope.launch {
                                        Api.unblockUser(me.id, u.id).onSuccess {
                                            Toast.makeText(ctx, "已解除拉黑", Toast.LENGTH_SHORT).show()
                                            Api.listBlocked(me.id).onSuccess { blocked = it }
                                        }
                                    }
                                }) { Text("解除", color = LemonColors.Green) }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showBlacklist = false }) { Text("关闭", color = LemonColors.TextSub) } }
            )
        }
        // 黑名单
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(LemonShapes.card)
                .background(Color.White).clickable { showBlacklist = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Block, null, tint = Color(0xFFD64545), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("黑名单", fontSize = 15.sp, color = LemonColors.TextMain)
        }

        // 官方号专属：管理后台入口
        if (me.isOfficial) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(LemonShapes.card)
                    .background(Color(0xFFEAF6FF))
                    .clickable { onOpenAdmin() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AdminPanelSettings, null, tint = Color(0xFF1D9BF0), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("管理后台（官方专属）", fontSize = 15.sp, color = Color(0xFF1D9BF0), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        // 退出登录（白）
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = LemonShapes.pill,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) { Text("退出登录", color = LemonColors.TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(10.dp))
        // 注销账号（红）——官方账号不允许注销自己
        if (!me.isOfficial) Button(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = LemonShapes.pill,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x14D64545))
        ) { Text("注销账号", color = Color(0xFFD64545), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        val appVer = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "?" } }
        Text("Lemon Chat v$appVer", fontSize = 11.sp, color = LemonColors.TextSub)
        Spacer(Modifier.height(80.dp))
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("注销账号", fontWeight = FontWeight.Bold) },
            text = { Text("注销后你的账号、好友、所有聊天记录将被永久删除，无法恢复！确定要注销吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        Api.deleteAccount(me.id).onSuccess { onLogout() }
                            .onFailure { e -> Toast.makeText(ctx, e.message ?: "注销失败", Toast.LENGTH_SHORT).show() }
                    }
                }) { Text("确定注销", color = Color(0xFFD64545), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("我再想想", color = LemonColors.TextSub) }
            }
        )
    }
}

// ══════════════ 管理后台（官方号专属） ══════════════
@Composable
fun AdminPage(me: Api.User, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<Api.AdminStats?>(null) }
    var error by remember { mutableStateOf("") }
    var resetResult by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Api.adminStats(me.id).onSuccess { stats = it }.onFailure { error = it.message ?: "加载失败" }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBackIosNew, "返回", tint = LemonColors.TextMain, modifier = Modifier.size(20.dp))
            }
            Text("管理后台", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
        }
        if (error.isNotEmpty()) {
            Text("⚠️ $error", color = Color(0xFFD64545), modifier = Modifier.padding(20.dp))
        } else if (stats == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LemonColors.Green)
            }
        } else {
            val st = stats!!
            // 统计卡
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                StatCard("注册总数", "${st.total}", Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                StatCard("当前在线", "${st.online}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                items(st.users) { u ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .clip(LemonShapes.card).background(Color.White).padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(LemonShapes.pill)
                                .background(if (u.online) Color(0xFF34C759) else Color(0xFFC7C7CC)))
                            Spacer(Modifier.width(8.dp))
                            if (u.isOfficial) OfficialName(u.nickname, 15)
                            else Text(u.nickname, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LemonColors.TextMain)
                            Spacer(Modifier.weight(1f))
                            Text(if (u.online) "在线" else "离线", fontSize = 11.sp,
                                color = if (u.online) Color(0xFF34C759) else LemonColors.TextSub)
                        }
                        Spacer(Modifier.height(6.dp))
                        InfoLine("ID", "${u.id} · ${u.username}")
                        InfoLine("密码(加密)", u.passHash.take(24) + "…")
                        InfoLine("注册时间", u.createdAt.take(19).replace("T", " "))
                        InfoLine("注册IP", u.regIp)
                        InfoLine("设备", u.regUa)
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    Api.adminResetPassword(me.id, u.id).onSuccess { temp ->
                                        resetResult = "${u.nickname} 的新密码：$temp（只显示这一次，发给TA后让TA登录改密码）"
                                    }.onFailure { e -> resetResult = "重置失败：${e.message}" }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1D9BF0))
                        ) { Text("重置密码", fontSize = 12.sp) }
                        if (!u.isOfficial) Row {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        Api.adminKick(me.id, u.id).onSuccess {
                                            Toast.makeText(ctx, "已强制下线 ${u.nickname}", Toast.LENGTH_SHORT).show()
                                            Api.adminStats(me.id).onSuccess { stats = it }
                                        }.onFailure { e -> Toast.makeText(ctx, e.message ?: "失败", Toast.LENGTH_SHORT).show() }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD64545))
                            ) { Text("下线", fontSize = 12.sp) }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        Api.adminBan(me.id, u.id, 7).onSuccess {
                                            Toast.makeText(ctx, "已封禁 ${u.nickname} 7 天", Toast.LENGTH_SHORT).show()
                                            Api.adminStats(me.id).onSuccess { stats = it }
                                        }.onFailure { e -> Toast.makeText(ctx, e.message ?: "失败", Toast.LENGTH_SHORT).show() }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD64545))
                            ) { Text("封禁7天", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
    resetResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { resetResult = null },
            title = { Text("重置成功", fontWeight = FontWeight.Bold) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { resetResult = null }) { Text("知道了", color = LemonColors.Green) } }
        )
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(LemonShapes.card).background(Color.White).padding(16.dp)
    ) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LemonColors.Green)
        Text(label, fontSize = 12.sp, color = LemonColors.TextSub)
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontSize = 11.sp, color = LemonColors.TextSub, modifier = Modifier.width(70.dp))
        Text(value, fontSize = 11.sp, color = LemonColors.TextMain, maxLines = 1)
    }
}

@Composable
fun PlaceholderPage(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = LemonColors.Green, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 13.sp, color = LemonColors.TextSub, textAlign = TextAlign.Center)
    }
}

// ══════════════ 忘记密码（密保重置） ══════════════
@Composable
fun ForgotPasswordDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) } // 0输用户名 1答密保 2成功
    var username by remember { mutableStateOf("") }
    var secQ by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("找回密码", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                when (step) {
                    0 -> {
                        OutlinedTextField(value = username, onValueChange = { username = it },
                            placeholder = { Text("输入你的用户名") }, singleLine = true,
                            shape = LemonShapes.input,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LemonColors.Green))
                        if (error.isNotEmpty()) Text("⚠️ $error", color = Color(0xFFD64545), fontSize = 12.sp)
                    }
                    1 -> {
                        Text("密保问题：$secQ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = answer, onValueChange = { answer = it },
                            placeholder = { Text("你的密保答案") }, singleLine = true,
                            shape = LemonShapes.input,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LemonColors.Green))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = newPw, onValueChange = { newPw = it },
                            placeholder = { Text("设置新密码（至少6位）") }, singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = LemonShapes.input,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LemonColors.Green))
                        if (error.isNotEmpty()) Text("⚠️ $error", color = Color(0xFFD64545), fontSize = 12.sp)
                    }
                    2 -> Text("✅ 密码已重置！用新密码登录吧", color = Color(0xFF34A853), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 2) { onDismiss(); return@Button }
                    scope.launch {
                        if (step == 0) {
                            Api.forgotQuestion(username.trim()).onSuccess { secQ = it; error = ""; step = 1 }
                                .onFailure { e -> error = e.message ?: "出错了" }
                        } else {
                            Api.resetBySecAnswer(username.trim(), answer, newPw).onSuccess { error = ""; step = 2 }
                                .onFailure { e -> error = e.message ?: "重置失败" }
                        }
                    }
                },
                shape = LemonShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = LemonColors.Green)
            ) { Text(if (step == 0) "下一步" else if (step == 1) "重置密码" else "完成", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = LemonColors.TextSub) }
        }
    )
}

// ══════════════ 团队页：群列表 ══════════════
@Composable
fun TeamsPage(me: Api.User, onOpenGroup: (Api.Group) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<Api.Group>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<Api.Group?>(null) }

    fun load() {
        kotlinx.coroutines.MainScope().launch {
            Api.groups(me.id).onSuccess { groups = it }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(Unit) {
        ChatSocket.events.collect { (_, to) -> if (to < 0) load() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, top = 18.dp, bottom = 10.dp, end = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("团队", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
            Row {
                IconButton(onClick = { showJoin = true }) {
                    Icon(Icons.Filled.GroupAdd, "加群", tint = LemonColors.Green)
                }
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.AddCircle, "建群", tint = LemonColors.Green)
                }
            }
        }
        if (loading && groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LemonColors.Green)
            }
        } else if (groups.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.Groups, null, tint = LemonColors.Green, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(14.dp))
                Text("还没有群", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
                Text("右上角建个群，或输入群号加入 🍋", fontSize = 13.sp, color = LemonColors.TextSub)
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 18.dp)) {
                items(groups.size) { i ->
                    val g = groups[i]
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(LemonShapes.card)
                            .background(Color.White)
                            .pointerInput(g.id) {
                                detectTapGestures(
                                    onTap = { onOpenGroup(g) },
                                    onLongPress = { selectedGroup = g }
                                )
                            }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(LemonShapes.pill).background(LemonColors.GreenLight),
                            contentAlignment = Alignment.Center
                        ) { Text(g.name.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LemonColors.Green) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LemonColors.TextMain)
                            Text("群号 ${g.id} · ${g.members} 人", fontSize = 12.sp, color = LemonColors.TextSub)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = LemonColors.TextSub)
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (showCreate) {
        var groupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            confirmButton = {
                TextButton(onClick = {
                    val n = groupName.trim()
                    if (n.isNotEmpty()) {
                        scope.launch {
                            Api.createGroup(me.id, n).onSuccess { g ->
                                showCreate = false; onOpenGroup(g)
                            }.onFailure { e -> Toast.makeText(ctx, e.message ?: "建群失败", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }) { Text("创建", color = LemonColors.Green) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消", color = LemonColors.TextSub) } },
            title = { Text("建个新群", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = groupName, onValueChange = { groupName = it },
                    label = { Text("群名称") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LemonColors.Green)
                )
            }
        )
    }
    if (showJoin) {
        var joinId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoin = false },
            confirmButton = {
                TextButton(onClick = {
                    val id = joinId.trim().toIntOrNull()
                    if (id != null) {
                        scope.launch {
                            Api.joinGroup(me.id, id).onSuccess {
                                showJoin = false; load()
                            }.onFailure { e -> Toast.makeText(ctx, e.message ?: "加群失败", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }) { Text("加入", color = LemonColors.Green) }
            },
            dismissButton = { TextButton(onClick = { showJoin = false }) { Text("取消", color = LemonColors.TextSub) } },
            title = { Text("输入群号加群", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = joinId, onValueChange = { joinId = it },
                    label = { Text("群号（纯数字）") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LemonColors.Green)
                )
            }
        )
    }

    // 长按群：进入 / 退出 / 解散（群主）
    if (selectedGroup != null) {
        val g = selectedGroup!!
        val isOwner = g.ownerId == me.id
        AlertDialog(
            onDismissRequest = { selectedGroup = null },
            title = { Text(g.name, fontWeight = FontWeight.Bold) },
            text = { Text("群号 ${g.id} · ${g.members} 人\n长按群可管理", fontSize = 13.sp, color = LemonColors.TextSub) },
            confirmButton = {
                Column {
                    TextButton(onClick = { selectedGroup = null; onOpenGroup(g) }) { Text("进入群聊", color = LemonColors.Green) }
                    TextButton(onClick = {
                        if (isOwner) Toast.makeText(ctx, "群主不能退出，请解散群聊", Toast.LENGTH_SHORT).show()
                        else scope.launch {
                            Api.groupLeave(me.id, g.id).onSuccess {
                                Toast.makeText(ctx, "已退出群聊", Toast.LENGTH_SHORT).show()
                                selectedGroup = null; load()
                            }.onFailure { e -> Toast.makeText(ctx, e.message ?: "失败", Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text("退出群聊", color = LemonColors.TextMain) }
                    if (isOwner) {
                        TextButton(onClick = {
                            scope.launch {
                                Api.groupDissolve(me.id, g.id).onSuccess {
                                    Toast.makeText(ctx, "群已解散", Toast.LENGTH_SHORT).show()
                                    selectedGroup = null; load()
                                }.onFailure { e -> Toast.makeText(ctx, e.message ?: "失败", Toast.LENGTH_SHORT).show() }
                            }
                        }) { Text("解散群聊", color = Color(0xFFD64545)) }
                    }
                    TextButton(onClick = { selectedGroup = null }) { Text("取消", color = LemonColors.TextSub) }
                }
            }
        )
    }
}

// ══════════════ 群聊窗口 ══════════════
// ══════════════ 群聊窗口（新版） ══════════════
@Composable
fun GroupChatScreen(me: Api.User, group: Api.Group, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<Api.GroupMessage>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var myAvatar by remember { mutableStateOf("") }
    var showInvite by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { myAvatar = Api.AvatarStore.get(me.id) }
    LaunchedEffect(group.id) {
        while (true) { delay(15000); if (!ChatSocket.online) tick++ }
    }
    LaunchedEffect(group.id) {
        ChatSocket.start(me.id)
        ChatSocket.events.collect { (_, to) -> if (to == -group.id) tick++ }
    }
    LaunchedEffect(group.id, tick) {
        Api.groupMessages(group.id).onSuccess { msgs ->
            messages = msgs
            loaded = true
            if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
        }.onFailure { loaded = true }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LemonColors.GreenLight, LemonColors.Bg)))
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 14.dp, bottom = 8.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBackIosNew, "返回", tint = LemonColors.TextMain, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(group.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LemonColors.TextMain)
                Text("群号 ${group.id} · ${group.members} 人", fontSize = 11.sp, color = LemonColors.TextSub)
            }
            IconButton(onClick = { showInvite = true }) {
                Icon(Icons.Filled.GroupAdd, "邀请成员", tint = LemonColors.Green)
            }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 14.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!loaded) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LemonColors.Green)
                    }
                }
            } else if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text("聊聊群规，打打招呼 🍋", color = LemonColors.TextSub, fontSize = 14.sp)
                    }
                }
            }
            items(messages.size) { i ->
                val m = messages[i]
                val mine = m.senderId == me.id
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        if (!mine) {
                            AvatarImg(m.senderId, m.nickname, m.avatar, 34, m.isOfficial)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            if (!mine) Text(m.nickname, fontSize = 10.sp, color = LemonColors.TextSub, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                            Box(
                                Modifier
                                    .widthIn(max = 250.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 18.dp, topEnd = 18.dp,
                                            bottomStart = if (mine) 18.dp else 4.dp,
                                            bottomEnd = if (mine) 4.dp else 18.dp
                                        )
                                    )
                                    .background(if (mine) LemonColors.Green else Color.White)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) { MsgContent(m.content, mine) }
                        }
                        if (mine) {
                            Spacer(Modifier.width(8.dp))
                            AvatarImg(me.id, me.nickname, myAvatar, 34, me.isOfficial)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        ChatInputRow(
            sending = sending,
            onSendText = { text ->
                sending = true
                scope.launch {
                    Api.sendGroup(group.id, me.id, text).onSuccess {
                        Api.groupMessages(group.id).onSuccess { msgs ->
                            messages = msgs
                            if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
                        }
                    }.onFailure { e -> }
                    sending = false
                }
            },
            onSendImage = { data ->
                sending = true
                scope.launch {
                    Api.sendGroup(group.id, me.id, data).onSuccess {
                        Api.groupMessages(group.id).onSuccess { msgs ->
                            messages = msgs
                            if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
                        }
                    }.onFailure { e -> }
                    sending = false
                }
            }
        )
    }

    if (showInvite) {
        val ctx = LocalContext.current
        var uname by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text("邀请成员", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = uname, onValueChange = { uname = it },
                    label = { Text("对方用户名") },
                    shape = LemonShapes.input, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LemonColors.Green)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = uname.trim()
                    if (u.isNotEmpty()) {
                        scope.launch {
                            Api.groupInvite(me.id, group.id, u).onSuccess {
                                Toast.makeText(ctx, "已邀请 $u", Toast.LENGTH_SHORT).show()
                                showInvite = false
                            }.onFailure { e -> Toast.makeText(ctx, e.message ?: "邀请失败", Toast.LENGTH_LONG).show() }
                        }
                    }
                }) { Text("邀请", color = LemonColors.Green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showInvite = false }) { Text("取消", color = LemonColors.TextSub) } }
        )
    }
}
// ══════════════ 共享组件 ══════════════

// 头像：有图显示图，没图显示首字圆块
@Composable
fun AvatarImg(userId: Int, fallback: String, avatar: String, size: Int, official: Boolean = false) {
    val bmp = remember(avatar) {
        if (avatar.startsWith("data:image/")) {
            runCatching {
                val bytes = android.util.Base64.decode(avatar.substringAfter("base64,"), android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else null
    }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(LemonColors.GreenLight),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(fallback.take(1), fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Bold, color = LemonColors.Green)
        }
        if (official) OfficialBadge((size * 0.42f).toInt())
    }
}

// 消息内容：文本 或 base64 图片
@Composable
fun MsgContent(content: String, mine: Boolean) {
    if (content.startsWith("data:image/")) {
        val bmp = remember(content) {
            runCatching {
                val bytes = android.util.Base64.decode(content.substringAfter("base64,"), android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (bmp != null) {
            val ratio = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
            Image(bmp, null, Modifier.widthIn(max = 200.dp).heightIn(max = 260.dp).fillMaxWidth(), contentScale = ContentScale.FillWidth)
        } else Text("[图片]", color = if (mine) Color.White else LemonColors.TextMain, fontSize = 15.sp)
    } else {
        Text(content, color = if (mine) Color.White else LemonColors.TextMain, fontSize = 15.sp)
    }
}

// 图片压缩 → base64 dataURL（最长边 1024，质量 70）
fun compressImage(ctx: android.content.Context, uri: Uri): String? {
    return runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
        "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }.getOrNull()
}

// 聊天输入栏（文本 + 加号图片面板）
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatInputRow(onSendText: (String) -> Unit, onSendImage: (String) -> Unit, sending: Boolean) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var showPanel by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val albumLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { u ->
        val data = compressImage(ctx, u)
        if (data != null) onSendImage(data)
        else Toast.makeText(ctx, "图片读取失败", Toast.LENGTH_SHORT).show()
    } }
    val cameraLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) cameraUri?.let { u ->
            val data = compressImage(ctx, u)
            if (data != null) onSendImage(data)
            else Toast.makeText(ctx, "图片读取失败", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { showPanel = !showPanel }) {
            Icon(Icons.Filled.AddCircle, "更多", tint = LemonColors.Green, modifier = Modifier.size(28.dp))
        }
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            placeholder = { Text("说点什么…", fontSize = 14.sp, color = LemonColors.TextSub) },
            modifier = Modifier.weight(1f),
            shape = LemonShapes.pill,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LemonColors.Green,
                unfocusedBorderColor = Color(0xFFDDE5E0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { val t = input.trim(); if (t.isNotEmpty() && !sending) { onSendText(t); input = "" } },
            shape = LemonShapes.pill,
            colors = ButtonDefaults.buttonColors(containerColor = LemonColors.Green),
            modifier = Modifier.size(48.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            if (sending) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            else Icon(Icons.Filled.Send, "发送", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }

    if (showPanel) {
        ModalBottomSheet(onDismissRequest = { showPanel = false }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).clip(LemonShapes.pill).background(LemonColors.GreenLight)
                            .clickable {
                                val file = java.io.File(ctx.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                                cameraUri = uri
                                showPanel = false
                                cameraLauncher.launch(uri)
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.PhotoCamera, null, tint = LemonColors.Green, modifier = Modifier.size(26.dp)) }
                    Text("相机", fontSize = 12.sp, color = LemonColors.TextSub)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).clip(LemonShapes.pill).background(LemonColors.GreenLight)
                            .clickable {
                                showPanel = false
                                albumLauncher.launch(androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.PhotoLibrary, null, tint = LemonColors.Green, modifier = Modifier.size(26.dp)) }
                    Text("相册", fontSize = 12.sp, color = LemonColors.TextSub)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// 好友申请对话框（微信式带申请信息）
@Composable
fun FriendRequestDialog(me: Api.User, targetUsername: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("我是 ${me.nickname}") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("好友申请", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("对方用户名：$targetUsername", fontSize = 13.sp, color = LemonColors.TextSub)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = msg, onValueChange = { msg = it },
                    label = { Text("申请信息") },
                    shape = LemonShapes.input, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LemonColors.Green)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    Api.sendFriendRequest(me.id, targetUsername, msg.trim()).onSuccess {
                        Toast.makeText(ctx, "申请已发送", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }.onFailure { e ->
                        Toast.makeText(ctx, e.message ?: "发送失败", Toast.LENGTH_LONG).show()
                    }
                    busy = false
                }
            }) { Text("发送申请", color = LemonColors.Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = LemonColors.TextSub) } }
    )
}

