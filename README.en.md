> English | [中文](README.md)

# DSH Mobile Native UI SDK

> Repository: <https://github.com/EricFL1998/dsh-mobile-plugin-sdk>  
> Online docs: <https://ericfl1998.github.io/dsh-mobile-plugin-sdk/> (GitHub Pages)
>
> Add a **Kotlin/Compose native UI companion APK** to an existing DSH JS plugin: the JS side stays untouched,
> and once the App detects the companion APK it replaces the plugin UI from a WebView with a native screen;
> if the APK is not installed, the Web UI keeps working.
>
> SDK API v1 · Target App: `com.dsh.mobile` · minSdk 26 · compileSdk 35 · Kotlin 2.1.10.

---

## 0. What this is, and how the work is split

A plugin consists of two artifacts:

| Artifact | Location | Owner |
| --- | --- | --- |
| JS plugin package | Host (proot/Node on the phone) | Already owned by the plugin author |
| Companion APK | Inside the App process, loaded via Dex | Added by this document |

Responsibility boundaries:

1. **Logic always lives in the Host**: auth state, network requests, model calls and data queries all stay in
   `lib/index.js`; the native APK only draws UI and returns user intent.
2. **Credentials never leave the Host**: the APK never touches tokens or API keys; when it needs data it asks
   the Host over RPC.
3. **Native is an enhancement, not a requirement**: without the APK the plugin keeps using the Web UI; only when
   it is installed are native pages/slots enabled.
4. **Rendering is stateless**: Compose only consumes the data returned by the Host and never opens a network
   connection.

Native UI reads data along one path:

```
Native page starts
  → PluginRpcHost.callRpc("demo.status", {})
  → Host plugin returns { result: { ok: true, value } }
  → Compose renders with value
```

Writes go along a different path (business state is never mutated inside the APK):

```
User taps a native button
  → host.dispatchEvent("dsh-plugin-demo#demo", "submit", { actionId: "bump", by: 1 })
  → bridge hits the JS plugin's mobileUi action
  → Host state changes, bridge queues client/refresh
  → native page re-reads RPC after receiving refreshEvents
```

The **Demo plugin** in this repo is the only example, and both the JS and the native half are complete and
runnable:

| Item | Value |
| --- | --- |
| JS package | `dsh-plugin-demo` (0.1.0) |
| Companion `pluginId` (manifest `dsh.plugin.id`) | `demo.native-ui` |
| Companion APK package name | `com.dsh.mobile.dsh_plugin_demo` |
| manifest `dsh.plugin.jsPackage` | `dsh-plugin-demo` |
| Settings page component identity `componentId` | `dsh-plugin-demo#demo` |
| dock component identity `componentId` | `dsh-plugin-demo#demo-dock` |

Suggested reading order:

1. This README: wiring the AAR, manifest, pages/slots, RPC, build and release;
2. [`demo/README.md`](demo/README.md): Demo plugin identity, contract, build and on-device verification;
3. [`docs/plugin-sdk-guide.en.md`](docs/plugin-sdk-guide.en.md): the complete development spec;
4. [`docs/source-driven-native-ui.md`](docs/source-driven-native-ui.md): scan first, then implement;
5. [`docs/publishing.md`](docs/publishing.md): publishing, tagging and signing conventions.

---

## 1. Directory layout

```
dsh-mobile-plugin-native-ui-sdk/
├─ README.md                          ← Chinese README (this file: README.en.md)
├─ slot-mapping.md                    ← built-in slot → NativeSlot → host location
├─ demo/
│  ├─ README.md                       ← Demo plugin (JS + native) integration notes
│  ├─ js/                             ← JS half: dsh-plugin-demo 0.1.0
│  │  ├─ package.json
│  │  ├─ cordis.patch.yml
│  │  ├─ lib/index.js                 ← plugin entry: name / apply
│  │  ├─ lib/constants.js             ← RPC prefix, componentId, settings ns
│  │  ├─ lib/state.js                 ← in-process state (count 42)
│  │  ├─ lib/rpc.js                   ← /api/demo.* routes + envelope
│  │  ├─ lib/actions.js               ← mobileUi actions: bump / reset
│  │  ├─ lib/catalog.js               ← registration list returned by demo.catalog
│  │  ├─ lib/client.js                ← Web half: settings.section + dock
│  │  ├─ locale/{zh,en}.json
│  │  └─ test/rpc.test.mjs
│  └─ native/                         ← native half: demo.native-ui
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
│  ├─ plugin-sdk-guide.md             ← development and debugging steps
│  ├─ native-plugin-sdk.md            ← plugin-side contract
│  ├─ native-plugin-host.md           ← App-side host containers and registries
│  ├─ plugin-conversion-guide.md      ← migrating Web UI to native UI
│  ├─ source-driven-native-ui.md      ← scan-driven integration flow
│  ├─ mobile-bridge.md                ← Host ↔ App bridge protocol
│  ├─ publishing.md                   ← publishing and tagging
│  ├─ pitfalls.md                     ← checklist and best practices
│  ├─ slot-inventory.md               ← App built-in slot coverage list
│  ├─ plugin-ui-inventory-example.md  ← scan output for the Demo plugin (plus .json)
│  ├─ index.html                      ← GitHub Pages site
│  └─ .nojekyll
├─ reference/native-host/             ← App-side host reference (NativeHost / NativeSlotHost / SlotPopup)
├─ sdk/                               ← SDK sources and standalone build configuration
│  ├─ README.md
│  ├─ build.gradle.kts
│  ├─ settings.gradle.kts
│  ├─ gradle.properties
│  └─ src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/
├─ tools/
│  ├─ plugin_ui_scanner.py            ← plugin source scanner
│  └─ sdk-mapping.json                ← built-in slot mapping / host snapshot (scanner runs standalone)
└─ artifacts/
   └─ dsh-native-ui-sdk-release.aar   ← prebuilt SDK AAR
```

> The repo does not package real business plugins; `demo/` is a fictional case whose code runs as-is.

---

## 2. Adding the dependency

### 2.1 Using the AAR (recommended)

Put [`artifacts/dsh-native-ui-sdk-release.aar`](artifacts/dsh-native-ui-sdk-release.aar) into the `libs/`
directory of the companion APK project, then declare the dependency:

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

The AAR does not carry transitive dependencies; Compose and the serialization library are declared by your
companion project. `build.gradle.kts` also needs:

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

### 2.2 Using the source

Copy the sources under [`sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/`](sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/)
into your library module (keeping the package name). Source and AAR are equivalent.

`sdk/` is itself a standalone root project; for the build see [`sdk/README.md`](sdk/README.md): open that
directory in Android Studio, or run `gradle assembleRelease` with Gradle 8.11+ (compatible with AGP 8.9).
**The SDK repo ships no Gradle Wrapper** — do not run `./gradlew` at the root of this repo.

### 2.3 Toolchain versions

| Item | Value |
| --- | --- |
| SDK API | v1 (`dsh.plugin.apiVersion = 1`) |
| compileSdk | 35 |
| minSdk | 26 |
| JDK | 17 |
| Kotlin | 2.1.10 |
| Compose BOM | 2025.02.00 |
| kotlinx-serialization | 1.8.0 |

---

## 3. Minimal companion APK implementation

### 3.1 Package name convention (mandatory)

> **One JS plugin = one companion APK.** This includes Web-injected overlays: write a companion for those too,
> draw the overlay through `NativeSlot.PLUGIN_OVERLAY`, and do not add a dedicated container type in the App for
> a single plugin. The host only provides a full-screen safe-area canvas; positioning, dragging, snapping and
> animation are the companion's own responsibility.

The companion APK's `applicationId` and `namespace` always use:

```
com.dsh.mobile.<full plugin name>
```

"Full plugin name" is the `name` field of the JS `package.json`, **keeping the `dsh-plugin-` prefix**, lowercased,
with every non-`[a-z0-9]` character replaced by `_`:

| Full JS plugin name | Companion package name |
| --- | --- |
| `dsh-plugin-demo` | `com.dsh.mobile.dsh_plugin_demo` |
| `dsh-plugin-my-plugin` | `com.dsh.mobile.dsh_plugin_my_plugin` |
| `dsh-genui` | `com.dsh.mobile.dsh_genui` |

Rule details:

- Do not abbreviate away the `dsh-plugin-` prefix; it is part of the plugin name;
- When one JS package has several capability companions, the main companion uses the full name and extra
  capabilities append `_<capability>`;
- `dsh.plugin.id` is the plugin identity, decoupled from the package name; use your own reverse-domain id;
- Once released, never change the package name or `dsh.plugin.id`, otherwise the system or the App will treat it
  as a different plugin.

### 3.2 AndroidManifest

The Demo's complete manifest ([`demo/native/src/main/AndroidManifest.xml`](demo/native/src/main/AndroidManifest.xml)):

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

All `dsh.plugin.*` meta-data entries go at **Service level**, not at `<application>` level:

| meta-data | Meaning |
| --- | --- |
| `dsh.plugin.apiVersion` | SDK API version, currently `1` |
| `dsh.plugin.minAppVersion` | Minimum App versionCode; the Demo uses `100` |
| `dsh.plugin.version` | Companion APK descriptor version, an integer |
| `dsh.plugin.id` | Plugin id (`demo.native-ui`), immutable after release |
| `dsh.plugin.name` | Display name in plugin details / the native plugin list |
| `dsh.plugin.jsPackage` | Paired JS package name; must exactly match the `name` in `package.json` |

### 3.3 Service entry point

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

The App loads the companion APK into its own process through `packageContext.classLoader`, then finds the
Service via `com.deepseek.dsh.action.PLUGIN_UI`; no cross-process communication is needed.

### 3.4 Plugin entry class

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

`descriptor.capabilities` is a whitelist: calling a capability you did not declare is rejected by the Host. The
capability constants live in `PluginCapabilities` (OPEN_URL / STORAGE / NOTIFY / SHARE / CLIPBOARD / WORKSPACE /
TOOL_RENDER / PAGE_ENTRY).

---

## 4. Pages and slots

Native UI comes in two shapes; choose by "whole page / local area".

### 4.1 Page type: `PageContent` (settings page / plugin page)

Suited to a complete management page (account, usage, configuration). Once registered, an entry appears under
"Plugin interfaces" on the settings page or on the plugin page, and opening it shows a full-page native Compose
screen instead of loading a WebView.

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

`PageEntry.Placement`:

- `SETTINGS`: the "Plugin interfaces" group on the settings page → a full-page native settings page;
- `PLUGINS`: a tab on the plugin page → a full-page native plugin page.

Two conditions for the settings page to be taken over natively:

1. The JS side registers `settings.section` with `id = "demo"`;
2. The companion APK loads through the static factory and registers `PageEntry(id = "demo", placement = SETTINGS)`.

Both share the component identity `dsh-plugin-demo#demo` (`<jsPackage>#<PageEntry.id>`), and only then does the
App skip the WebView. If the APK is installed but no page is registered, the Web path is still used.

### 4.2 Embedded type: `SlotContribution` (input bar, messages, tool cards, etc.)

Suited to local enhancements (composer dock chip, message actions, tool cards). The slot is declared by
`NativeSlot`, and the App places a host container at every location; when no plugin registers, the container
takes no space.

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

Render modes:

- `ENHANCE`: the built-in default content stays and plugin content is added beside/below it;
- `OVERRIDE`: the plugin takes over the slot and the built-in default is hidden; on plugin failure or removal the
  built-in UI is restored automatically.

The Demo covers 22 `NativeSlot` entries, 21 of which inject real UI; `WEB_ISLAND_FULL` is the full-island Web
fallback container, which the Demo does not inject (it would cover the App full-screen) and only documents on
its page:

| NativeSlot | Location | Demo |
| --- | --- | --- |
| `SETTINGS_SHEET` | Settings page / plugin page entry and full page | Injected |
| `SETTINGS_PROVIDER_CARD` | Provider card | Injected |
| `SIDEBAR_PANEL` | Left sidebar panel | Injected |
| `RIGHT_PANEL` | Chat right sidebar (fullscreen below 768px) | Injected |
| `COMPOSER_DOCK` | Input bar dock | Injected (`Demo 42` chip + popover) |
| `COMPOSER_DOCK_ACTIVITY` | dock activity (tok/s entry) | Injected |
| `COMPOSER_DOCK_USAGE` | dock usage (cache hit entry) | Injected |
| `COMPOSER_DOCK_CONTEXT` | dock context (context metering) | Injected |
| `COMPOSER_LEFT` | Left of the input bar | Injected |
| `COMPOSER_RIGHT` | Right of the input bar | Injected |
| `COMPOSER_MODEL` | Model selection | Injected |
| `COMPOSER_PERMISSION` | Permission selection | Injected |
| `COMPOSER_PLAN` | Plan mode | Injected |
| `COMPOSER_ATTACHMENTS` | Attachments entry | Injected |
| `COMPOSER_BAR` | The whole composer | Injected |
| `CONVERSATION_VIEW` | Conversation view | Injected |
| `SESSION_HEADER_CORNER` | Conversation title corner | Injected |
| `COMPOSER_OVERLAY` | Input overlay | Injected |
| `PLUGIN_OVERLAY` | Plugin overlay | Injected |
| `MESSAGE_ACTION` | Message actions | Injected |
| `TOOL_VIEW_SHEET` | Tool details | Injected |
| `CHAT_STREAM` | Chat stream | Injected |
| `WEB_ISLAND_FULL` | Full-island Web fallback container | Not injected (host is ready) |

The complete mapping from built-in slots to `NativeSlot`, and the host locations, are in
[`slot-mapping.md`](slot-mapping.md). A contribution registered for a slot without a host is never shown; check
[`docs/slot-inventory.md`](docs/slot-inventory.md) first.

### 4.3 Overlays: `PluginPopover`

The native counterpart of an anchored overlay on the Web side is `PluginPopover`: it is shown attached to the
triggering element, with material taken from the `PluginTheme` popup fields (surface color, hairline border,
corner radius, menu shadow). Do not use a centered `Dialog` or a hand-rolled Material Card. The Demo's dock chip
uses exactly this.

### 4.4 Generic renderer: `ComponentRenderer`

`ComponentRenderer` turns a JSON spec sent by the Host into generic components; pages and slots can use it
directly as well:

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

`supportedTypes` is the fallback switch: the App renders natively only for matching types, and falls back to the
plugin's Web UI for everything else.

---

## 5. RPC and events

Native pages call the Host plugin's own routes through `PluginRpcHost` (for the Demo, `demo.*`). Method names are
registered by the JS plugin; the App adds no new protocol.

### 5.1 Envelope contract

Request body (`payload` is passed through as-is and is not wrapped in `args`):

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

Success response:

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

Business failures are also **HTTP 200**, with the error in `result.error`:

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

Only transport-level errors use non-200 statuses: `400` (JSON cannot be parsed or the envelope does not match)
and `415` (not `application/json`). The native side decodes the envelope first and then branches on `code`;
never match on message text.

### 5.2 The Demo's 6 endpoints

| Endpoint | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | The updated status snapshot |
| `demo.reset` | `{}` | The status snapshot after reset |
| `demo.echo` | Any JSON | `{ received: <original echoed back> }` |

Error codes: `demo/bad-request` (400), `demo/unsupported-media-type` (415),
`demo/unknown-endpoint` (HTTP 200 + `result.error`).

### 5.3 Native reads: `PluginRpcHost.callRpc`

`callRpc` is a callback-style API. The Demo wraps it as a `suspend` function with a top-level extension shared by
pages and slots:

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

`DemoRpc.value()` extracts `result.value`, and `DemoRpc.errorMessage()` yields a readable error on business
failure.

### 5.4 Native writes: `dispatchEvent` → `mobileUi` action → `client/refresh`

Native controls do not execute plugin JS; they only report "which component, which action, what payload":

```kotlin
val payload = buildJsonObject {
    put("actionId", "bump")
    put("by", 1)
}
host.dispatchEvent(DemoRpc.PAGE_COMPONENT_ID, "submit", payload) // dsh-plugin-demo#demo
```

The complete chain:

```
host.dispatchEvent(componentId, "submit", payload)
  → App bridge calls client-slots/dispatch
  → JS plugin mobileUi action (demo/js/lib/actions.js)
  → Host state changes, bridge queues client/refresh
  → native page re-reads RPC after receiving refreshEvents
```

Pages filter refresh events by component identity and only re-fetch the components they care about:

```kotlin
LaunchedEffect(refreshEvents) {
    refreshEvents?.collect { componentId ->
        if (componentId == DemoRpc.PAGE_COMPONENT_ID || componentId == DemoRpc.JS_PACKAGE) refresh()
    }
}
```

Event names are constrained by the bridge to `press` / `input` / `toggle` / `submit`; the Demo uses `submit`
throughout. Pure write operations can also go straight through RPC (the Demo's "Reset" is `demo.reset`).

### 5.5 Best practices

- Component identity must be the `<jsPackage>#<id inside the slot>` that JS actually registered; if it is wrong,
  the bridge cannot find the action;
- Before updating the page from an async callback, check `PageLifecycle.LocalIsActive.current` so that results
  arriving after the user went back do not write into a page that is already gone;
- The native side only displays presentation data from `result.value`; it never maintains business state itself;
- Keep long-running work in the Host; do not make blocking calls inside Compose composition;
- Failures need readable errors and a retry entry point; never fail silently;
- Back off and retry while the Host is not ready on first paint (the Demo dock uses 800ms / 2s), then re-read on
  `client/refresh` or on a periodic poll.

---

## 6. Theme and capabilities

All colors, font sizes and corner radii come from `PluginTheme`; nothing is hardcoded:

```kotlin
val theme = host.currentTheme()
Text(
    text = "标题",
    style = theme.textTitle,
    color = theme.colorOnSurface,
    modifier = Modifier.padding(theme.spacingUnit),
)
```

Check host capabilities with `host.requestCapability(...)` before calling them, and degrade when they are
undeclared or disabled by the user:

```kotlin
if (host.requestCapability(PluginCapabilities.OPEN_URL)) {
    host.openUrl(url)
}
```

### Settings page panels: DshPanel

Do not use Material `Card` directly for settings/details pages; use the SDK's `design/DshPanel.kt`, which shares
the App's built-in settings page look — same background, 0.5dp hairline border, 16dp corner radius, 44dp row
height and 11sp section title:

```kotlin
DshPanel(theme = theme, title = "账号") {
    DshSectionLabel("订阅", theme)
    DshPanelRow(theme, title = "自动续费", value = "已开启", showDivider = true)
    DshPanelRow(theme, title = "管理订阅", onClick = { /* openUrl */ })
    DshPanelAction("查看全部", theme) { /* ... */ }
}
```

Every color comes from `PluginTheme` (`colorPanel` / `colorPanelBorder` / `colorLabelPrimary` /
`colorLabelSecondary` / `colorLabelTertiary` / `colorLink`), provided by the host for the current light/dark
theme; plugins hardcode no color values and write no light/dark branches. For the full component list, spacing,
button hierarchy and acceptance items, see [`docs/ui-guideline.en.md`](docs/ui-guideline.en.md).

`PluginStorage` may only hold presentation preferences (such as "last selected tab"), and **must never store
tokens or secrets**.

---

## 7. Degradation

The native layer is always an enhancement, never a requirement:

| Scenario | Behavior |
| --- | --- |
| User has not installed the companion APK | The plugin uses the Web UI (full interface), fully functional |
| APK installed but ABI incompatible | Shows "plugin needs an update" and falls back to the Web UI |
| `supportedTypes` does not match the spec type | That component degrades; the rest stay native |
| Renderer throws | Caught by the error fence, shows a placeholder block, does not affect the App or other plugins |
| User uninstalls the APK | The render slot is removed immediately and the Web UI is restored |

**Therefore**: JS-side functionality must be fully usable on plain Web and must not assume the native side is
present. The Demo's Web fallback is in [`demo/js/lib/client.js`](demo/js/lib/client.js): it registers
`settings.section(id = "demo")` and `conversation.composer.dock(id = "demo-dock")`, and uses
`connection.rpc.call("/api", "demo.status")` to read the same Host state.

---

## 8. Scan the plugin source first, then write native UI (recommended first step)

The scanner reads the plugin's own source and produces slots / entries / components / RPC / UI tree plus gaps; it
needs neither screenshots nor the App repo. The full flow is in
[`docs/source-driven-native-ui.md`](docs/source-driven-native-ui.md).

```bash
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

Notes:

- `--plugin` points at the plugin package root; it prefers `lib/*.js.map` (with embedded original TSX), then
  `src/*.tsx`, and finally falls back to the compiled `lib/client.js`;
- `--companion` makes the scanner verify that the companion source really registers the matching
  `NativeSlot` / `PageContent`;
- The scanner ships with its own [`tools/sdk-mapping.json`](tools/sdk-mapping.json) snapshot (built-in slot
  mapping and host coverage), so it runs directly from the SDK repo root;
- To cross-check the host sources in the App repo, add the optional `--root <App repo>`; daily integration does
  not need it.

The Demo's scan output is in [`docs/plugin-ui-inventory-example.md`](docs/plugin-ui-inventory-example.md):
both registered slots are mapped, the host is ready, the companion is implemented, and `--check` passes.

```text
$ python tools/plugin_ui_scanner.py --plugin demo/js \
    --companion demo/native/src/main/kotlin --check
plugin UI coverage check OK
```

Mapping the report to a native implementation:

| Scan result | Native integration |
| --- | --- |
| `settings.section` page | `PageContent` + `PageRegistry` |
| `conversation.composer.dock` entry | `SlotContribution` (`COMPOSER_DOCK`) |
| Built-in controls (input bar, dock readouts) | `OfficialSlot` wrapper, ENHANCE/OVERRIDE per item |
| Informational cards/lists | `SpecSlotContribution` + dsh-ui spec |
| Plugin RPC / `useProjection` | `PluginRpcHost.callRpc`, no new Host protocol |

Consistency between built-in slots and the host is maintained by the `tools/sdk-mapping.json` snapshot and the
App-side `SlotCoverageTest`; see [`docs/source-driven-native-ui.md`](docs/source-driven-native-ui.md) for details.

---

## 9. Building and debugging the Demo

### 9.1 Prerequisites

- The App development repo (which provides the Gradle Wrapper, version catalog and the `:plugin:native-sdk` module);
- An Android device with the App installed and USB debugging enabled;
- Node.js ≥ 20 (to run the Demo's JS self-tests);
- Python 3 (to run the scanner).

The SDK repo itself has no Gradle Wrapper; `demo/native` is built inside the App repo.

### 9.2 Building the companion APK

Copy `demo/native` into the App repo as `samples/demo-native` and add this to the App repo's
`settings.gradle.kts`:

```kotlin
include(":samples:demo-native")
```

Then run, from the **App repo root**:

```bash
./gradlew :samples:demo-native:assembleDebug
```

Output:

```text
samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk
```

`demo/native/build.gradle.kts` uses the App repo's version catalog and depends on the App's `:plugin:native-sdk`
module; when building for your own plugin, use the AAR from section 2 or the `sdk/` sources instead.

### 9.3 Installing

```bash
# 1) JS 插件安装到 Host profile（本地路径）
dsh plugin --profile web add ./demo/js

# 2) 安装伴侣 APK
adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk
```

Returning to the foreground after installing/uninstalling an APK triggers a rescan (`onResume` + PackageManager
broadcast).

### 9.4 Self-tests

```bash
# RPC / 动作 / 状态隔离（无需 Host、Android、网络）
node --test demo/js/test/rpc.test.mjs

# 扫描器：JS 注册的 2 个落点都有 NativeSlot 映射、宿主与伴生实现
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

### 9.5 On-device verification

1. **Plugin page → Native plugins**: the companion entry is visible;
2. **Plugin details**: shows "Included components" and the component count; the Demo's two components show as
   "Native" with their switches on;
3. **Settings page**: a "Demo plugin" entry appears, and opening it shows the native page with the count, last
   action and update time;
4. **Input bar**: the dock shows the `Demo 42` chip; tapping opens a popup attached to the chip that uses the App
   menu material;
5. **Write path**: tapping "Server +1" changes the count; tapping "Reset" returns it to 42;
6. **Fallback**: after uninstalling the Demo APK, the settings page and dock return to the Web version and can
   still read `demo.status`.

### 9.6 Debugging

```bash
# App 日志
adb logcat | grep -E "NativeHost|DshPlugin|plugin"

# Host 日志
adb shell run-as com.dsh.mobile ls -t files/logs
adb shell run-as com.dsh.mobile cat files/logs/host_<最新>.log
```

If "Included components" in the plugin details shows 0, check in order:

- whether `dsh.plugin.jsPackage` exactly matches the `name` in the JS `package.json`;
- whether the plugin is enabled;
- whether installing the APK triggered a rescan (send the App to the background and back).

When RPC fails, look at the Host logs and the `result.error.code` in the envelope first.

---

## 10. Releasing

### 10.1 Release artifacts

| Artifact | Where | Consumed by |
| --- | --- | --- |
| JS plugin | npm (`dsh-plugin-*`) | The Host's `dsh plugin add` / "Add plugin" on the plugin page |
| Companion APK | GitHub Releases (`.apk` asset) | The plugin store and the system installer |
| SDK AAR (the SDK repo itself) | GitHub Releases (`.aar` asset) | Plugin authors building companion APKs |

A plugin may ship JS only, or JS + APK as a pair. When shipping only an APK, it must point to an installable JS
plugin through `dsh.plugin.jsPackage`.

### 10.2 Release example for the Demo

- JS package: `dsh-plugin-demo` (npm, 0.1.0);
- Companion APK: `dsh-plugin-demo-companion-1.0.0.apk`, package name
  `com.dsh.mobile.dsh_plugin_demo`, `dsh.plugin.id = demo.native-ui`;
- tag / Release: `v1.0.0`;
- Signing: debug signing is fine for test builds; switch to a release keystore before public distribution.

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

Conventions:

- Tags use `v<js-version>`; when only the companion APK is updated you may use `v<js-version>-native`, e.g.
  `v0.1.0-native`;
- The store only reads the **latest release**, so do not publish it as a prerelease;
- A Release needs at least one `.apk` asset, and the asset name must end with `.apk`;
- For updates, use a new tag + new Release; never overwrite existing assets;
- The SDK repo itself uses a prefix such as `sdk-v1.0.0` to stay distinct from plugin versions.

### 10.3 Releasing the SDK AAR

`sdk/` is a standalone project and its build output lands in `sdk/build/outputs/aar/`:

```bash
# 1) 在 sdk/ 目录构建（Android Studio 或兼容 AGP 8.9 的 Gradle 8.11+）
gradle assembleRelease

# 2) 回到 SDK 仓库根目录，复制为固定文件名
cp sdk/build/outputs/aar/*-release.aar artifacts/dsh-native-ui-sdk-release.aar
```

Tagging and release:

```bash
git tag -a sdk-v1.0.0 -m "DSH Native UI SDK 1.0.0"
git push origin sdk-v1.0.0
gh release create sdk-v1.0.0 \
  artifacts/dsh-native-ui-sdk-release.aar \
  --title "SDK v1.0.0" \
  --notes "Native UI SDK AAR"
```

For the complete commands, version matrix, signing and update-notification rules, see
[`docs/publishing.md`](docs/publishing.md).

---

## 11. Pre-release checklist

- [ ] `dsh.plugin.id` is stable and globally unique, with consistent component id prefixes;
- [ ] `dsh.plugin.jsPackage` exactly matches the `name` in the JS `package.json`;
- [ ] `dsh.plugin.apiVersion = 1`, and `minAppVersion` is not lower than the target App;
- [ ] All network/credential logic stays in the Host; no token or API key appears inside the APK;
- [ ] Only `PluginTheme` colors, font sizes and corner radii are used;
- [ ] The target slot has a host, as listed in [`slot-mapping.md`](slot-mapping.md);
- [ ] The Web UI remains fully usable when the APK is not installed;
- [ ] Native pages have loading/empty/error states, and RPC failures are not silent;
- [ ] Layout has been checked in dark mode, large font sizes and landscape;
- [ ] The scanner `--check` passes (the plugin has it and the companion implements it);
- [ ] `node --test demo/js/test/rpc.test.mjs` passes.

---

## 12. Versions

| Item | Value |
| --- | --- |
| SDK API | v1 |
| App plugin API | `dsh.plugin.apiVersion = 1` |
| Minimum App version | `dsh.plugin.minAppVersion = 100` |
| Kotlin | 2.1.10 |
| Compose BOM | 2025.02.00 |
| kotlinx-serialization | 1.8.0 |
| minSdk | 26 |
| compileSdk | 35 |

Breaking changes bump `apiVersion`; the App loads according to the version declared by the APK and falls back to
the Web UI on mismatch.

---

## 13. Document index

| Document | Contents |
| --- | --- |
| [`demo/README.md`](demo/README.md) | Demo plugin identity, contract, build and verification |
| [`demo/js/README.md`](demo/js/README.md) | The Demo's JS half: RPC, actions, Web fallback |
| [`docs/plugin-sdk-guide.en.md`](docs/plugin-sdk-guide.en.md) | Complete native plugin development spec |
| [`docs/ui-guideline.en.md`](docs/ui-guideline.en.md) | Native UI design spec: DshPanel / DshButton / spacing / acceptance |
| [`docs/native-plugin-sdk.en.md`](docs/native-plugin-sdk.en.md) | Plugin-side SDK contract |
| [`docs/native-plugin-host.md`](docs/native-plugin-host.md) | App-side host containers and registries |
| [`docs/plugin-conversion-guide.md`](docs/plugin-conversion-guide.md) | Migrating Web UI to native UI |
| [`docs/source-driven-native-ui.md`](docs/source-driven-native-ui.md) | Scan-driven integration flow |
| [`docs/mobile-bridge.md`](docs/mobile-bridge.md) | Host ↔ App bridge protocol |
| [`docs/publishing.md`](docs/publishing.md) | Publishing, tags and signing |
| [`docs/pitfalls.md`](docs/pitfalls.md) | Integration checklist and best practices |
| [`docs/plugin-ui-inventory-example.md`](docs/plugin-ui-inventory-example.md) | Scan output for the Demo plugin |
| [`docs/slot-inventory.md`](docs/slot-inventory.md) | App built-in slot coverage list |
| [`slot-mapping.md`](slot-mapping.md) | Built-in slot → NativeSlot → host location |
| [`sdk/README.md`](sdk/README.md) | SDK sources and standalone build |
| [`reference/native-host/`](reference/native-host/) | App-side host reference implementation |
