# 示例伴侣 APK

这两个示例来自 App 仓库 `samples/`，只保留了 `src/main`（Manifest + Kotlin 源码），
构建脚本在 App 仓库中（它们依赖 App 的 Gradle 版本目录）。阅读源码即可了解接入方式。

## kimi-native（composer dock）

- 插件 id：`changfenhuang.kimi-oauth`
- 配对 JS 包：`dsh-plugin-subscription-oauth`
- 落点：`NativeSlot.COMPOSER_DOCK`
- 内容：在输入框 dock 上方显示 Kimi 登录状态/授权码/额度胶囊
- 关键文件：`src/main/kotlin/com/example/kimi/nativeui/KimiNativePlugin.kt`

适合参考：**如何做一个非页面的嵌入型落点**。

## subscription-native（设置页整页）

- 插件 id：`changfenhuang.subscription`
- 配对 JS 包：`dsh-plugin-subscription-oauth`
- 入口：`PageEntry.Placement.SETTINGS`
- 内容：订阅服务列表、OAuth 登录、账号/模型/额度管理
- 关键文件：`src/main/kotlin/com/example/subscription/nativeui/SubscriptionNativePlugin.kt`
  与 `SubscriptionSettingsPage.kt`

适合参考：**如何做一整页原生设置 UI、如何通过 RPC 读写 Host 状态**。

## 在自己的工程里构建

在 App 仓库中运行：

```bash
./gradlew :samples:kimi-native:assembleDebug
./gradlew :samples:subscription-native:assembleDebug
```

安装后用插件页「原生插件」应能看到对应条目；插件详情页会显示「包含的组件」与运行状态。
