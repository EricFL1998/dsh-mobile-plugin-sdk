> English | [中文](native-plugin-sdk.md)

# Native Plugin SDK Specification (Plugin-Side Contract)

> Status: **proposal (pending review)**. This document defines every contract a third-party developer must follow in the "native UI plugin store" system; examples throughout use the Demo plugin in this repository.
> Audience: plugin developers who want to provide native (Kotlin/Compose) UI for the DSH mobile App.
> Related documents: [JS UI to native UI migration guide](plugin-conversion-guide.md), [SDK development guide](plugin-sdk-guide.en.md), [App-side host interfaces](native-plugin-host.md), [mobile bridge protocol](mobile-bridge.md), [Demo plugin (JS half)](../demo/js/README.md), [Demo plugin directory](../demo/README.md), [plugin UI inventory example](plugin-ui-inventory-example.md), [documentation site example](index.html#case).
> Updated: 2026-10-22 (examples switched to the Demo plugin: `demo/js` + `demo/native`; the contract itself is unchanged).

## Table of Contents

- [0. Summary](#0-summary)
- [1. Positioning: the native APK is a second face for Host logic](#1-positioning-the-native-apk-is-a-second-face-for-host-logic)
- [2. Iron rule: logic always lives in the Host, credentials never leave it](#2-iron-rule-logic-always-lives-in-the-host-credentials-never-leave-it)
- [3. Package shape: npm package + companion APK](#3-package-shape-npm-package--companion-apk)
- [4. APK manifest contract (AndroidManifest)](#4-apk-manifest-contract-androidmanifest)
- [5. SDK API contract](#5-sdk-api-contract)
- [6. Theme contract](#6-theme-contract)
- [7. Capability whitelist](#7-capability-whitelist)
- [8. Communication with Host-side JS](#8-communication-with-host-side-js)
- [9. Degradation contract](#9-degradation-contract)
- [10. Distribution and store submission](#10-distribution-and-store-submission)
- [11. Review checklist](#11-review-checklist)
- [12. Versioning and compatibility](#12-versioning-and-compatibility)
- [13. Complete example: the Demo plugin (JS + native)](#13-complete-example-the-demo-plugin-js--native)
- [14. Prohibited items at a glance](#14-prohibited-items-at-a-glance)

---

## 0. Summary

The DSH mobile App provides a "native UI plugin" system: third-party developers can attach a **Kotlin/Compose renderer** to an existing (or new) JS plugin, distribute it as a separate APK through the store, and after installation upgrade the plugin UI from a WebView/transpiled tree to a native experience.

Core promises:

- **Zero rewrite of the logic layer**: the plugin's Host-side logic (`lib/index.js`: RPC, state, events) keeps running untouched; the native APK only implements "frontend logic" — drawing the spec and sending user intent back.
- **Zero new protocols for communication**: the native side talks to the Host through the existing bridge `dispatch` op and `client/refresh` event; no new protocol line is invented.
- **Degradation always available**: if users do not install the native APK, the plugin remains fully usable through the WebView; installing it enhances the experience. The native layer is an enhancement, not a requirement.

The example identity in this document is fixed to the Demo plugin: JS package `dsh-plugin-demo` (`demo/js`), companion `pluginId` `demo.native-ui`, APK package name `com.dsh.mobile.dsh_plugin_demo` (`demo/native`). It has no accounts, network, or credentials, which keeps the contract down to a few hundred readable lines.

---

## 1. Positioning: the native APK is a second face for Host logic

DSH plugins are naturally layered:

| Layer | Artifact | Runs in | Written by |
| --- | --- | --- | --- |
| Logic layer | `lib/index.js` | Host (proot/Node inside the phone) | Third party, already done |
| Web frontend | `lib/client.js` | Browser / WebView | Third party, already done |
| **Native frontend** | **Companion APK** | **App process (inside the Compose host container)** | **Third party, new (this specification)** |
| Communication protocol | bridge `dispatch` / `client/refresh` | Inside the Host | DSH, already available |
| Host interfaces | `PluginHostApi` implementation | Inside the App | DSH, see [native-plugin-host.md](native-plugin-host.md) |

```
┌─ Host (proot/Node) ──────────────────────────┐
│  dsh-plugin-demo/lib/index.js                │
│  demo.* RPC · in-memory state · native action listeners │
│        ↕ client/refresh / dispatch           │
│  bridge plugin (available)                   │
└───────↕────────────────↕──────────────────────┘
   Frontend A: browser      Frontend B: native APK
   lib/client.js (React)    DemoNativePlugin.kt (Compose)
   Desktop / Web users      Mobile users
```

Both frontends consume **the same state stream** and send back **the same kinds of events**, without knowing about each other.

---

## 2. Iron rule: logic always lives in the Host, credentials never leave it

This is the first rule of the specification and the bottom line of store review:

1. **The native APK performs no business logic**: it does not start authorization flows, refresh tokens, or call model APIs directly. To do anything, it can only `dispatchEvent` and let Host-side JS do the work.
2. **Credentials never leave the Host**: tokens, secrets, and API keys exist only inside the Host process (proot). `PluginHostApi` provides no way to read credentials; specs may contain presentation data only.
3. **Renderers are stateless**: native renderers consume only the spec (a JSON snapshot), hold no network connections, cache no credentials, and persist no business state across calls.
4. **Review backstop**: static store scanning rejects APKs that call model APIs directly, read Host directories, or reflectively access App-internal classes.

The Demo plugin has no credentials at all (its state is just a counter in Host memory), but these boundaries are designed for "real plugins that do have credentials" — Demo is merely the smallest sample that exercises the boundary.

This boundary is also the security architecture: the native plugin APK is third-party code inside the App process, physically unable to reach credentials, which greatly reduces the attack surface.

---

## 3. Package shape: npm package + companion APK

### 3.1 Why a companion APK instead of a bare dex

| Dimension | Bare dex download | Companion APK (chosen by this specification) |
| --- | --- | --- |
| Signature verification | Implement yourself | Handled system-wide by PackageManager |
| Install confirmation | No system UI | System installer, visible to the user |
| Version management | Implement yourself | versionCode/versionName handled by the system |
| Uninstall linkage | Detect yourself | `PACKAGE_REMOVED` broadcast, automatic cleanup |
| Play policy | **Prohibited** (downloading executable code) | Legal (an app the user actively installs) |

The cost is one extra "install another app" confirmation for the user; the store UI wraps this flow as "install plugin".

### 3.2 How the two packages are linked

- npm package `dsh-plugin-demo` ([demo/js](../demo/js)): unchanged, installed into the Host through the existing plugin installation path.
- Companion APK `com.dsh.mobile.dsh_plugin_demo` ([demo/native](../demo/native)): distributed through the store and installed by the system installer.
- The companion identity is **`pluginId`** `demo.native-ui`, used for signing, store, and registration ownership.
- The JS pairing key is **`jsPackage`** `dsh-plugin-demo`, declared via `dsh.plugin.jsPackage` on the Service. The two may differ.
- The page takeover key is **`<jsPackage>#<PageEntry.id>`**: `dsh-plugin-demo#demo` (settings page) and `dsh-plugin-demo#demo-dock` (dock), matching the actual component identities in the bridge. The App uses this to pair "slots registered by the JS plugin" with "native pages"; it cannot guess the package name from pluginId alone.

Authorization plugins use the same pairing mechanism; this document uses the Demo plugin as the example.

### 3.3 A plugin may have only one side

- JS-only plugin: unaffected; it uses the WebView/transpiled tree.
- Native-only plugin: allowed, but the logic must still live in some Host plugin (it can be a very thin companion JS package); **the APK must not carry business logic** (see section 2).

---

## 4. APK manifest contract (AndroidManifest)

The plugin APK must declare the following meta-data and service; none may be omitted. The Demo manifest (`demo/native/src/main/AndroidManifest.xml`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.dsh.mobile.dsh_plugin_demo">

    <application>
        <!-- 入口服务：App 通过 action 发现插件，并读取 service 级 meta-data -->
        <service
            android:name=".DemoPluginService"
            android:exported="true"
            android:permission="com.deepseek.dsh.permission.BIND_PLUGIN">
            <meta-data android:name="dsh.plugin.apiVersion" android:value="1" />
            <meta-data android:name="dsh.plugin.minAppVersion" android:value="12" />
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

Rules:

- `apiVersion` is an integer, incremented on breaking SDK changes; an App below `minAppVersion` **refuses to load** the plugin (shows "update required", does not crash).
- Once published, `dsh.plugin.id` **must not change** — it anchors signature pinning and store identity.
- `dsh.plugin.jsPackage` is the JS pairing key; the Demo's `pluginId` (`demo.native-ui`) differs from the JS package name (`dsh-plugin-demo`), which is allowed.
- `android:permission="com.deepseek.dsh.permission.BIND_PLUGIN"` must be copied exactly to prevent other apps from binding your service.
- The plugin APK **requests no system permissions** (no `<uses-permission>`); capabilities go through the capability bridge declarations in section 7.
- Only plugins that genuinely need a system redirect declare an extra Activity (scheme fixed to `dsh-plugin`, host set to `pluginId`); the Demo does not need one.

---

## 5. SDK API contract

Dependency: put `artifacts/dsh-native-ui-sdk-release.aar` into the companion APK project's `libs/`, then declare `implementation(files("libs/dsh-native-ui-sdk-release.aar"))` in `build.gradle.kts`; alternatively copy `sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/**` sources into your own library module. The AAR and sources are equivalent; the AAR carries no transitive dependencies, so you must declare Compose and serialization dependencies yourself (see [sdk/README.md](../sdk/README.md)). The SDK pins the same Kotlin/Compose versions as the App; plugins use the Compose runtime as `compileOnly` (the App provides it at runtime, eliminating version coupling).

### 5.1 Plugin entry point

```kotlin
interface DshUiPlugin {
    val descriptor: PluginDescriptor
    fun createRenderer(): ComponentRenderer
}

data class PluginDescriptor(
    val id: String,                  // 与清单 dsh.plugin.id 一致
    val name: String,
    val version: Int,                // 插件自身版本，随发布递增
    val capabilities: Set<String>,   // 见第 7 节，未声明的能力调用即拒
)
```

Copy the service class from the template and replace only the plugin instance. Keep both `onBind` and the static `plugin()`: the former is used for service binding, the latter lets the App instantiate the plugin inside its own process and register pages. The main App and the companion share one parent-first SDK/Compose type set; plugins must not bypass the ABI by loading code with an isolated ClassLoader or nested dex:

```kotlin
class DemoPluginService : Service() {
    override fun onBind(intent: Intent): IBinder =
        DshPluginBinder(DemoNativePlugin())

    companion object {
        /** Required factory used when the App loads the plugin inside its own process. */
        @JvmStatic
        fun plugin(): DshUiPlugin = DemoNativePlugin()
    }
}
```

### 5.2 Renderer

```kotlin
interface ComponentRenderer {
    /** 声明本渲染器能画的 spec 类型；App 命中才走原生，否则降级 */
    val supportedTypes: Set<String>

    /**
     * 画一个 spec。运行在 App 提供的宿主容器内（尺寸约束/错误围栏），
     * 插件只画内容区。拿不到 Context、View、App 内部对象——只有 host。
     */
    @Composable
    fun Render(spec: JsonObject, host: PluginHostApi)
}
```

The Demo implementation declares a single `demo.card` type to prove minimal integration; to draw full dsh-ui specs, use `SpecRenderer` (see [SDK development guide](plugin-sdk-guide.en.md) §6).

### 5.3 Host API (the only App surface a plugin can touch)

```kotlin
interface PluginHostApi {
    /** 回传用户意图给 Host 侧 JS（走 bridge dispatch op，现成协议） */
    fun dispatchEvent(componentId: String, event: String, payload: JsonObject)

    /** 打开链接：域白名单内直接开，其余经系统浏览器确认 */
    fun openUrl(url: String)

    /** 主题 token 只读快照；深色模式切换时更新并触发重组 */
    fun currentTheme(): PluginTheme

    /** 申请能力（通知/存储等）；未声明或用户关闭返回 false */
    fun requestCapability(capability: String): Boolean

    /** 每插件独立命名空间的键值存储；禁读其他插件 */
    fun storage(): PluginStorage
}

interface PluginStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}
```

**What is explicitly absent** (presence is a violation): network, `Context`, Host token, arbitrary filesystem paths, other plugins' storage, reflection entry points.

---

## 6. Theme contract

- Colors, font sizes, shapes, and spacing **all** come from tokens in `host.currentTheme()`.
- Hard-coded colors/font sizes are prohibited — static store scanning plus manual spot checks.
- Dark mode and system font scaling are handled uniformly by the App; plugins do not branch on `isSystemInDarkTheme()`, because the theme snapshot already contains the resolved values.
- Touch targets ≥44dp, no hover dependency, aligned with the Web mobile adaptation checklist.

---

## 7. Capability whitelist

Capabilities are versioned, declaration-based, and user-controlled. v1 exposes six:

| Capability | Declaration value | Call | App-side policy |
| --- | --- | --- | --- |
| Open URL | `openUrl` | `host.openUrl(url)` | Domain allowlist; otherwise confirm in the system browser |
| Isolated storage | `storage` | `host.storage().get/put` | Separate namespace per plugin |
| Notifications | `notify` | SDK callback interface after `requestCapability("notify")` | Foreground service channel; user can disable globally |
| Share | `share` | Through an SDK extension function | System share sheet |
| Clipboard | `clipboard` | Through an SDK extension function | Write-only |
| Workspace files | `workspace` | Through an SDK extension function | Only inside SAF-authorized directories, with path-prefix validation |

Two further declaration values cover page entries and tool rendering (`pageEntry`, `toolRender`); the Demo declares `openUrl`, `storage`, and `pageEntry` (see `DemoNativePlugin.descriptor`).

Discipline:

- **Add, never change**: existing capability semantics stay fixed; breaking changes bump `apiVersion`.
- Camera/location/contacts are **not exposed in v1**; exposing them later requires an `apiVersion` bump and an explicit note on the store page.
- Calls to capabilities not declared in `descriptor.capabilities` return `false`/no-op and are audit-logged.

---

## 8. Communication with Host-side JS

The existing bridge path is reused entirely ([mobile-bridge.md](mobile-bridge.md)). The Demo "server +1" path:

```
native host.dispatchEvent("dsh-plugin-demo#demo", "submit", { actionId: "bump" })
  → App bridge client calls client-slots/dispatch
  → Host event bus ui/dsh-plugin-demo#demo/submit
  → JS plugin mobileUi action (demo/js/lib/actions.js)
  → JS state updates; the bridge queues client/refresh
  → App receives the refresh, re-pulls the spec / the page re-reads RPC
  → native renderer Render() recomposes
```

Requirements for plugin authors:

1. **componentId convention**: use the Host's actual `<jsPackage>#<id inside slot>`, matching the package name and id in the JS-side `ctx.slots.register`; the companion `pluginId` may differ. The Demo's two identities are `dsh-plugin-demo#demo` and `dsh-plugin-demo#demo-dock`.
2. **Push state from JS**: put the data the native side should draw into the `patch` of `client/refresh` (or re-register directly), keeping the spec JSON-safe presentation data (no credentials, no functions, no circular references).
3. **Existing RPC**: full pages can call existing Host routes through `PluginRpcHost.callRpc(method, payload, callback)`, sending the payload as-is; unwrap the `{ result: { ok, value | error } }` envelope first. Do not invent generic `component.state` / `component.dispatch` routes.
4. **Page lifecycle**: `PageContent.Content` that takes `refreshEvents` can respond to Host refreshes; async callbacks should check `PageLifecycle.LocalIsActive` first to avoid late results updating a page the user has left.

The Demo exposes six RPC endpoints: `demo.status`, `demo.catalog`, `demo.ping`, `demo.bump`,
`demo.reset`, and `demo.echo`. The request envelope is
`{ type: "client-request", rpcId, method, payload }`; a success response is
`{ type: "server-response", rpcId, result: { ok: true, value } }`; business failures still return HTTP 200,
with the error in `result.error: { code, message, details }`; transport errors use 400/415. See section 13.4 for the full contract.

---

## 9. Degradation contract

The native layer is always an enhancement, never a requirement:

| Scenario | Behavior |
| --- | --- |
| User has not installed the companion APK | Plugin uses the WebView (full UI) or transpiled tree; fully functional |
| APK installed but ABI incompatible | Shows "plugin needs update"; falls back to WebView |
| Renderer `supportedTypes` misses the spec type | That component falls back; the rest stays native |
| Renderer throws | Error fence catches it, shows a placeholder block, and does not affect the App or other plugins |
| User uninstalls the APK | `PACKAGE_REMOVED` immediately removes the render slot and falls back to WebView |
| User turns on the "disable all plugin UI" master switch | All native plugin UI is disabled and uniformly degrades |

JS must therefore **not assume** the native side is present: every feature must work in pure Web. The Demo's Web fallback lives in
`demo/js/lib/client.js`: it registers `settings.section(id = "demo")` and
`conversation.composer.dock(id = "demo-dock")` and reads the same Host state through
`connection.rpc.call("/api", "demo.status")`; without the APK, all three buttons
(refresh / server +1 / reset) still work.

---

## 10. Distribution and store submission

1. **Develop**: create an Android project with `dsh-plugin-sdk` and implement sections 4–7.
2. **Self-check**: verify slot coverage between plugin source and companion implementation with `tools/plugin_ui_scanner.py --check`, and self-review against the checklist in section 11.
3. **Submit to the store**: upload the APK + source repository URL + screenshots + capability description.
4. **Store registration**: record pluginId and signing certificate fingerprint (permanent identity); once review (section 11) passes, the plugin enters the index.
5. **User install**: store page → download → system installer → automatic App discovery (see [native-plugin-host.md](native-plugin-host.md) §3).
6. **Update**: increment the APK versionCode while keeping the same signature; a signature change is treated as a new plugin (anti-tampering), and the store rejects overwriting the same pluginId.

---

## 11. Review checklist

Static scanning (automatic):

- [ ] Service-level `apiVersion`/`minAppVersion`/`id`/`name` are complete; declare `jsPackage` when JS pairing is needed.
- [ ] `pluginId` matches the descriptor; `jsPackage#PageEntry.id` matches the actual Host component identity (Demo: `demo.native-ui` / `dsh-plugin-demo#demo`).
- [ ] The Service exposes `@JvmStatic fun plugin(): DshUiPlugin`.
- [ ] No `<uses-permission>`.
- [ ] Zero hits on forbidden APIs: reflective access to `com.deepseek.dsh.*` internal classes, `DexClassLoader`/`PathClassLoader` nested dynamic loading, reading the private `/data/data/com.deepseek.dsh.mobile` directory, direct calls to model API domains.
- [ ] No hard-coded colors/font sizes (theme contract).
- [ ] `capabilities` declarations match actual calls.

Manual spot checks:

- [ ] Screenshots match the installed build; capability descriptions are not exaggerated.
- [ ] No credential-collection UI (password fields, token inputs).
- [ ] Degradation path works (the JS plugin remains fully usable after uninstalling the APK).

---

## 12. Versioning and compatibility

| Version | Meaning | Change rules |
| --- | --- | --- |
| `apiVersion` | SDK/App interface version | +1 on breaking changes; the App rejects versions that are too low |
| `minAppVersion` | Minimum App version code required by the plugin | Declared at plugin release; the App refuses to load below it |
| `descriptor.version` | Plugin's own version | Incremented on each release; shown in the store |
| APK `versionCode` | System install version | Incremented on each release; managed by PackageManager |

Compatibility matrix:

| Plugin apiVersion | App apiVersion | Behavior |
| --- | --- | --- |
| Equal | — | Normal |
| Plugin < App | — | Allowed; capability detection degrades as needed |
| Plugin > App | — | Refused; prompts the user to upgrade the App |

---

## 13. Complete example: the Demo plugin (JS + native)

The Demo plugin is the SDK's integration case: one real npm plugin package plus one companion APK, both in this repository.

### 13.1 Project structure

```
demo/js/                   ← JS plugin (dsh-plugin-demo 0.1.0)
├── package.json                   ← dsh.client / dsh.native declarations
├── cordis.patch.yml
├── lib/
│   ├── index.js                   ← entry: name / apply
│   ├── constants.js               ← RPC prefix, componentId, settings ns
│   ├── state.js                   ← in-memory state (counter starts at 42)
│   ├── rpc.js                     ← /api/demo.* routes + envelope
│   ├── actions.js                 ← mobileUi actions: bump / reset
│   ├── catalog.js                 ← registration catalog for demo.catalog
│   └── client.js                  ← Web fallback (settings.section + composer.dock)
└── test/rpc.test.mjs

demo/native/               ← companion APK (demo.native-ui)
├── build.gradle.kts               ← companion APK build config (see demo/README)
└── src/main/
    ├── AndroidManifest.xml        ← see section 4
    └── kotlin/com/dsh/mobile/dsh_plugin_demo/
        ├── DemoPluginService.kt
        ├── DemoNativePlugin.kt
        ├── DemoSettingsPage.kt
        ├── DemoDockContribution.kt
        ├── DemoSlotContribution.kt
        └── DemoRpc.kt
```

### 13.2 Identity pairing

| Location | Value |
| --- | --- |
| JS package name | `dsh-plugin-demo` (0.1.0, entry `lib/index.js`) |
| Companion `pluginId` (manifest `dsh.plugin.id`) | `demo.native-ui` |
| Companion APK package name | `com.dsh.mobile.dsh_plugin_demo` |
| manifest `dsh.plugin.jsPackage` | `dsh-plugin-demo` |
| Settings page Web registration id / native `PageEntry.id` | `demo` |
| Settings page `componentId` | `dsh-plugin-demo#demo` |
| Dock Web registration id / `componentId` | `demo-dock` / `dsh-plugin-demo#demo-dock` |

Only when "JS registers `settings.section(id = "demo")`" and "the companion registers
`PageEntry(id = "demo", placement = SETTINGS)`" both hold does the App skip the WebView.

### 13.3 All native-side code

Entry: `descriptor` matches the manifest; it registers the settings page, ready slots, and the dock. The Demo covers all 22
`NativeSlot` entries, 21 of which inject real UI; `WEB_ISLAND_FULL`'s host is ready, but it is the
full-island Web fallback container and the Demo does not inject it (to avoid covering the whole App), listing it only on the settings page:

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

Settings page: `PageContent` receives an empty spec and reads authoritative state via RPC; "server +1" is sent back with `dispatchEvent`,
the Host action changes state and the bridge queues `client/refresh`; "reset" calls `demo.reset` directly:

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

// 页面内：读 → 事件 → 重拉
LaunchedEffect(rpc, refreshKey) {
    val body = rpcHost.demoCall(DemoRpc.STATUS)          // demo.status
    if (!isActive()) return@LaunchedEffect
    DemoRpc.value(body)?.let { status = DemoRpc.statusOf(it) }
}

host.dispatchEvent(
    DemoRpc.PAGE_COMPONENT_ID,                           // dsh-plugin-demo#demo
    DemoRpc.EVENT_SUBMIT,                                // "submit"
    buildJsonObject { put("actionId", "bump"); put("by", 1) },
)

rpcHost.demoCall(DemoRpc.RESET)                          // demo.reset
```

Dock: `SlotContribution` + `PluginPopover`, again reading only through RPC and writing only through events:

```kotlin
class DemoDockContribution : SlotContribution() {
    override val slot: NativeSlot = NativeSlot.COMPOSER_DOCK
    override val supportedTypes: Set<String> = setOf("demo.dock")
    override val order: Int = 100

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        // 胶囊显示 "Demo <count>"；popover 内按钮回传
        // host.dispatchEvent(DemoRpc.DOCK_COMPONENT_ID, DemoRpc.EVENT_SUBMIT, payload)
    }
}
```

`DemoRpc.kt` centralizes method names, component identities, envelope parsing, and the `demoCall` extension; `DemoSlotContribution.kt`
places recognizable markers at the remaining ready slots; `DemoRenderer` (defined in `DemoNativePlugin.kt`) provides the minimal
`ComponentRenderer`. See [demo/native](../demo/native) for the complete code.

### 13.4 JS-side support

The entry declares no top-level `inject`; both capabilities use `ctx.inject`:

```js
export const name = 'dsh-plugin-demo';

export function apply(ctx) {
  const state = createDemoState();
  registerDemoRpc(ctx, state);        // ctx.inject(['connection']) → /api/demo.*
  registerDemoActions(ctx, state);    // ctx.inject(['mobileUi']) → bump / reset
}
```

Request envelope (payload is passed as-is, not wrapped in `args`):

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

Response envelope:

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

Business failures also return HTTP 200, with the error in `result.error`:

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

Six endpoints:

| Endpoint | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | Updated status snapshot |
| `demo.reset` | `{}` | Reset status snapshot |
| `demo.echo` | Any JSON | `{ received: <echoed as-is> }` |

Error codes: `demo/bad-request` (400), `demo/unsupported-media-type` (415),
`demo/unknown-endpoint` (HTTP 200 + `result.error`).

Web fallback (`lib/client.js`) registers two Web slots and reads state through the same channel:

```js
ctx.slots.register({ name: "settings.section", id: "demo", order: 10 /* … */ }, DemoSettingsSection);
ctx.slots.register({ name: "conversation.composer.dock", id: "demo-dock", order: 10 /* … */ }, DemoDockChip);
// read: connection.rpc.call("/api", "demo.status", payload)
```

Self-check output:

```bash
$ node --test demo/js/test/rpc.test.mjs
# 7/7 pass：路由、信封、bump/reset、payload 透传、动作隔离、状态隔离

$ python tools/plugin_ui_scanner.py --plugin demo/js \
    --companion demo/native/src/main/kotlin --check
plugin UI coverage check OK
```

The scanner's slot inventory (2 registered slots, all mapped to NativeSlots, hosted, and implemented by the companion) is archived in
[plugin UI inventory example](plugin-ui-inventory-example.md).

### 13.5 The flow from the user's perspective

1. The user installs the JS plugin on the App's plugins page (existing flow, unchanged).
2. The store page shows "Demo plugin · native enhancement": screenshots, capability declarations (open URL, isolated storage), signing fingerprint, and "compatible with the current App ✓".
3. One-tap install → system installer → automatic App discovery, signature verification, and ABI check.
4. The "Demo plugin" entry on the settings page and the dock in the chat input bar upgrade to native rendering.
5. The user uninstalls the native APK → the plugin automatically falls back to the WebView, fully functional.

---

## 14. Prohibited items at a glance

| Prohibited | Reason |
| --- | --- |
| Business logic inside the APK (authorization flows, calling model APIs) | Iron rule: logic lives in the Host |
| Storing/transmitting tokens, secrets, or API keys | Credentials never leave the Host |
| Requesting system permissions (`<uses-permission>`) | Capabilities go through the whitelist bridge |
| Hard-coded colors/font sizes | Theme contract |
| Reflective access to App-internal classes | Security boundary |
| Nested dynamic loading (loading a dex from inside a dex) | Review red line |
| Assuming the native side is present (JS features depending on the APK) | Degradation contract |
| Changing a published `dsh.plugin.id` | Identity anchor |
| Publishing the same id under a different signature | Anti-tampering |
