# DSH Mobile Native UI SDK

> 中文 | [English](README.en.md)

> 仓库：<https://github.com/EricFL1998/dsh-mobile-plugin-sdk>  
> 在线文档：<https://ericfl1998.github.io/dsh-mobile-plugin-sdk/>（GitHub Pages）
>
> 为已有的 DSH JS 插件增加一个 **Kotlin/Compose 原生 UI 伴侣 APK**：JS 侧逻辑不动，
> App 识别伴侣 APK 后把插件 UI 从 WebView 换成原生界面；不安装 APK 时继续使用 Web UI。
>
> SDK API v1 · 适用 App：`com.dsh.mobile` · minSdk 26 · compileSdk 35 · Kotlin 2.1.10。

---

## 0. 这是什么，怎么分工

一个插件由两份产物组成：

| 产物 | 位置 | 谁负责 |
| --- | --- | --- |
| JS 插件包 | Host（手机内 proot/Node） | 插件作者已有 |
| 伴侣 APK | App 进程内，Dex 加载 | 本文档新增 |

职责边界：

1. **逻辑永远在 Host**：登录态、网络请求、模型调用、数据查询都留在 `lib/index.js`；
   原生 APK 只画 UI、回传用户意图。
2. **凭证不出 Host**：APK 不接触 token / API key；需要数据就通过 RPC 向 Host 要。
3. **原生是增强，不是必需**：不装 APK，插件继续走 Web UI；装了才启用原生页面/落点。
4. **渲染无状态**：Compose 只消费 Host 返回的数据，不建立网络连接。

原生 UI 读数据走一条链路：

```
原生页面启动
  → PluginRpcHost.callRpc("demo.status", {})
  → Host 插件返回 { result: { ok: true, value } }
  → Compose 用 value 渲染
```

写数据走另一条链路（不在 APK 里改业务状态）：

```
用户点击原生按钮
  → host.dispatchEvent("dsh-plugin-demo#demo", "submit", { actionId: "bump", by: 1 })
  → bridge 命中 JS 插件的 mobileUi 动作
  → Host 状态变化，bridge 排 client/refresh
  → 原生页收到 refreshEvents 后重读 RPC
```

仓库里的 **Demo 插件**是唯一示例，JS 与原生两半都完整可跑：

| 项 | 值 |
| --- | --- |
| JS 包 | `dsh-plugin-demo`（0.1.0） |
| 伴生 `pluginId`（manifest `dsh.plugin.id`） | `demo.native-ui` |
| 伴生 APK 包名 | `com.dsh.mobile.dsh_plugin_demo` |
| manifest `dsh.plugin.jsPackage` | `dsh-plugin-demo` |
| 设置页组件身份 `componentId` | `dsh-plugin-demo#demo` |
| dock 组件身份 `componentId` | `dsh-plugin-demo#demo-dock` |

建议阅读顺序：

1. 本 README：接通 AAR、manifest、页面/落点、RPC、构建与发布；
2. [`demo/README.md`](demo/README.md)：Demo 插件的身份、契约、构建与真机验证；
3. [`docs/plugin-sdk-guide.md`](docs/plugin-sdk-guide.md)：完整开发规范；
4. [`docs/source-driven-native-ui.md`](docs/source-driven-native-ui.md)：先扫描再实现；
5. [`docs/publishing.md`](docs/publishing.md)：发布、打 tag 与签名约定。

---

## 1. 目录结构

```
dsh-mobile-plugin-native-ui-sdk/
├─ README.md                          ← 本文件
├─ slot-mapping.md                    ← 内置 slot → NativeSlot → 宿主位置
├─ demo/
│  ├─ README.md                       ← Demo 插件（JS + 原生）接入说明
│  ├─ js/                             ← JS 半边：dsh-plugin-demo 0.1.0
│  │  ├─ package.json
│  │  ├─ cordis.patch.yml
│  │  ├─ lib/index.js                 ← 插件入口：name / apply
│  │  ├─ lib/constants.js             ← RPC 前缀、componentId、settings ns
│  │  ├─ lib/state.js                 ← 进程内状态（计数 42）
│  │  ├─ lib/rpc.js                   ← /api/demo.* 路由 + 信封
│  │  ├─ lib/actions.js               ← mobileUi 动作：bump / reset
│  │  ├─ lib/catalog.js               ← demo.catalog 返回的注册清单
│  │  ├─ lib/client.js                ← Web 半边：settings.section + dock
│  │  ├─ locale/{zh,en}.json
│  │  └─ test/rpc.test.mjs
│  └─ native/                         ← 原生半边：demo.native-ui
│     ├─ build.gradle.kts
│     └─ src/main/
│        ├─ AndroidManifest.xml
│        └─ kotlin/com/dsh/mobile/dsh_plugin_demo/
│           ├─ DemoPluginService.kt
│           ├─ DemoNativePlugin.kt
│           ├─ DemoSettingsPage.kt
│           ├─ DemoDockContribution.kt
│           ├─ DemoSlotContribution.kt
│           └─ DemoRpc.kt
├─ docs/
│  ├─ plugin-sdk-guide.md             ← 开发与联调步骤
│  ├─ native-plugin-sdk.md            ← 插件侧契约
│  ├─ native-plugin-host.md           ← App 侧宿主容器与注册表
│  ├─ plugin-conversion-guide.md      ← Web UI 迁移到原生 UI
│  ├─ source-driven-native-ui.md      ← 扫描驱动的接入流程
│  ├─ mobile-bridge.md                ← Host ↔ App bridge 协议
│  ├─ publishing.md                   ← 发布与打 tag
│  ├─ pitfalls.md                     ← 检查清单与最佳实践
│  ├─ slot-inventory.md               ← App 内置 slot 覆盖清单
│  ├─ plugin-ui-inventory-example.md  ← Demo 插件的扫描输出（含 .json）
│  ├─ index.html                      ← GitHub Pages 站点
│  └─ .nojekyll
├─ reference/native-host/             ← App 侧宿主参考（NativeHost / NativeSlotHost / SlotPopup）
├─ sdk/                               ← SDK 源码与独立构建配置
│  ├─ README.md
│  ├─ build.gradle.kts
│  ├─ settings.gradle.kts
│  ├─ gradle.properties
│  └─ src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/
├─ tools/
│  ├─ plugin_ui_scanner.py            ← 插件源码扫描器
│  └─ sdk-mapping.json                ← 内置 slot 映射/宿主快照（扫描器独立运行）
└─ artifacts/
   └─ dsh-native-ui-sdk-release.aar   ← 预编译 SDK AAR
```

> 仓库不打包真实业务插件；`demo/` 是虚构案例，代码可直接跑。

---

## 2. 依赖接入

### 2.1 用 AAR（推荐）

把 [`artifacts/dsh-native-ui-sdk-release.aar`](artifacts/dsh-native-ui-sdk-release.aar) 放进伴侣
APK 工程的 `libs/`，然后声明依赖：

```kotlin
dependencies {
    implementation(files("libs/dsh-native-ui-sdk-release.aar"))

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
```

AAR 不携带传递依赖，Compose 与序列化库由你的伴侣工程自行声明。`build.gradle.kts`
还需要：

```kotlin
android {
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
```

### 2.2 用源码

把 [`sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/`](sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/)
下的源码拷进你的 library 模块（包名保持不变）。源码与 AAR 等价。

`sdk/` 本身是独立 root project，构建方式见 [`sdk/README.md`](sdk/README.md)：用 Android
Studio 打开该目录，或用兼容 AGP 8.9 的 Gradle 8.11+ 执行 `gradle assembleRelease`。
**SDK 仓库不带 Gradle Wrapper**，不要在本仓库根目录执行 `./gradlew`。

### 2.3 工具链版本

| 项 | 值 |
| --- | --- |
| SDK API | v1（`dsh.plugin.apiVersion = 1`） |
| compileSdk | 35 |
| minSdk | 26 |
| JDK | 17 |
| Kotlin | 2.1.10 |
| Compose BOM | 2025.02.00 |
| kotlinx-serialization | 1.8.0 |

---

## 3. 伴侣 APK 最小实现

### 3.1 包名规范（强制）

> **一个 JS 插件 = 一个 companion APK。** 包括 Web 注入式浮层这类形态：也写 companion，
> 通过 `NativeSlot.PLUGIN_OVERLAY` 画浮层，不在 App 里为单个插件增加专用容器类型。
> 宿主只提供安全区全屏画布，位置/拖动/吸附/动画由 companion 自己负责。

伴侣 APK 的 `applicationId` 和 `namespace` 统一使用：

```
com.dsh.mobile.<完整插件名>
```

“完整插件名”取 JS `package.json` 的 `name`，**保留 `dsh-plugin-` 前缀**，转小写，
非 `[a-z0-9]` 字符替换成 `_`：

| JS 插件完整名 | 伴侣包名 |
| --- | --- |
| `dsh-plugin-demo` | `com.dsh.mobile.dsh_plugin_demo` |
| `dsh-plugin-my-plugin` | `com.dsh.mobile.dsh_plugin_my_plugin` |
| `dsh-genui` | `com.dsh.mobile.dsh_genui` |

规则细节：

- 不要缩写掉 `dsh-plugin-` 前缀，它是插件名的一部分；
- 同一 JS 包有多个能力伴侣时，主伴侣用全名，额外能力加 `_<capability>`；
- `dsh.plugin.id` 是插件身份，和包名解耦，使用你自己的反向域名 id；
- 包名与 `dsh.plugin.id` 一旦发布不要改，否则会被系统或 App 当成另一个插件。

### 3.2 AndroidManifest

Demo 的完整 manifest（[`demo/native/src/main/AndroidManifest.xml`](demo/native/src/main/AndroidManifest.xml)）：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.dsh.mobile.dsh_plugin_demo">

    <application>
        <service
            android:name=".DemoPluginService"
            android:exported="true"
            android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">
            <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
            <meta-data android:name="dsh.plugin.minAppVersion" android:value="100" />
            <meta-data android:name="dsh.plugin.version" android:value="1" />
            <meta-data android:name="dsh.plugin.id" android:value="demo.native-ui" />
            <meta-data android:name="dsh.plugin.name" android:value="Demo 插件" />
            <!-- 与 demo/js/package.json 的 name 一致；App 据此配对 JS 插件 -->
            <meta-data android:name="dsh.plugin.jsPackage" android:value="dsh-plugin-demo" />

            <intent-filter>
                <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

所有 `dsh.plugin.*` meta-data 都放在 **Service 级**，不是 `<application>` 级：

| meta-data | 含义 |
| --- | --- |
| `dsh.plugin.apiVersion` | SDK API 版本，当前 `1` |
| `dsh.plugin.minAppVersion` | 最低 App versionCode，Demo 为 `100` |
| `dsh.plugin.version` | 伴侣 APK 描述符版本，整数 |
| `dsh.plugin.id` | 插件 id（`demo.native-ui`），发布后不可改 |
| `dsh.plugin.name` | 插件详情/原生插件列表展示名 |
| `dsh.plugin.jsPackage` | 配对的 JS 包名，必须与 `package.json` 的 `name` 完全一致 |

### 3.3 Service 入口

```kotlin
class DemoPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(DemoNativePlugin())

    companion object {
        /** App 在自身进程加载页面时使用的入口；只写 onBind 不会注册原生页面。 */
        @JvmStatic
        fun plugin(): DshUiPlugin = DemoNativePlugin()
    }
}
```

App 用 `packageContext.classLoader` 把伴侣 APK 加载进自己的进程，再通过
`com.deepseek.dsh.action.PLUGIN_UI` 找到 Service；不需要跨进程通信。

### 3.4 插件入口类

```kotlin
class DemoNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "demo.native-ui",
        name = "Demo 插件",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,
            PluginCapabilities.STORAGE,
            PluginCapabilities.PAGE_ENTRY,
        ),
    )

    override fun createRenderer(): ComponentRenderer = DemoRenderer()

    init {
        // 页面型 UI：在设置页/插件页注册入口
        PageRegistry.register(DemoSettingsPage())
        // 嵌入型 UI：在每个已就绪落点注册贡献
        DemoSlots.entries
            .filter { it.hostReady && it.slot != NativeSlot.COMPOSER_DOCK }
            .forEach { SlotRegistry.register(DemoSlotContribution(it)) }
        // dock 单独展示 popover + App 菜单材质
        SlotRegistry.register(DemoDockContribution())
    }
}
```

`descriptor.capabilities` 是白名单：调用未声明的能力会被 Host 拒绝。能力常量见
`PluginCapabilities`（OPEN_URL / STORAGE / NOTIFY / SHARE / CLIPBOARD / WORKSPACE /
TOOL_RENDER / PAGE_ENTRY）。

---

## 4. 页面与落点

原生 UI 有两种形态，按“整页 / 局部”选择。

### 4.1 页面型：`PageContent`（设置页 / 插件页）

适合完整管理页（账号、用量、配置管理）。注册后设置页「插件界面」或插件页出现入口，
点进去是整页原生 Compose，不再加载 WebView。

```kotlin
class DemoSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "demo",
        title = "Demo 插件",
        subtitle = "JS 插件 + 原生伴生：RPC 与落点接入示例（虚构）",
        placement = PageEntry.Placement.SETTINGS,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        DemoSettingsContent(theme = theme, host = host, refreshEvents = null)
    }
}
```

`PageEntry.Placement`：

- `SETTINGS`：设置页「插件界面」分组 → 整页原生设置页；
- `PLUGINS`：插件页 tab → 整页原生插件页。

设置页被原生接管的两个条件：

1. JS 侧注册 `settings.section` 且 `id = "demo"`；
2. 伴侣 APK 通过静态工厂加载并注册 `PageEntry(id = "demo", placement = SETTINGS)`。

两者组件身份都是 `dsh-plugin-demo#demo`（`<jsPackage>#<PageEntry.id>`），App 才会跳过
WebView。只安装 APK、未注册页面时仍走 Web。

### 4.2 嵌入型：`SlotContribution`（输入栏、消息、工具卡等）

适合局部增强（composer dock 胶囊、消息动作、工具卡片）。落点由 `NativeSlot` 声明，
App 在每个位置放了宿主容器；没有插件注册时容器不占空间。

```kotlin
class DemoDockContribution : SlotContribution() {
    override val slot: NativeSlot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes: Set<String> = setOf("demo.dock")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // 在输入栏上方画 "Demo 42" 胶囊；点按用 PluginPopover 展开详情
    }
}
```

渲染模式：

- `ENHANCE`：内置默认内容保留，插件内容加在旁边/下方；
- `OVERRIDE`：插件接管该落点，内置默认隐藏；插件异常/卸载时自动回退内置 UI。

Demo 覆盖 22 个 `NativeSlot` 条目，其中 21 个注入真实界面；`WEB_ISLAND_FULL` 是整岛
Web 回退容器，Demo 不注入（会整屏覆盖 App），只在 Demo 页展示说明：

| NativeSlot | 位置 | Demo |
| --- | --- | --- |
| `SETTINGS_SHEET` | 设置页 / 插件页入口与整页 | 注入 |
| `SETTINGS_PROVIDER_CARD` | Provider 卡片 | 注入 |
| `SIDEBAR_PANEL` | 左侧栏面板 | 注入 |
| `RIGHT_PANEL` | 聊天右侧栏（窄屏整屏覆盖） | 注入 |
| `COMPOSER_DOCK` | 输入栏 dock | 注入（`Demo 42` 胶囊 + popover） |
| `COMPOSER_DOCK_ACTIVITY` | dock activity（tok/s 条目） | 注入 |
| `COMPOSER_DOCK_USAGE` | dock usage（缓存命中条目） | 注入 |
| `COMPOSER_DOCK_CONTEXT` | dock context（上下文计量） | 注入 |
| `COMPOSER_LEFT` | 输入栏左 | 注入 |
| `COMPOSER_RIGHT` | 输入栏右 | 注入 |
| `COMPOSER_MODEL` | 模型选择 | 注入 |
| `COMPOSER_PERMISSION` | 权限选择 | 注入 |
| `COMPOSER_PLAN` | Plan 模式 | 注入 |
| `COMPOSER_ATTACHMENTS` | 附件入口 | 注入 |
| `COMPOSER_BAR` | 整个 composer | 注入 |
| `CONVERSATION_VIEW` | 会话视图 | 注入 |
| `SESSION_HEADER_CORNER` | 会话标题角 | 注入 |
| `COMPOSER_OVERLAY` | 输入框浮层 | 注入 |
| `PLUGIN_OVERLAY` | 插件浮层 | 注入 |
| `MESSAGE_ACTION` | 消息动作 | 注入 |
| `TOOL_VIEW_SHEET` | 工具详情 | 注入 |
| `CHAT_STREAM` | 聊天流 | 注入 |
| `WEB_ISLAND_FULL` | 整岛 Web 回退容器 | 不注入（宿主已就绪） |

内置 slot 到 `NativeSlot` 的完整映射与宿主位置见 [`slot-mapping.md`](slot-mapping.md)。
注册了没有宿主的落点，贡献不会显示；先查 [`docs/slot-inventory.md`](docs/slot-inventory.md)。

### 4.3 浮层：`PluginPopover`

Web 侧用锚定浮层的交互，原生侧对应 `PluginPopover`：贴着触发元素显示，材质来自
`PluginTheme` 的 popup 字段（表面色、发丝边、圆角、菜单阴影），不要用居中 `Dialog`
或自造 Material Card。Demo 的 dock 胶囊即此用法。

### 4.4 通用渲染器：`ComponentRenderer`

`ComponentRenderer` 把 Host 下发的 JSON spec 画成通用组件；页面和落点也可以直接用它：

```kotlin
class DemoRenderer : ComponentRenderer {
    override val supportedTypes = setOf("demo.card")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.shapeCard,
            colors = CardDefaults.cardColors(containerColor = theme.colorSurface),
        ) {
            Text("Demo 组件渲染", style = theme.textTitle, color = theme.colorOnSurface)
        }
    }
}
```

`supportedTypes` 是降级开关：App 只对命中的类型走原生渲染，未命中回退插件 Web UI。

---

## 5. RPC 与事件

原生页面通过 `PluginRpcHost` 调 Host 插件自己的路由（Demo 为 `demo.*`）。方法名由
JS 插件注册，App 不新增协议。

### 5.1 信封契约

请求体（`payload` 原样传递，不包 `args`）：

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

成功响应：

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

业务失败也是 **HTTP 200**，错误放在 `result.error`：

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

传输层错误才用非 200：`400`（JSON 无法解析或信封不匹配）、`415`（非
`application/json`）。原生端先解信封，再按 `code` 分支，不要匹配文案。

### 5.2 Demo 的 6 个端点

| 端点 | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | 更新后的 status 快照 |
| `demo.reset` | `{}` | 重置后的 status 快照 |
| `demo.echo` | 任意 JSON | `{ received: <原样回显> }` |

错误码：`demo/bad-request`（400）、`demo/unsupported-media-type`（415）、
`demo/unknown-endpoint`（HTTP 200 + `result.error`）。

### 5.3 原生读：`PluginRpcHost.callRpc`

`callRpc` 是回调式 API。Demo 用顶层扩展把它包成 `suspend`，页面与落点共用：

```kotlin
internal suspend fun PluginRpcHost.demoCall(
    method: String,
    payload: JsonObject = JsonObject(emptyMap()),
): JsonElement? = suspendCancellableCoroutine { continuation ->
    callRpc(method, payload) { result ->
        continuation.resume(result.getOrNull())
    }
}

// 页面里
val body = rpcHost.demoCall(DemoRpc.STATUS)   // demo.status
DemoRpc.value(body)?.let { status = DemoRpc.statusOf(it) }
```

`DemoRpc.value()` 取 `result.value`，`DemoRpc.errorMessage()` 在业务失败时给出可读错误。

### 5.4 原生写：`dispatchEvent` → `mobileUi` 动作 → `client/refresh`

原生控件不执行插件 JS，只上报“哪个组件、哪个动作、什么 payload”：

```kotlin
val payload = buildJsonObject {
    put("actionId", "bump")
    put("by", 1)
}
host.dispatchEvent(DemoRpc.PAGE_COMPONENT_ID, "submit", payload) // dsh-plugin-demo#demo
```

完整链路：

```
host.dispatchEvent(componentId, "submit", payload)
  → App bridge 调 client-slots/dispatch
  → JS 插件 mobileUi 动作（demo/js/lib/actions.js）
  → Host 状态变化，bridge 排 client/refresh
  → 原生页收到 refreshEvents 后重读 RPC
```

页面按组件身份过滤刷新事件，只重拉自己关心的组件：

```kotlin
LaunchedEffect(refreshEvents) {
    refreshEvents?.collect { componentId ->
        if (componentId == DemoRpc.PAGE_COMPONENT_ID || componentId == DemoRpc.JS_PACKAGE) refresh()
    }
}
```

事件名由 bridge 限定为 `press` / `input` / `toggle` / `submit`；Demo 统一用 `submit`。
纯写操作也可以直接走 RPC（Demo 的「重置」就是 `demo.reset`）。

### 5.5 最佳做法

- 组件身份必须是 JS 实际注册的 `<jsPackage>#<slot 内 id>`；写错时 bridge 找不到动作；
- 异步回调更新页面前先检查 `PageLifecycle.LocalIsActive.current`，避免用户返回后继续写入
  已离开的页面；
- 原生端只展示 `result.value` 里的纯展示数据，不自己维护业务状态；
- 长耗时逻辑留在 Host，不要在 Compose 组合里做阻塞调用；
- 失败要有可读错误与重试入口，不要静默；
- 首屏 Host 未就绪时退避重试（Demo dock 为 800ms / 2s），之后按 `client/refresh` 或
  周期轮询重读。

---

## 6. 主题与能力

所有颜色/字号/圆角从 `PluginTheme` 取，不硬编码：

```kotlin
val theme = host.currentTheme()
Text(
    text = "标题",
    style = theme.textTitle,
    color = theme.colorOnSurface,
    modifier = Modifier.padding(theme.spacingUnit),
)
```

宿主能力调用前用 `host.requestCapability(...)` 检查；未声明或用户关闭时降级：

```kotlin
if (host.requestCapability(PluginCapabilities.OPEN_URL)) {
    host.openUrl(url)
}
```

### 设置页面板：DshPanel

设置页/详情页不要直接用 Material `Card`，用 SDK 的 `design/DshPanel.kt`，与 App 内置
设置页共用同一套底色、0.5dp 发丝边、16dp 圆角、44dp 行高和 11sp 分组标题：

```kotlin
DshPanel(theme = theme, title = "账号") {
    DshSectionLabel("订阅", theme)
    DshPanelRow(theme, title = "自动续费", value = "已开启", showDivider = true)
    DshPanelRow(theme, title = "管理订阅", onClick = { /* openUrl */ })
    DshPanelAction("查看全部", theme) { /* ... */ }
}
```

颜色全部来自 `PluginTheme`（`colorPanel` / `colorPanelBorder` / `colorLabelPrimary` /
`colorLabelSecondary` / `colorLabelTertiary` / `colorLink`），宿主按当前明暗主题下发；
插件不写死色值，也不写深浅色分支。完整的组件清单、间距、按钮层级与验收项见
[`docs/ui-guideline.md`](docs/ui-guideline.md)。

`PluginStorage` 只能存展示偏好（如“上次选中的 tab”），**不能存 token/secret**。

---

## 7. 降级

原生层永远是增强，不是必需：

| 场景 | 行为 |
| --- | --- |
| 用户未装伴侣 APK | 插件走 Web UI（完整界面），功能完整 |
| APK 已装但 ABI 不兼容 | 显示“插件需更新”，降级 Web UI |
| `supportedTypes` 未命中 spec 类型 | 该组件降级，其余原生 |
| 渲染器抛异常 | 错误围栏捕获，显示占位块，不影响 App 与其他插件 |
| 用户卸载 APK | 即时摘除渲染位，回退 Web UI |

**因此**：JS 侧功能必须在纯 Web 下完整可用，不得假设原生端在场。Demo 的 Web 回退在
[`demo/js/lib/client.js`](demo/js/lib/client.js)：它注册 `settings.section(id = "demo")` 与
`conversation.composer.dock(id = "demo-dock")`，用
`connection.rpc.call("/api", "demo.status")` 读同一份 Host 状态。

---

## 8. 先扫描插件源码，再写原生 UI（推荐第一步）

扫描器读取插件自己的源码，得到 slot / entry / component / RPC / UI 树与缺口；不需要
截图，也不需要 App 仓库。完整流程见
[`docs/source-driven-native-ui.md`](docs/source-driven-native-ui.md)。

```bash
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

说明：

- `--plugin` 指向插件包根目录，优先读 `lib/*.js.map`（内嵌原始 TSX），其次 `src/*.tsx`，
  最后回退编译后的 `lib/client.js`；
- `--companion` 让扫描器检查伴侣源码是否真的注册了对应 `NativeSlot` / `PageContent`；
- 扫描器自带 [`tools/sdk-mapping.json`](tools/sdk-mapping.json) 快照（内置 slot 映射与
  宿主覆盖），在 SDK 仓库根目录可直接运行；
- 需要核对 App 仓库里的宿主源码时，可加可选参数 `--root <App 仓库>`；日常接入不需要。

Demo 的扫描输出见
[`docs/plugin-ui-inventory-example.md`](docs/plugin-ui-inventory-example.md)：
2 个注册落点全部映射、宿主就绪、伴生实现，`--check` 通过。

```text
$ python tools/plugin_ui_scanner.py --plugin demo/js \
    --companion demo/native/src/main/kotlin --check
plugin UI coverage check OK
```

按报告映射到原生实现：

| 扫描结果 | 原生接入方式 |
| --- | --- |
| `settings.section` 页面 | `PageContent` + `PageRegistry` |
| `conversation.composer.dock` 条目 | `SlotContribution`（`COMPOSER_DOCK`） |
| 内置控件（输入栏、dock 读数） | `OfficialSlot` 包装，可单项 ENHANCE/OVERRIDE |
| 信息型卡片/列表 | `SpecSlotContribution` + dsh-ui spec |
| 插件 RPC / `useProjection` | `PluginRpcHost.callRpc`，不新增 Host 协议 |

内置 slot 与宿主的一致性由 `tools/sdk-mapping.json` 快照与 App 侧的
`SlotCoverageTest` 维护；详见 [`docs/source-driven-native-ui.md`](docs/source-driven-native-ui.md)。

---

## 9. 构建与联调 Demo

### 9.1 前置条件

- App 开发仓库（提供 Gradle Wrapper、版本目录与 `:plugin:native-sdk` 模块）；
- 一台安装了 App 的 Android 设备，开启 USB 调试；
- Node.js ≥ 20（跑 Demo 的 JS 自测）；
- Python 3（跑扫描器）。

SDK 仓库自身不带 Gradle Wrapper，`demo/native` 的构建在 App 仓库里完成。

### 9.2 构建伴侣 APK

把 `demo/native` 复制到 App 仓库的 `samples/demo-native`，在 App 仓库
`settings.gradle.kts` 中加入：

```kotlin
include(":samples:demo-native")
```

然后在 **App 仓库根目录**执行：

```bash
./gradlew :samples:demo-native:assembleDebug
```

产物：

```text
samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk
```

`demo/native/build.gradle.kts` 使用 App 仓库的版本目录，并依赖 App 内的
`:plugin:native-sdk` 模块；为你自己的插件构建时，改用第 2 节的 AAR 或 `sdk/` 源码。

### 9.3 安装

```bash
# 1) JS 插件安装到 Host profile（本地路径）
dsh plugin --profile web add ./demo/js

# 2) 安装伴侣 APK
adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk
```

安装/卸载 APK 后回到 App 前台会触发重扫（`onResume` + PackageManager 广播）。

### 9.4 自测

```bash
# RPC / 动作 / 状态隔离（无需 Host、Android、网络）
node --test demo/js/test/rpc.test.mjs

# 扫描器：JS 注册的 2 个落点都有 NativeSlot 映射、宿主与伴生实现
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

### 9.5 真机验证

1. **插件页 → 原生插件**：能看到伴侣条目；
2. **插件详情**：显示「包含的组件」及组件数量，Demo 两个组件显示为「原生」且开关打开；
3. **设置页**：出现「Demo 插件」入口，点进去是原生页面，显示计数、上次动作、更新时间；
4. **输入栏**：dock 出现 `Demo 42` 胶囊；点按 popup 紧贴胶囊，使用 App 菜单材质；
5. **写链路**：点「服务端 +1」后计数变化；点「重置」回到 42；
6. **回退**：卸载 Demo APK 后，设置页与 dock 回到 Web 版本，仍能读 `demo.status`。

### 9.6 调试

```bash
# App 日志
adb logcat | grep -E "NativeHost|DshPlugin|plugin"

# Host 日志
adb shell run-as com.dsh.mobile ls -t files/logs
adb shell run-as com.dsh.mobile cat files/logs/host_<最新>.log
```

插件详情「包含的组件」为 0 时，按顺序检查：

- `dsh.plugin.jsPackage` 与 JS `package.json` 的 `name` 是否完全一致；
- 插件是否已启用；
- APK 安装后是否触发重扫（切后台再回前台）。

RPC 报错时先看 Host 日志与信封里的 `result.error.code`。

---

## 10. 发布

### 10.1 发布物

| 发布物 | 去哪里 | 谁消费 |
| --- | --- | --- |
| JS 插件 | npm（`dsh-plugin-*`） | Host 的 `dsh plugin add` / 插件页「添加插件」 |
| 伴侣 APK | GitHub Releases（`.apk` asset） | 插件商店、系统安装器 |
| SDK AAR（SDK 仓库自身） | GitHub Releases（`.aar` asset） | 插件作者编译伴侣 APK |

一个插件可以只发 JS，也可以 JS + APK 成对发布。只发 APK 时，APK 必须通过
`dsh.plugin.jsPackage` 指向可安装的 JS 插件。

### 10.2 Demo 的发布示例

- JS 包：`dsh-plugin-demo`（npm，0.1.0）；
- 伴侣 APK：`dsh-plugin-demo-companion-1.0.0.apk`，包名
  `com.dsh.mobile.dsh_plugin_demo`，`dsh.plugin.id = demo.native-ui`；
- tag / Release：`v1.0.0`；
- 签名：测试版可用 debug 签名，正式分发前换 release keystore。

```bash
# 1) JS 插件（在插件仓库根目录）
npm publish --access public

# 2) 伴侣 APK tag + Release（资产名以 .apk 结尾）
git tag -a v1.0.0 -m "dsh-plugin-demo companion 1.0.0"
git push origin v1.0.0

# 3) 创建 Release
gh release create v1.0.0 dsh-plugin-demo-companion-1.0.0.apk \
  --title "v1.0.0" \
  --notes "Companion APK for dsh-plugin-demo 0.1.0"

# 4) 商店发现 topic
gh api -X PUT repos/<owner>/<repo>/topics \
  -f 'names[]=dsh-plugin' \
  -f 'names[]=dsh-mobile-plugin'
```

约定：

- tag 用 `v<js-version>`；只更新伴侣 APK 时可用 `v<js-version>-native`，例如 `v0.1.0-native`；
- 商店只读 **latest release**，不要发成 prerelease；
- Release 至少有一个 `.apk` 资产，资产名以 `.apk` 结尾；
- 更新用新 tag + 新 Release，不要覆盖已有资产；
- SDK 仓库自身用 `sdk-v1.0.0` 这类前缀，和插件版本区分。

### 10.3 发布 SDK AAR

`sdk/` 是独立工程，构建产物在 `sdk/build/outputs/aar/` 下：

```bash
# 1) 在 sdk/ 目录构建（Android Studio 或兼容 AGP 8.9 的 Gradle 8.11+）
gradle assembleRelease

# 2) 回到 SDK 仓库根目录，复制为固定文件名
cp sdk/build/outputs/aar/*-release.aar artifacts/dsh-native-ui-sdk-release.aar
```

打 tag 与发布：

```bash
git tag -a sdk-v1.0.0 -m "DSH Native UI SDK 1.0.0"
git push origin sdk-v1.0.0
gh release create sdk-v1.0.0 \
  artifacts/dsh-native-ui-sdk-release.aar \
  --title "SDK v1.0.0" \
  --notes "Native UI SDK AAR"
```

完整命令、版本对照、签名与更新提醒规则见 [`docs/publishing.md`](docs/publishing.md)。

---

## 11. 发布前检查清单

- [ ] `dsh.plugin.id` 稳定且全局唯一，组件 id 前缀一致；
- [ ] `dsh.plugin.jsPackage` 与 JS `package.json` 的 `name` 完全匹配；
- [ ] `dsh.plugin.apiVersion = 1`，`minAppVersion` 不低于目标 App；
- [ ] 所有网络/凭证逻辑留在 Host，APK 内不出现 token/API key；
- [ ] 只使用 `PluginTheme` 的颜色/字号/圆角；
- [ ] 已检查目标落点是否在 [`slot-mapping.md`](slot-mapping.md) 中有宿主；
- [ ] 未安装 APK 时 Web UI 仍完整可用；
- [ ] 原生页面有加载/空/错误状态，RPC 失败不静默；
- [ ] 在深色模式、大字号、横屏下自查布局；
- [ ] 扫描器 `--check` 通过（插件有、伴侣也有实现）；
- [ ] `node --test demo/js/test/rpc.test.mjs` 通过。

---

## 12. 版本

| 项 | 值 |
| --- | --- |
| SDK API | v1 |
| App 插件 API | `dsh.plugin.apiVersion = 1` |
| 最低 App 版本 | `dsh.plugin.minAppVersion = 100` |
| Kotlin | 2.1.10 |
| Compose BOM | 2025.02.00 |
| kotlinx-serialization | 1.8.0 |
| minSdk | 26 |
| compileSdk | 35 |

不兼容变更会提升 `apiVersion`；App 按 APK 声明的版本加载，不匹配时回退 Web UI。

---

## 13. 文档索引

| 文档 | 内容 |
| --- | --- |
| [`demo/README.md`](demo/README.md) | Demo 插件的身份、契约、构建与验证 |
| [`demo/js/README.md`](demo/js/README.md) | Demo 的 JS 半边：RPC、动作、Web 回退 |
| [`docs/plugin-sdk-guide.md`](docs/plugin-sdk-guide.md) | 原生插件完整开发规范 |
| [`docs/ui-guideline.md`](docs/ui-guideline.md) | 原生 UI 设计规范：DshPanel / DshButton / 间距 / 验收 |
| [`docs/native-plugin-sdk.md`](docs/native-plugin-sdk.md) | 插件侧 SDK 契约 |
| [`docs/native-plugin-host.md`](docs/native-plugin-host.md) | App 侧宿主容器与注册表 |
| [`docs/plugin-conversion-guide.md`](docs/plugin-conversion-guide.md) | Web UI 迁移到原生 UI |
| [`docs/source-driven-native-ui.md`](docs/source-driven-native-ui.md) | 扫描驱动的接入流程 |
| [`docs/mobile-bridge.md`](docs/mobile-bridge.md) | Host ↔ App bridge 协议 |
| [`docs/publishing.md`](docs/publishing.md) | 发布、tag 与签名 |
| [`docs/pitfalls.md`](docs/pitfalls.md) | 接入检查清单与最佳实践 |
| [`docs/plugin-ui-inventory-example.md`](docs/plugin-ui-inventory-example.md) | Demo 插件的扫描输出 |
| [`docs/slot-inventory.md`](docs/slot-inventory.md) | App 内置 slot 覆盖清单 |
| [`slot-mapping.md`](slot-mapping.md) | 内置 slot → NativeSlot → 宿主位置 |
| [`sdk/README.md`](sdk/README.md) | SDK 源码与独立构建 |
| [`reference/native-host/`](reference/native-host/) | App 侧宿主参考实现 |
