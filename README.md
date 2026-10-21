# DSH Mobile Native UI SDK

> 仓库：<https://github.com/EricFL1998/dsh-mobile-plugin-sdk>  
> 在线文档：<https://ericfl1998.github.io/dsh-mobile-plugin-sdk/>（GitHub Pages）
>
> 为已有 DSH JS 插件增加一个 **Kotlin/Compose 原生 UI 伴侣 APK**：Host 侧逻辑不动，
> App 自动识别伴侣 APK，把插件 UI 从 WebView/转译树升级成原生界面。
>
> 本 README 按当前 App 的真实接入流程编写（2026-10-21，SDK API v1）。
> 适用 App：`com.dsh.mobile`，minSdk 26，compileSdk 35，Kotlin 2.1.10。

---

## 0. 先理解分工

一个插件由两份产物组成：

| 产物 | 位置 | 谁负责 |
| --- | --- | --- |
| JS 插件包 | Host（手机内 proot/Node） | 插件作者已有 |
| 伴侣 APK | App 进程内，Dex 加载 | 本文档新增 |

职责边界（也是审核底线）：

1. **逻辑永远在 Host**：OAuth、token 刷新、模型调用、数据查询都在 `lib/index.js`；
   原生 APK 只画 UI、回传用户意图。
2. **凭证不出 Host**：APK 不接触 token / API key；需要数据就通过 RPC 向 Host 要。
3. **原生是增强，不是必需**：不装 APK，插件继续走 Web UI；装了才启用原生页面/落点。
4. **渲染无状态**：Compose 只消费 Host 推来的 spec，不建立网络连接。

一次原生交互的完整链路：

```
用户点击原生按钮
  → PluginRpcHost.callRpc(方法, JSON)          // APK → Host
  → Host 插件执行逻辑（OAuth / 查询）
  → client/refresh 推送新 spec / 事件            // Host → App
  → 原生页面重组（重读 RPC 或接收 refresh）
```

---

## 1. 目录结构

```
dsh-mobile-plugin-native-ui-sdk/
├─ README.md                         ← 本文件
├─ slot-mapping.md                   ← 30 个官方 slot → 原生落点 → 宿主位置
├─ postmortem/slot-discovery.md     ← 首版漏落点的复盘与覆盖闸门
├─ docs/
│  ├─ native-plugin-sdk.md           ← 插件侧完整规范
│  ├─ native-plugin-host.md          ← App 侧宿主容器与注册表
│  ├─ plugin-conversion-guide.md     ← Web UI 迁移到原生 UI
│  ├─ plugin-sdk-guide.md            ← 开发与联调步骤
│  ├─ mobile-bridge.md               ← Host ↔ App bridge 协议
│  └─ slot-inventory.md              ← 自动生成的官方 slot 覆盖清单
├─ reference/native-host/            ← App 侧 OfficialSlot / NativeSlotHost 参考
├─ sdk/                              ← SDK 源码与独立构建配置
├─ artifacts/dsh-native-ui-sdk-release.aar
└─ samples/
   ├─ kimi-native/                   ← composer dock 示例
   └─ subscription-native/           ← 设置页整页示例
```

---

## 2. 依赖接入

### 2.1 用 AAR（推荐）

把 `artifacts/dsh-native-ui-sdk-release.aar` 放进伴侣 APK 的 `libs/`：

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

伴侣 APK 的 `build.gradle.kts` 还需要：

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

把 `sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/**` 拷进你的 library
模块（包名保持不变）。`sdk/build.gradle.kts` 是等价独立构建配置，`sdk/README.md`
说明如何单独编译。

---

## 3. 伴侣 APK 最小实现

### 3.1 AndroidManifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myplugin.nativeui">

    <application>
        <service
            android:name=".MyPluginService"
            android:exported="true"
            android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">

            <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
            <meta-data android:name="dsh.plugin.minAppVersion" android:value="12" />
            <meta-data android:name="dsh.plugin.id" android:value="your.plugin.id" />
            <meta-data android:name="dsh.plugin.name" android:value="我的插件" />
            <!-- 必须与 JS 插件 package.json 的 name 完全一致 -->
            <meta-data android:name="dsh.plugin.jsPackage"
                android:value="dsh-plugin-my-plugin" />

            <intent-filter>
                <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

字段说明：

| meta-data | 含义 |
| --- | --- |
| `dsh.plugin.apiVersion` | SDK API 版本，当前 `1` |
| `dsh.plugin.minAppVersion` | 最低 App versionCode，当前示例 `12` |
| `dsh.plugin.id` | 插件 id，发布后不可改；组件 id 前缀必须与它一致 |
| `dsh.plugin.name` | 插件详情/原生插件列表展示名 |
| `dsh.plugin.jsPackage` | 配对的 JS 包名，App 用它在插件详情里归组 |

### 3.2 Service 入口

```kotlin
class MyPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(MyNativePlugin())

    companion object {
        @JvmStatic
        fun plugin(): DshUiPlugin = MyNativePlugin()
    }
}
```

App 用 DexClassLoader 把伴侣 APK 加载进自己的进程，再通过
`com.deepseek.dsh.action.PLUGIN_UI` 找到 Service；不需要跨进程通信。

### 3.3 插件入口类

```kotlin
class MyNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "your.plugin.id",
        name = "我的插件",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,
            PluginCapabilities.STORAGE,
            PluginCapabilities.PAGE_ENTRY,
            PluginCapabilities.TOOL_RENDER,
        ),
    )

    override fun createRenderer(): ComponentRenderer = MyRenderer()

    init {
        // 页面型 UI：在设置页/插件页注册入口
        PageRegistry.register(MySettingsPage())
        // 嵌入型 UI：在某个落点注册贡献
        SlotRegistry.register(MyComposerDockContribution())
    }
}
```

`descriptor.capabilities` 是白名单：调用未声明的能力会被 Host 拒绝。
能力常量见 `PluginCapabilities`（OPEN_URL / STORAGE / NOTIFY / SHARE /
CLIPBOARD / WORKSPACE / TOOL_RENDER / PAGE_ENTRY）。

---

## 4. 两种 UI 形态

### 4.1 页面型：`PageContent`（设置页 / 插件页）

适合完整管理页（订阅管理、账号设置）。注册后设置页「插件界面」或插件页出现入口，
点进去是整页原生 Compose，不再加载 WebView。

```kotlin
class MySettingsPage : PageContent {
    override val entry = PageEntry(
        id = "settings",
        title = "我的插件",
        subtitle = "账号与用量",
        placement = PageEntry.Placement.SETTINGS,
        order = 10,
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // 直接画页面；需要数据时通过 PluginRpcHost 拉取
        Text("Hello from native UI", color = theme.colorOnSurface)
    }
}
```

`PageEntry.Placement`：

- `SETTINGS`：设置页「插件界面」分组 → 整页原生设置页；
- `PLUGINS`：插件页 tab → 整页原生插件页。

### 4.2 嵌入型：`SlotContribution`（输入栏、消息、工具卡等）

适合局部增强（composer dock 胶囊、消息动作、工具卡片）。落点由 `NativeSlot`
声明，App 在每个位置放了宿主容器。

```kotlin
class MyComposerDockContribution : SlotContribution() {
    override val slot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes = setOf("my.usage-pill")
    override val mode = RenderMode.ENHANCE   // 或 OVERRIDE

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val text = spec["text"]?.jsonPrimitive?.contentOrNull ?: return
        Text(text, style = theme.textCaption, color = theme.colorOnSurfaceVariant)
    }
}
```

App 侧官方控件同样走 slot 通道：

- `ENHANCE`：官方默认内容保留，插件内容加在旁边/下方；
- `OVERRIDE`：插件接管该落点，官方默认隐藏；插件异常/卸载时自动回退官方 UI。

完整落点表见根目录 `slot-mapping.md`；当前 30 个官方 slot 全部有原生宿主。
如果注册了没有宿主的落点，贡献不会显示——先查 `docs/slot-inventory.md`。

### 4.3 通用 spec 渲染器（可选）

`ComponentRenderer` 用于把 Host 下发的 JSON spec 画成通用组件；页面和落点也可以
直接使用它：

```kotlin
class MyRenderer : ComponentRenderer {
    override val supportedTypes = setOf("my.card")
    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        Card(colors = CardDefaults.cardColors(containerColor = theme.colorSurface)) {
            Text(spec["title"]?.jsonPrimitive?.content ?: "", style = theme.textTitle)
        }
    }
}
```

`supportedTypes` 是降级开关：App 只对命中的类型走原生渲染，未命中回退插件 Web UI。

---

## 5. RPC：原生 UI 怎么读写 Host

原生页面通过 `PluginRpcHost` 调 Host 插件自己的路由（例如
`subscription-oauth.status`、`subscription-oauth.login`）。

```kotlin
class MySettingsPage : PageContent {
    // ...
    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val rpc = host as? PluginRpcHost
        var text by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            rpc?.callRpc("my-plugin.status", JsonObject(emptyMap())) { result ->
                text = result.getOrNull()?.toString() ?: "读取失败"
            }
        }
        Text(text)
    }
}
```

约定：

- 方法名由 JS 插件注册，App 不新增协议；
- 返回值是 Host 的 RPC 信封：`result.value` 是插件自己的 JSON；
- 失败时回调 `Result.failure`，原生 UI 应显示可读错误，不要静默；
- Host 需要用 `client/refresh` / bridge `dispatch` 推送状态变化，页面据此重读；
- 长耗时操作（OAuth 轮询）不要在 Compose 组合里做，交给 Host。

---

## 6. 主题与能力

所有颜色/字号/圆角从 `PluginTheme` 取，不要硬编码：

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

`PluginStorage` 只能存展示偏好（如“上次选中的 tab”），**不能存 token/secret**。

---

## 7. 安装与联调（真实流程）

### 7.1 先装 JS 插件

App → 抽屉 → **Plugin Market & Ecosystem → 插件 → 添加插件**，输入 npm 包名
（例如 `dsh-plugin-subscription-oauth`）或 GitHub 地址，安装并启用。

安装后 Host 启动时会：

- 把插件挂进 profile；
- 扫描已安装插件包中声明的 HTTPS 域名并写入 hosts（无需手工加域名）；
- 插件首次创建数据目录失败时，App 会从错误中解析路径并补建后重试（proot 兼容）。

### 7.2 再装伴侣 APK

```bash
# 在 App 仓库中构建示例
./gradlew :samples:kimi-native:assembleDebug

# 安装到设备
adb install -r samples/kimi-native/build/outputs/apk/debug/kimi-native-debug.apk
```

也可以直接安装你自己的伴侣 APK。安装/卸载后回到 App 前台会触发重扫
（App `onResume` + PackageManager 广播）。

### 7.3 验证

1. **插件页 → 原生插件**：能看到伴侣条目；
2. **插件详情**：显示「包含的组件」及组件数量；`kimi-oauth`/`subscription`
   应该显示为「原生」且开关打开、运行状态正常；
3. **设置页**：出现 `Subscriptions`（或你的 PageEntry 标题）入口，点进去是原生页面；
4. **输入栏**：`COMPOSER_DOCK` 等落点出现插件内容；
5. 未安装 APK 时，同一插件回退 Web UI。

App 不会主动“发现”APK 内部页面：必须同时满足
`jsPackage` 匹配 + 插件已启用 + `apiVersion`/`minAppVersion` 兼容。

### 7.4 调试

```bash
# App 日志
adb logcat | grep -E "NativeHost|DshPlugin|plugin"

# 插件页的运行状态/组件数量是第一个检查点
# 如果插件详情「包含的组件」为 0：
#   - dsh.plugin.jsPackage 与 JS package.json name 不一致
#   - 插件被禁用
#   - APK 安装后未触发重扫（切后台再回前台）
```

原生页面 RPC 报错时，先看 Host 日志：

```bash
adb shell run-as com.dsh.mobile ls -t files/logs
adb shell run-as com.dsh.mobile cat files/logs/host_<最新>.log
```

---

## 8. 当前 App 已有的 proot 兼容（不需要插件处理）

- **插件数据目录自愈**：RPC 返回 `ENOENT/ENOSYS ... mkdir '<path>'` 时，App 会
  在 rootfs 内补建目录并自动重试一次，适用于所有插件；
- **插件域名预解析**：Host 启动时扫描已安装插件包里的 `https://` 域名并写 hosts；
- **会话目录转义**：会话落盘目录会把工作区非 ASCII 字符编码成 `~XXXX`，App 按同
  规则预建，避免 Host mkdir 失败；
- **bash 输出兼容**：proot 下 bash 结果可能缺少 cwd，runtime 补丁允许该字段缺失，
  工具结果不会因 schema 校验失败。

---

## 9. 示例

- `samples/kimi-native`：`COMPOSER_DOCK` 用量胶囊 + OAuth 授权码展示；
- `samples/subscription-native`：设置页整页订阅管理（账号、模型、额度）。

在 App 仓库中构建：

```bash
./gradlew :samples:kimi-native:assembleDebug
./gradlew :samples:subscription-native:assembleDebug
```

示例只保留 `src/main`；Gradle 构建脚本使用 App 仓库的版本目录。

---

## 10. 发布前检查清单

- [ ] `dsh.plugin.id` 稳定且全局唯一，组件 id 前缀一致；
- [ ] `dsh.plugin.jsPackage` 与 JS `package.json` 的 `name` 完全匹配；
- [ ] `dsh.plugin.apiVersion = 1`，`minAppVersion` 不低于目标 App；
- [ ] 所有网络/凭证逻辑留在 Host，APK 内不出现 token/API key；
- [ ] 只使用 `PluginTheme` 的颜色/字号/圆角；
- [ ] 已检查目标落点是否在 `slot-mapping.md` 中有宿主；
- [ ] 未安装 APK 时 Web UI 仍完整可用；
- [ ] 原生页面有加载/空/错误状态，RPC 失败不静默；
- [ ] 在深色模式、大字号、横屏下自查布局；
- [ ] 跑过 App 的 `SlotCoverageTest`（App 侧新增落点时）。

---

## 11. 版本

| 项 | 值 |
| --- | --- |
| SDK API | v1 |
| App 插件 API | `dsh.plugin.apiVersion = 1` |
| 最低 App 版本 | `dsh.plugin.minAppVersion = 12` |
| Kotlin | 2.1.10 |
| Compose BOM | 2025.02.00 |
| kotlinx-serialization | 1.8.0 |
| minSdk | 26 |

不兼容变更会提升 `apiVersion`；App 按 APK 声明的版本加载，不匹配时回退 Web UI。

---

## 12. 先扫描插件源码，再写原生 UI（推荐第一步）

完整流程与数据契约见 **`docs/source-driven-native-ui.md`**。第三方开发者不需要 DSH
官方仓库，也不需要等用户截图。SDK 包自带插件源码扫描器：

```bash
python tools/plugin_ui_scanner.py \
  --plugin /path/to/your-plugin \
  --companion /path/to/native-companion/src \
  --write docs/plugin-ui.md \
  --json docs/plugin-ui.json \
  --check
```

传入 `--companion` 后，扫描器会同时检查原生伴侣源码是否真的注册了对应
`NativeSlot`/`PageContent`；插件有、伴侣没实现的 UI 会直接列出来并让 `--check` 失败。

它会优先读取插件包里的 `lib/*.js.map`（内嵌原始 TSX），没有 map 时读 `src/*.tsx`，
最后回退到编译后的 `lib/client.js`，输出：

- 注册的 slot / entry / order / component；
- 每个 slot 对应的 `NativeSlot`、是否已有原生宿主；
- 组件使用的 RPC 端点、`useProjection` / `useChat` 数据；
- JSX 组件树、可见文案、按钮动作、locale 文案；
- 未映射或没有宿主的位置（`--check` 退出码 1）。

示例输出见 `docs/plugin-ui-inventory-example.md`（订阅插件：4 个落点、24 个 RPC）。

按报告映射到原生实现：

| 扫描结果 | 原生接入方式 |
| --- | --- |
| `settings.section` 页面 | `PageContent` + `PageRegistry` |
| `conversation.composer.dock` 条目 | `SlotContribution`（`COMPOSER_DOCK`） |
| 官方控件（输入栏、dock 读数） | `OfficialSlot` 包装，可单项 ENHANCE/OVERRIDE |
| 信息型卡片/列表 | `SpecSlotContribution` + dsh-ui spec |
| `rpc:*` / `useProjection` | `PluginRpcHost.callRpc`，不新增 Host 协议 |

扫描结果中的 `settings.models.provider-card` 这类没有映射的 slot，会直接列为缺口，
由 App 补宿主或标记 Web 回退，不再依赖人工发现。

浮层（popover）必须使用 SDK `PluginPopover` 和 `PluginTheme` 的 popup 材质字段，
贴着触发元素显示，不要用居中 `Dialog` 或自造 Material Card；详见
`docs/source-driven-native-ui.md` 第 4 节。

App 开发仓库另有 `tools/official_ui_inventory.py`，用于锁定官方 slot/entry 覆盖；
第三方环境可 `--rootfs <已安装 rootfs>` 运行，同样不需要 upstream 目录。

截图只用于视觉验收，不再作为发现 UI 的来源。
