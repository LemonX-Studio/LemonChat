# 🍋 Lemon Chat

安卓原生即时通讯 APP（Kotlin + Jetpack Compose），白 + 绿清新 UI，全圆角 + 悬浮毛玻璃 Tab 栏。

## 当前版本：v0.1.0

- 登录 / 注册界面（UI，后端待接入）
- 四大页面：消息 / 通讯录 / 团队 / 我的
- 悬浮毛玻璃 Tab 栏：高亮玻璃块随选中位置滑动，点击弹跳
- 切页左右滑动动画

## 打包

无需本地编译环境，push 到 main 后 GitHub Actions 自动构建 APK：

1. 打开仓库 → **Actions** 标签
2. 点最新一次运行，等绿勾 ✅
3. 底部 **Artifacts** 下载 `LemonChat-v0.1.0`
4. 解压得到 apk，安装（需允许未知来源）

签名：`android/lemonchat.jks`（alias `LemonChat`，已配置 release 签名）

## 目录结构

```
android/                  安卓工程（Kotlin + Compose）
  app/src/main/java/com/lemon/chat/
    MainActivity.kt       登录页 + 四个页面
    ui/FloatingTabBar.kt  悬浮毛玻璃 Tab 栏
    ui/Theme.kt           主题（颜色/圆角）
.github/workflows/        自动打包流水线
backend/                  后端（规划中：Vercel + Neon Postgres）
```

## Roadmap

- [x] v0.0.1 首版 UI
- [x] v0.1.0 Tab 栏交互重构（滑动高亮）
- [x] 后端接入：账号注册/登录（Vercel + Neon）
- [x] 单聊收发消息（实时轮询）
- [ ] 群聊 / 语音 / 视频
- [ ] 好友 / 群聊 / 消息收发
