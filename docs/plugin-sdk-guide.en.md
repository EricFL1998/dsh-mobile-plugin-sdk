> English | [中文](plugin-sdk-guide.md)

# DSH Native Plugin SDK Developer Guide

> Status: **Self-verified (Demo sample tests and scan pass; companion APK build steps are in [demo/README](../demo/README.md))**. This document is the complete development guide for native plugins (companion APKs).
> Audience: plugin developers who want to provide a native (Kotlin/Compose) UI for the DSH mobile App.
> Related docs: [JS UI to native UI practical guide](plugin-conversion-guide.md), [plugin-side SDK spec](native-plugin-sdk.en.md), [App-side host interfaces](native-plugin-host.md), [Demo plugin (JS half)](../demo/js/README.md), [Demo plugin directory](../demo/README.md), [Plugin UI inventory example](plugin-ui-inventory-example.md), [Docs site case](index.html#case).
> SDK dependency: `artifacts/dsh-native-ui-sdk-release.aar` (or the [sdk/ sources](../sdk/README.md)) · ABI version `1`.
> Updated: 2026-10-22 (all examples switched to the Demo plugin: `demo/js` + `demo/native`).
>
> **Self-verification**: the `demo/js` RPC / action tests pass 7/7; `tools/plugin_ui_scanner.py --check`
> prints `plugin UI coverage check OK` for the Demo plugin (both registered slots map to NativeSlot,
> the host is ready, the companion is implemented); for building `demo/native`, see [demo/README](../demo/README.md).

## Contents

- [0. What this is](#0-what-this-is)
- [1. Five-minute quick start](#1-five-minute-quick-start)
- [2. Core concepts](#2-core-concepts)
- [3. Package structure and manifest contract](#3-package-structure-and-manifest-contract)
- [4. Plugin entry and renderers](#4-plugin-entry-and-renderers)
- [5. Capability bridge PluginHostApi](#5-capability-bridge-pluginhostapi)
- [6. GenUI: rendering dsh-ui specs](#6-genui-rendering-dsh-ui-specs)
- [7. Slots: placing content in the App UI](#7-slots-placing-content-in-the-app-ui)
- [8. Tool call render hook](#8-tool-call-render-hook)
- [9. Page entries: settings page and plugin page](#9-page-entries-settings-page-and-plugin-page)
- [10. Theme contract](#10-theme-contract)
- [11. Communicating with Host-side JS](#11-communicating-with-host-side-js)
- [12. Degradation contract](#12-degradation-contract)
- [13. Release and store](#13-release-and-store)
- [14. Complete example: the Demo plugin (JS + native)](#14-complete-example-the-demo-plugin-js--native)
- [15. Component catalogue (dsh-ui spec reference)](#15-component-catalogue-dsh-ui-spec-reference)

---

## 0. What this is

The DSH mobile App exposes a **native UI plugin** system: you attach a **Kotlin/Compose renderer** to an existing (or new) JS plugin, package it as a standalone APK and distribute it through the store. Once installed, the plugin UI upgrades from a WebView to a native experience — faster rendering, more responsive interaction, and theming consistent with the system.

**Core promises**:

- **Zero logic rewrite**: your Host-side logic (`lib/index.js`: RPC, state, events) keeps running untouched. The native APK only writes "frontend logic" — drawing specs and returning intent;
- **Zero new protocols**: the native side talks to the Host through the existing bridge `dispatch` op and `client/refresh` events;
- **Degradation always works**: if the user does not install your APK, the plugin stays fully usable in the WebView; installing it enhances the experience.

**In one sentence**: the native APK is a second face of Host logic. Logic always lives in the Host.

All examples in this document come from the **Demo plugin** in the repo: the JS half is the npm package [`demo/js`](../demo/js) (`dsh-plugin-demo`), the native half is the companion APK [`demo/native`](../demo/native) (`demo.native-ui` / `com.dsh.mobile.dsh_plugin_demo`). It has no account, network or credentials and only demonstrates the integration shape of the SDK; real authorization-style plugins are out of scope here.

---

## 1. Five-minute quick start

```kotlin
// 1. 依赖 SDK：把 artifacts/dsh-native-ui-sdk-release.aar 放进伴侣 APK 工程的 libs/
// 在你的 Android 工程 build.gradle.kts 里：
dependencies {
    implementation(files("libs/dsh-native-ui-sdk-release.aar"))
}

// 2. 写一个渲染器（或直接用 SpecRenderer 画 dsh-ui spec）
// 3. 在 Service 填 manifest 契约与 jsPackage 配对
// 4. 打包成 APK，提交商店（或 CI 代编）
```

You can also copy the sources under `sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/**` into your own library module; the two approaches are equivalent, see [sdk/README.md](../sdk/README.md).

For a minimal usable plugin see [§14 Complete example: the Demo plugin (JS + native)](#14-complete-example-the-demo-plugin-js--native). To first get the repo's Demo running:

```bash
# RPC / 动作 / 状态隔离（无需 Host、Android、网络）
node --test demo/js/test/rpc.test.mjs

# 扫描器：JS 注册的 2 个落点都有 NativeSlot 映射、宿主与伴生实现
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

---

## 2. Core concepts

| Concept | Description |
| --- | --- |
| **Plugin (JS half)** | Your existing npm/GitHub plugin, whose `lib/index.js` runs in the Host. Logic, state and RPC all live here. |
| **Companion APK (native half)** | A standalone Android app containing your Compose renderer. It only draws UI and returns intent. |
| **pluginId** | The companion APK's own identity and page-registration owner, e.g. `demo.native-ui`; it need not equal the JS package name. |
| **jsPackage** | The package name paired with the JS plugin in the Host, e.g. `dsh-plugin-demo`; declared as `dsh.plugin.jsPackage` in the manifest. |
| **componentId** | The bridge's component identity, usually `<jsPackage>#<id inside the slot>`; it decides which Web page the native page takes over, e.g. `dsh-plugin-demo#demo`. |
| **spec** | A JSON state snapshot the Host pushes to the native side (presentation data only). Your renderer consumes it; the Demo's pages read data directly over RPC instead. |
| **Slot (NativeSlot)** | A place in the App UI where plugins can attach content (dock, beside the input box, tool cards, settings page…). |
| **Capability bridge (PluginHostApi)** | The only App surface a plugin can touch: return events, open links, read the theme, store data. |

---

## 3. Package structure and manifest contract

### 3.1 Project structure

```
demo/js/                 ← JS 插件（npm 包 dsh-plugin-demo，0.1.0）
├── package.json                 ← npm 身份 + dsh.client / dsh.native 声明
├── cordis.patch.yml             ← bundle 自激活：挂载 lib/index.js
├── lib/
│   ├── index.js                 ← plugin entry: name / apply (no top-level inject)
│   ├── constants.js             ← RPC prefix, componentId, settings ns (three-way convention)
│   ├── state.js                 ← in-memory state store (count 42, last action, update time)
│   ├── rpc.js                   ← /api/demo.* routes + request/response envelope
│   ├── actions.js               ← mobileUi actions: bump / reset
│   ├── catalog.js               ← registration list returned by demo.catalog
│   └── client.js                ← Web fallback: settings.section + composer.dock
└── test/rpc.test.mjs            ← node:test: envelope, routes, actions, state isolation

demo/native/             ← 伴生 APK 工程
├── build.gradle.kts             ← companion APK build configuration (see demo/README)
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/com/dsh/mobile/dsh_plugin_demo/
        ├── DemoPluginService.kt     ← Service binding + static factory plugin()
        ├── DemoNativePlugin.kt      ← DshUiPlugin: descriptor, renderer, slot registration
        ├── DemoSettingsPage.kt      ← PageContent: full settings page (RPC-driven)
        ├── DemoDockContribution.kt  ← COMPOSER_DOCK chip + PluginPopover
        ├── DemoSlotContribution.kt  ← demo contributions for the remaining NativeSlots
        └── DemoRpc.kt               ← envelope parsing + demoCall
```

### 3.2 AndroidManifest.xml (base contract + JS pairing)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.dsh.mobile.dsh_plugin_demo">

    <application>
        <!-- 入口服务：发现、ABI 校验、静态工厂加载及 JS 插件配对都在 service 级声明 -->
        <service
            android:name=".DemoPluginService"
            android:exported="true"
            android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">
            <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
            <meta-data android:name="dsh.plugin.minAppVersion" android:value="100" />
            <meta-data android:name="dsh.plugin.version" android:value="1" />
            <meta-data android:name="dsh.plugin.id" android:value="demo.native-ui" />
            <meta-data android:name="dsh.plugin.name" android:value="Demo 插件" />
            <!-- 与 demo/js 的 package.json name 一致；App 据此配对并连带安装 JS 插件。 -->
            <meta-data android:name="dsh.plugin.jsPackage" android:value="dsh-plugin-demo" />
            <intent-filter>
                <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

**Rules**:
- `dsh.plugin.id` is immutable once released — it anchors the signature pin and the store identity; it need not equal the JS package name (the Demo's `demo.native-ui` ≠ `dsh-plugin-demo`);
- `dsh.plugin.jsPackage` is the pairing key with the Host plugin; `PageEntry.id` decides which `settings.section` is taken over;
- All plugin meta-data entries go at **Service level**, not at `<application>` level;
- A plugin APK **requests no system permissions** (no `<uses-permission>`); capabilities are declared through the capability bridge;
- Only declare an extra Activity when the plugin genuinely needs a system round-trip (for example an authorization plugin using deep links): the scheme is fixed to `dsh-plugin` and the host must be `pluginId`; the Demo needs none.

---

## 4. Plugin entry and renderers

### 4.1 Plugin entry `DshUiPlugin`

```kotlin
class DemoNativePlugin : DshUiPlugin {
    override val descriptor = PluginDescriptor(
        id = "demo.native-ui",                 // 与 manifest 的 dsh.plugin.id 一致
        name = "Demo 插件",
        version = 1,
        capabilities = setOf(
            PluginCapabilities.OPEN_URL,       // 按需声明；未声明的能力调用即被拒
            PluginCapabilities.STORAGE,
            PluginCapabilities.PAGE_ENTRY,     // 注册 PageContent 需要
        ),
    )
    override fun createRenderer(): ComponentRenderer = DemoRenderer()
}
```

### 4.2 Service (copy the template)

```kotlin
class DemoPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(DemoNativePlugin())

    companion object {
        /** App 在自身进程加载页面时使用的入口；仅写 onBind 不足以注册原生页面。 */
        @JvmStatic
        fun plugin(): DshUiPlugin = DemoNativePlugin()
    }
}
```

### 4.3 Renderer `ComponentRenderer`

```kotlin
class DemoRenderer : ComponentRenderer {
    /** 声明你能渲染的 spec 类型；命中才走原生，否则降级 Web 界面 */
    override val supportedTypes = setOf("demo.card")

    @Composable
    override fun Render(spec: JsonObject, host: PluginHostApi) {
        val theme = host.currentTheme()
        // 画 spec。交互经 host.dispatchEvent 回传 Host。
    }
}
```

**Discipline**: the renderer is pure frontend — it holds no network, caches no credentials and performs no business logic. All of that stays in Host-side JS.

---

## 5. Capability bridge PluginHostApi

The only App surface a plugin can touch. Before every capability call the host validates your `capabilities` declaration; undeclared calls are rejected.

| Method | Capability | Description |
| --- | --- | --- |
| `dispatchEvent(componentId, event, payload)` | — (core channel) | Returns user intent to Host-side JS (via bridge dispatch) |
| `openUrl(url)` | `openUrl` | Custom Tab inside the domain allowlist; other domains go through system-browser confirmation |
| `currentTheme()` | — | Read-only theme snapshot (dark mode / font scaling aware) |
| `requestCapability(cap)` | — | Requests a capability; returns false when undeclared or disabled by the user |
| `storage()` | `storage` | Key-value storage in the plugin's own namespace |

**Capability whitelist (v1)**: `openUrl` · `storage` · `notify` · `share` · `clipboard` · `workspace` · `toolRender` · `pageEntry` (constants in `PluginCapabilities`). Capabilities are additive only; breaking changes bump the ABI version.

A single round-trip in the Demo (the "Server +1" button in `DemoSettingsPage.kt`) looks like this:

```kotlin
val payload = buildJsonObject {
    put("actionId", "bump")
    put("by", 1)
}
host.dispatchEvent("dsh-plugin-demo#demo", "submit", payload)
```

---

## 6. GenUI: rendering dsh-ui specs

**GenUI is not a component library but a rendering capability**: the model produces a dsh-ui spec (JSON) at runtime, and you draw it as native UI with `SpecRenderer` — no need to write Compose layouts yourself.

```kotlin
import com.deepseek.dsh.plugin.nativesdk.render.SpecRenderer
import com.deepseek.dsh.plugin.nativesdk.render.DshUiSpecParser

@Composable
fun MyContent(specJson: JsonObject, host: PluginHostApi) {
    val spec = DshUiSpecParser.fromJson(specJson)
    val theme = host.currentTheme()
    SpecRenderer.Render(spec = spec, theme = theme, host = host)
}
```

`SpecRenderer.Render` walks the spec's `items` and renders each component (cards, lists, tables, charts, step bars…); the theme comes from `theme` and interactions are returned through `host`. Unsupported engine components (mermaid/3D, etc.) automatically degrade to a "view in the full Web interface" placeholder — original JSON is never exposed, and it never crashes.

`DemoRenderer` in `demo/native` is a minimal `ComponentRenderer` (it does not parse specs; it only demonstrates mounting and the theme contract); to draw a complete spec, combine `DshUiSpecParser` and `SpecRenderer` as shown above.

**Typical scenario**: a GenUI plugin's `render_ui` tool — when the model calls it, the arguments contain the complete spec, and your tool renderer (see §8) can call `SpecRenderer.Render` directly.

---

## 7. Slots: placing content in the App UI

A slot (`NativeSlot`) is a place in the App UI where plugins can attach content. Every insertable slot on the Web side has its corresponding `NativeSlot` declared by the App's built-in `NativeSlotMapping`; a plugin only registers a native contribution for the same slot, and the App provides the container everywhere else.

### 7.1 Slot list

The Demo companion covers all 22 `NativeSlot` entries (the list is `DemoSlots` in `DemoNativePlugin.kt`). **21 of them inject real UI**; `WEB_ISLAND_FULL` has a host, but it is the full-island Web fallback container and the Demo does not inject it (that would cover the App full-screen); it only documents it on the Demo page:

| Slot | Location | Corresponding Web slot | Host |
| --- | --- | --- | --- |
| `SETTINGS_SHEET` | Settings page | settings.section / plugins.tab | ✅ Ready |
| `SETTINGS_PROVIDER_CARD` | Provider card | settings.models.provider-card | ✅ Ready |
| `SIDEBAR_PANEL` | Sidebar panel | sidebar.* | ✅ Ready |
| `COMPOSER_DOCK` | Input bar dock | conversation.composer.dock / conversation.input.dock | ✅ Ready |
| `COMPOSER_DOCK_ACTIVITY` | dock activity | tok/s entry | ✅ Ready |
| `COMPOSER_DOCK_USAGE` | dock usage | cache hit entry | ✅ Ready |
| `COMPOSER_DOCK_CONTEXT` | dock context | context metering | ✅ Ready |
| `COMPOSER_LEFT` | Left of the input bar | conversation.input.left | ✅ Ready |
| `COMPOSER_RIGHT` | Right of the input bar | conversation.input.right | ✅ Ready |
| `COMPOSER_MODEL` | Model selection | conversation.input.model | ✅ Ready |
| `COMPOSER_PERMISSION` | Permission selection | conversation.input.permission | ✅ Ready |
| `COMPOSER_PLAN` | Plan mode | conversation.input.plan | ✅ Ready |
| `COMPOSER_ATTACHMENTS` | Attachments entry | conversation.input.attachments | ✅ Ready |
| `COMPOSER_BAR` | The whole composer | conversation.composer.bar | ✅ Ready |
| `CONVERSATION_VIEW` | Conversation view | conversation.view | ✅ Ready |
| `SESSION_HEADER_CORNER` | Conversation title corner | session.header.corner | ✅ Ready |
| `COMPOSER_OVERLAY` | Input overlay | conversation.input.overlay | ✅ Ready |
| `PLUGIN_OVERLAY` | Plugin overlay | shell.overlay | ✅ Ready |
| `MESSAGE_ACTION` | Message actions | assistant-actions | ✅ Ready |
| `TOOL_VIEW_SHEET` | Tool details | tool.call.toolview | ✅ Ready |
| `CHAT_STREAM` | Chat stream | chat.flow / chat.node | ✅ Ready |
| `WEB_ISLAND_FULL` | Web island | Full Web fallback | ✅ Host ready · Demo does not inject |

### 7.2 Registering a slot contribution

```kotlin
class DemoDockContribution : SlotContribution() {
    override val slot: NativeSlot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes: Set<String> = setOf("demo.dock")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // 在输入栏上方画 "Demo 42" 胶囊；点按用 PluginPopover 展开详情。
    }
}

// 在插件入口注册
class DemoNativePlugin : DshUiPlugin {
    init {
        PageRegistry.register(DemoSettingsPage())
        DemoSlots.entries
            .filter { it.hostReady && it.slot != NativeSlot.COMPOSER_DOCK }
            .forEach { SlotRegistry.register(DemoSlotContribution(it)) }
        SlotRegistry.register(DemoDockContribution())   // dock 单独展示 popover
    }
    // ...
}
```

The App places a generic container (`NativeSlotHost`) at every slot, which looks up contributions for that slot in `SlotRegistry` and renders them. **What is displayed is entirely up to you**; when no plugin registers, the container takes no space.

### 7.3 The Demo's two Web registration slots

The Demo's JS side registers only 2 slots, which are also the source of the component identities the App perceives; for both of them the scanner reports "NativeSlot mapping present, host present, companion implemented":

| JS registration | id | `componentId` | Native slot | Host | Native implementation |
| --- | --- | --- | --- | --- | --- |
| `settings.section` | `demo` | `dsh-plugin-demo#demo` | `SETTINGS_SHEET` | Yes | Yes |
| `conversation.composer.dock` | `demo-dock` | `dsh-plugin-demo#demo-dock` | `COMPOSER_DOCK` | Yes | Yes |

```bash
$ python tools/plugin_ui_scanner.py --plugin demo/js \
    --companion demo/native/src/main/kotlin --check
plugin UI coverage check OK
```

The scan results are also archived in [Plugin UI inventory example](plugin-ui-inventory-example.md).

---

## 8. Tool call render hook

Take over the native rendering of a particular tool call result.

```kotlin
class RenderUiToolRenderer : ToolRenderer {
    /** 你接管的工具名（精确匹配） */
    override val toolNames = setOf("render_ui")

    @Composable
    override fun Render(call: ToolCallContext, theme: PluginTheme, host: PluginHostApi) {
        // render_ui 的入参就是完整 dsh-ui spec
        val spec = DshUiSpecParser.fromToolArgs(call.args)
        if (spec != null) {
            SpecRenderer.Render(spec = spec, theme = theme, host = host)
        }
    }
}

// 注册
ToolRenderRegistry.register(RenderUiToolRenderer())
```

After the model calls `render_ui`, the App queries `ToolRenderRegistry` at the tool card; if your renderer matches, that call is drawn as native UI. When no plugin takes it over, the App uses the default tool card style; tool rendering reuses the spec rendering approach from §6.

---

## 9. Page entries: settings page and plugin page

Mount a native page on the settings page or plugin page. The Demo's settings page is a `PageContent` with `id = "demo"`:

```kotlin
class DemoSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "demo",
        title = "Demo 插件",
        subtitle = "JS 插件 + 原生伴生：RPC 与落点接入示例（虚构）",
        placement = PageEntry.Placement.SETTINGS,   // 或 PLUGINS
    )

    @Composable
    override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        DemoSettingsContent(theme = theme, host = host, refreshEvents = null)
    }
}

PageRegistry.register(DemoSettingsPage())
```

When the user taps your entry on the settings page or plugin page, the App draws the full page with `Content`.

### 9.1 Settings page takeover rules

`PageEntry.id` must be exactly the `id` registered by the JS-side `settings.section`. The effective component identity is
`<jsPackage>#<PageEntry.id>`; the companion's `pluginId` and the JS package name may differ. Only after the static factory loads successfully and that page is registered does the App skip the WebView; if the APK is merely installed without registering a page, the Web path is still used.

For the Demo: `settings.section id = "demo"` + `PageEntry(id = "demo", placement = SETTINGS)`
→ `componentId = dsh-plugin-demo#demo`; the companion's own `pluginId` is `demo.native-ui`.

### 9.2 Receiving Host refreshes and checking page liveness

A page with live state can re-render the `Content` variant that takes `refreshEvents` and re-read the authoritative
RPC after receiving an event for its own component. Before updating the page from an async callback, check `PageLifecycle.LocalIsActive.current` so that state is not written into an old page after the user has already gone back. The Demo settings page does exactly this:

```kotlin
LaunchedEffect(refreshEvents) {
    refreshEvents?.collect { componentId ->
        if (componentId == DemoRpc.PAGE_COMPONENT_ID || componentId == DemoRpc.JS_PACKAGE) refresh()
    }
}
```

---

## 10. Theme contract

- Colors/font sizes/shapes **all** come from the tokens of `host.currentTheme()`;
- **Never** hardcode color values or font sizes — it is a store review red line and a prerequisite for visual consistency between Web and native;
- Dark mode and system font scaling are handled uniformly by the App; snapshot changes trigger recomposition, and you write no light/dark branches;
- Touch targets ≥44dp, no hover dependency.

---

## 11. Communicating with Host-side JS

Fully reuse the bridge's existing chain, with zero new protocols. Taking the Demo's "Server +1" as an example:

```
your native side host.dispatchEvent("dsh-plugin-demo#demo", "submit", { actionId: "bump" })
  → App bridge client calls client-slots/dispatch
  → Host event bus ui/dsh-plugin-demo#demo/submit
  → JS plugin mobileUi action (demo/js/lib/actions.js)
  → state changes; bridge queues client/refresh
  → App receives the refresh, re-fetches the spec or lets the page re-read RPC
  → your renderer's Render() recomposes
```

You only need to guarantee:

1. **componentId convention**: use the Host's actual `<jsPackage>#<id inside the slot>`; the companion's own `pluginId` need not equal that package name. The Demo's two identities are `dsh-plugin-demo#demo` (settings page) and `dsh-plugin-demo#demo-dock` (dock);
2. **JS-side state push**: put the data the native side should draw into the `patch` of `client/refresh`, and make sure the spec is JSON-safe presentation data (no credentials, no functions, no circular references).

### 11.1 Pages over an existing plugin RPC: `PluginRpcHost`

A complete native page can also safely cast `host` to `PluginRpcHost` and call the plugin routes the Host already has:

```kotlin
val rpc = host as? PluginRpcHost
rpc?.callRpc("demo.status", JsonObject(emptyMap())) { result ->
    // 校验 result.ok，取 value；payload 原样发送，不包 args。
}
```

`callRpc` is a callback-style API, not `suspend`. When you need to re-read, increment `refreshKey` and let
`LaunchedEffect(rpc, refreshKey)` issue the next call. Call only methods the Host already exposes, and decode the
`{ result: { ok, value | error } }` envelope before interpreting business state. The Demo wraps the callback into a
`suspend` function with a top-level extension (`DemoRpc.kt`), so pages and slot contributions can simply `await`:

```kotlin
internal suspend fun PluginRpcHost.demoCall(
    method: String,
    payload: JsonObject = JsonObject(emptyMap()),
): JsonElement? = suspendCancellableCoroutine { continuation ->
    callRpc(method, payload) { result ->
        continuation.resume(result.getOrNull())
    }
}
```

The Demo exposes 6 endpoints (full contract in §14.3):

| Endpoint | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | The updated status snapshot |
| `demo.reset` | `{}` | The status snapshot after reset |
| `demo.echo` | Any JSON | `{ received: <original echoed back> }` |

### 11.2 Reference implementations for migrating complex pages

For complete implementations of whole-page RPC reads, `dispatchEvent` round-trips plus `refreshEvents` re-fetch, `PluginPopover` overlays and placeholders for every slot, see:

- `demo/native/src/main/kotlin/com/dsh/mobile/dsh_plugin_demo/DemoSettingsPage.kt` (the full settings page);
- `demo/native/src/main/kotlin/com/dsh/mobile/dsh_plugin_demo/DemoDockContribution.kt` (dock chip + popover);
- `demo/native/src/main/kotlin/com/dsh/mobile/dsh_plugin_demo/DemoSlotContribution.kt` (the remaining slots).

Do not migrate only the card shell: expanded regions, button actions, empty states, error states and fallback paths are all part of the migration.

---

## 12. Degradation contract

The native layer is always an enhancement, never a requirement:

| Scenario | Behavior |
| --- | --- |
| User has not installed the companion APK | The plugin uses the WebView (full interface), fully functional |
| APK installed but ABI incompatible | Shows "plugin needs an update" and falls back to the WebView |
| `supportedTypes` does not match the spec type | That component degrades; the rest stay native |
| Renderer throws | Caught by the error fence, shows a placeholder block, does not affect the App or other plugins |
| User uninstalls the APK | The render slot is removed immediately and the WebView is restored |

**Therefore**: your JS-side functionality must be fully usable on plain Web — never assume the native side is
present. The Demo's Web fallback is in `demo/js/lib/client.js`: it registers `settings.section(id = "demo")` and
`conversation.composer.dock(id = "demo-dock")`, and uses `connection.rpc.call("/api", "demo.status")`
to read the same Host state; it still works when the APK is not installed.

---

## 13. Release and store

### 13.1 Two ways to build

- **Build it yourself**: build the release APK in your own Android project; for the Demo's build steps see [demo/README](../demo/README.md) (copy `demo/native` into the App repo and build with the App's Gradle);
- **Let CI build it**: you only submit Kotlin sources to GitHub, and the store CI compiles and signs them uniformly with pinned SDK/Compose/Kotlin versions — you do not even need the Android SDK locally.

### 13.2 Store inclusion (GitHub tag mode)

The store's actual content goes through a **GitHub tag index**: tag your plugin repo with `dsh-mobile-plugin` and the store picks it up into the index automatically.

1. Tag your plugin repo (JS plugin + native companion sources) with `dsh-mobile-plugin`;
2. The store's indexer scans repos carrying that tag and reads the `dsh.native` declaration (pluginId/capabilities/version);
3. After review it enters the store index, and users see it in the recommended list under "Plugins → Plugin store";
4. One-tap install → system installer confirmation → the App discovers it, verifies the signature and checks the ABI → it appears in the list.

### 13.3 Declaring the native companion on the JS side (`package.json`)

The Demo's key declaration (excerpt from `demo/js/package.json`):

```json
{
  "name": "dsh-plugin-demo",
  "version": "0.1.0",
  "dsh": {
    "native": {
      "pluginId": "demo.native-ui",
      "name": "Demo 插件",
      "apiVersion": 1,
      "minAppVersion": 100,
      "capabilities": ["openUrl", "storage"]
    }
  }
}
```

The bridge's `native-plugins` provider exposes this declaration to the App, and the store page uses it to label "native enhanced" and compatibility.

### 13.4 Signing and updates

- The store records your pluginId + signing certificate fingerprint (permanent identity);
- Updates need only an incremented APK versionCode with an unchanged signature; **a signature change is treated as a new plugin** (anti-tampering), and the store refuses to overwrite the same pluginId.

---

## 14. Complete example: the Demo plugin (JS + native)

A real npm plugin package plus a companion APK, all code in the repo and directly runnable:

- JS half: [`demo/js`](../demo/js) (`dsh-plugin-demo` 0.1.0, entry `lib/index.js`);
- Native half: [`demo/native`](../demo/native) (`demo.native-ui`, APK package name `com.dsh.mobile.dsh_plugin_demo`);
- Inventory output: [Plugin UI inventory example](plugin-ui-inventory-example.md).

Data lives only in the Host process (count starts at 42); it makes no network access, reads and writes no account, and holds no credentials; after a Host restart it returns to the initial value.

### 14.1 Identity pairing

| Location | Value |
| --- | --- |
| JS package name | `dsh-plugin-demo` (0.1.0) |
| JS entry | `lib/index.js` (exports `name` / `apply`) |
| Companion `pluginId` (manifest `dsh.plugin.id`) | `demo.native-ui` |
| Companion APK package name | `com.dsh.mobile.dsh_plugin_demo` |
| manifest `dsh.plugin.jsPackage` | `dsh-plugin-demo` |
| Settings page Web registration id / native `PageEntry.id` | `demo` |
| Settings page component identity `componentId` | `dsh-plugin-demo#demo` |
| dock Web registration id | `demo-dock` |
| dock component identity `componentId` | `dsh-plugin-demo#demo-dock` |

Two conditions for the settings page to be taken over natively:

1. The JS side registers `settings.section` with `id = "demo"`;
2. The companion APK registers `PageEntry(id = "demo", placement = SETTINGS)`.

Both share the component identity `dsh-plugin-demo#demo`, and only then does the App skip the WebView.

### 14.2 JS side: entry, RPC and actions

The entry declares no top-level `inject`; both capabilities are registered with `ctx.inject` when the service appears — so the Demo still loads when the Host composition has no web server or bridge plugin:

```js
// demo/js/lib/index.js
export const name = 'dsh-plugin-demo';

export function apply(ctx) {
  const state = createDemoState();      // 每个插件实例一份状态
  registerDemoRpc(ctx, state);          // ctx.inject(['connection']) → /api/demo.*
  registerDemoActions(ctx, state);      // ctx.inject(['mobileUi']) → bump / reset
}
```

RPC is registered on the `connection` service; `payload` is passed through as-is and is not wrapped in `args`:

```js
// demo/js/lib/rpc.js（节选）
export function registerDemoRpc(ctx, state) {
  ctx.inject(['connection'], (connectionCtx) => {
    const connection = connectionCtx.get('connection');
    const register = connection?.fetch?.register;
    if (typeof register !== 'function') return;
    for (const endpoint of RPC_ENDPOINTS) {
      const method = `${RPC_PREFIX}${endpoint}`;      // demo.status …
      const route = rpcRoute(method, endpoint, state);
      connectionCtx.effect(
        () => register.call(connection.fetch, route),
        `dsh-plugin-demo: /api/${method}`,
      );
    }
  });
}
```

Native actions are likewise registered on the component identity; when the bridge hits one it runs the plugin code and then queues `client/refresh` for the phone:

```js
// demo/js/lib/actions.js（节选）
ctx.inject(['mobileUi'], (uiCtx) => {
  const ui = uiCtx.get('mobileUi');
  if (ui === undefined || typeof ui.registerAction !== 'function') return;
  for (const componentId of [PAGE_COMPONENT_ID, DOCK_COMPONENT_ID]) {
    for (const actionId of ['bump', 'reset']) {
      uiCtx.effect(
        () => ui.registerAction(componentId, actionId, (payload) => runAction(actionId, payload)),
        `dsh-plugin-demo: ${componentId}/${actionId}`,
      );
    }
  }
});
```

The Web fallback (`demo/js/lib/client.js`) registers the same two slots and reads the same state with
`connection.rpc.call("/api", "demo.status")`:

```js
ctx.slots.inject("settings.section", () => ctx.slots.register({
  name: "settings.section", id: "demo", order: 10,
  label: () => t("nav"), inject: () => ({ call, t }),
}, DemoSettingsSection));

ctx.slots.inject("conversation.composer.dock", () => ctx.slots.register({
  name: "conversation.composer.dock", id: "demo-dock", order: 10,
  inject: () => ({ call }),
}, DemoDockChip));
```

### 14.3 RPC contract

Request envelope (`payload` is passed through as-is and is not wrapped in `args`):

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

Success response:

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

Business failures are also HTTP 200, with the error in `result.error`; transport-level errors use 400 (unparseable / envelope mismatch) and
415 (non-JSON):

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

The 6 endpoints:

| Endpoint | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | The updated status snapshot |
| `demo.reset` | `{}` | The status snapshot after reset |
| `demo.echo` | Any JSON | `{ received: <original echoed back> }` |

Error codes: `demo/bad-request` (400), `demo/unsupported-media-type` (415),
`demo/unknown-endpoint` (HTTP 200 + `result.error`). The native side branches on the code rather than matching message text.

### 14.4 All native-side code

Entry (`DemoNativePlugin.kt`): the descriptor matches the manifest; it registers the settings page, the ready slots and the dock:

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
        PageRegistry.register(DemoSettingsPage())
        DemoSlots.entries
            .filter { it.hostReady && it.slot != NativeSlot.COMPOSER_DOCK }
            .forEach { SlotRegistry.register(DemoSlotContribution(it)) }
        SlotRegistry.register(DemoDockContribution())
    }
}
```

The settings page (`DemoSettingsPage.kt`) receives an empty spec, reads authoritative state over RPC, and returns intent from its buttons:

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

// 页面内的数据流
LaunchedEffect(rpc, refreshKey) {
    val body = rpcHost.demoCall(DemoRpc.STATUS)     // demo.status
    if (!isActive()) return@LaunchedEffect
    DemoRpc.value(body)?.let { status = DemoRpc.statusOf(it) }
}

// 「服务端 +1」：不自己改状态，回传事件给 JS 插件
host.dispatchEvent(
    DemoRpc.PAGE_COMPONENT_ID,                      // dsh-plugin-demo#demo
    DemoRpc.EVENT_SUBMIT,                           // "submit"
    buildJsonObject { put("actionId", "bump"); put("by", 1) },
)

// 「重置」：纯 RPC 写路径
rpcHost.demoCall(DemoRpc.RESET)                     // demo.reset
```

The dock (`DemoDockContribution.kt`) uses the same read/write approach, and its overlay uses `PluginPopover` (anchored to the trigger + App menu material):

```kotlin
class DemoDockContribution : SlotContribution() {
    override val slot: NativeSlot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes: Set<String> = setOf("demo.dock")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // PluginRpcHost.callRpc("demo.status") 读；PluginPopover 里按钮回传：
        // host.dispatchEvent(DemoRpc.DOCK_COMPONENT_ID, DemoRpc.EVENT_SUBMIT,
        //                    buildJsonObject { put("actionId", "bump"); put("by", 1) })
    }
}
```

RPC constants and envelope parsing are centralized in `DemoRpc.kt`: method names, component identities, `value()` / `errorMessage()`
and the `demoCall` extension all live there, shared by pages and slot contributions, with no duplicated string building.

### 14.5 Verification and the user's view

```bash
$ node --test demo/js/test/rpc.test.mjs
# tests 7 / pass 7：路由、信封、bump/reset、payload 透传、动作隔离、状态隔离

$ python tools/plugin_ui_scanner.py --plugin demo/js \
    --companion demo/native/src/main/kotlin --check
plugin UI coverage check OK
```

From the user's point of view:

1. Install the JS plugin (`dsh plugin --profile web add ./demo/js`);
2. "Plugins → Plugin store" shows "Demo plugin · Native enhanced";
3. Build and install the companion APK (steps in [demo/README](../demo/README.md): copy `demo/native` into the
   App repo, build with the App's Gradle, then `adb install` the produced debug APK);
4. Open the App → Settings → Plugin interfaces → Demo plugin (taken over by the native page); above the input bar
   on the chat page a "Demo 42" chip appears, and tapping it opens a popover;
5. Uninstall the native APK → the Web page is restored automatically (it can still read `demo.status`).

---

## 15. Component catalogue (dsh-ui spec reference)

Every component type `SpecRenderer` supports. The spec fields for each component are as follows.

### Layout containers

| type | Fields | Description |
| --- | --- | --- |
| `card` | `title?`, `items[]` | Card container with a title |
| `col` | `items[]` | Vertical stack |
| `row` | `items[]` | Horizontal, evenly divided |
| `grid` | `columns`, `items[]` | Grid (rows split by column) |

### Text and display

| type | Fields | Description |
| --- | --- | --- |
| `text` | `value`/`text`, `size?`(title/caption) | Text |
| `callout` | `content`/`text`, `tone?`(info/success/warning/error), `title?` | Callout block |
| `badge` | `label`/`text` | Pill label |
| `stat` | `label`, `value` | Metric (label + large number) |
| `hero` | `title`, `subtitle?`/`content?` | Cover block (at most one per answer) |
| `divider` | — | Divider |
| `link` | `url`/`href`, `label?`/`text?` | Link (taps go through `openUrl`) |
| `image` | `alt?` | Image placeholder (degraded notice on native) |

### Collections and data

| type | Fields | Description |
| --- | --- | --- |
| `list` | `items[]` (text or child components) | List |
| `table` | `columns[]`, `rows[][]` | Table |
| `keyvalue` | `pairs[]` (`{key, value}`) | Key-value pairs |
| `steps` | `steps[]` (`{title}` or text) | Step bar |
| `timeline` | `events[]`/`items[]` (`{time/label, title/text}`) | Timeline |
| `progress` | `value`(0-100), `label?` | Progress bar |
| `code` | `code`/`value`/`content` | Code block (monospace, horizontally scrollable) |
| `chart` | `kind:"bars"`, `data[]` (`{label, value}`) | Bar chart (simplified on native) |

### Fallback components (rendered on Web)

The following engine components render on native as a readable "view in the full Web interface" placeholder:

`mermaid` · `diagram` · `plot` · `echart` · `scene3d` · `diff` · any other unrecognized type

> These need a Web rendering engine; users see their full form in the plugin's complete Web interface.

---

## Appendix: Prohibited items at a glance

| Prohibited | Reason |
| --- | --- |
| Writing business logic inside the APK (auth flows, calling model APIs) | Iron rule: logic lives in the Host |
| Storing/transmitting tokens, secrets or API keys | Credentials never leave the Host |
| Requesting system permissions (`<uses-permission>`) | Capabilities go through the whitelist bridge |
| Hardcoding colors or font sizes | Theme contract |
| Reflecting into App-internal classes | Security boundary |
| Nested dynamic loading (loading a dex inside a dex) | Review red line |
| Assuming the native side is present (JS features depending on the APK) | Degradation contract |
| Changing a released `dsh.plugin.id` | Identity anchoring |
| Publishing under the same id after changing the signature | Anti-tampering |
