> English | [中文](plugin-conversion-guide.md)

# Converting JS UI to Native UI: A Companion APK Field Guide

> Status: **The Demo plugin is the complete reference implementation.** This document uses `dsh-plugin-demo` (Web half
> `demo/js`) and `demo/native` (the Kotlin/Compose companion APK) as examples to
> explain how to migrate an existing React/Web settings page and dock into a native page/anchor point while keeping a complete Web
> fallback. The Demo is a purely fictional demonstration: it does not access the network, does not read or write accounts, holds no credentials, and its state lives only in
> the Host process (initial count 42).
> Full source: [Demo plugin JS half](../demo/js/README.md) ·
> [scan inventory](plugin-ui-inventory-example.md) · [docs-site case](index.html#case).
> Prerequisite reading: [SDK overview and API](plugin-sdk-guide.md).
> Updated: 2026-10-22.

## Table of Contents

- [1. Understand the migration boundary first](#1-understand-the-migration-boundary-first)
- [2. Inventory the Web UI, don't translate JSX directly](#2-inventory-the-web-ui-dont-translate-jsx-directly)
- [3. Set up the Android project](#3-set-up-the-android-project)
- [4. Register the three identities](#4-register-the-three-identities)
- [5. Write the entry point, Service, and page](#5-write-the-entry-point-service-and-page)
- [6. Connect Host state and actions](#6-connect-host-state-and-actions)
- [7. Complete the Web interactions](#7-complete-the-web-interactions)
- [8. Refresh, polling, and page lifecycle](#8-refresh-polling-and-page-lifecycle)
- [9. WebView takeover and uninstall fallback](#9-webview-takeover-and-uninstall-fallback)
- [10. Full integration checklist](#10-full-integration-checklist)
- [11. Integration acceptance checklist](#11-integration-acceptance-checklist)

---

## 1. Understand the migration boundary first

The goal of the migration is: **Host logic stays untouched, a native frontend is added, and the Web UI remains fully usable when the companion APK is absent.**

| Layer | Modified? | Responsibility |
| --- | --- | --- |
| Host JS (RPC, state, actions) | Not in principle | Authoritative state, side effects, and business logic |
| Web React UI | Not removed | Desktop UI + mobile fallback path |
| Native companion APK | Added | Compose presentation, local UI state, calling the Host |

The native APK is not a standalone business app, and it does not duplicate business state. It calls existing Host
interfaces through the App host: the count, last action, and update time on the Demo native settings page all come from `demo.status`; there is
no second counter inside the APK. If a real plugin holds credentials such as tokens or API keys, they likewise stay only in the Host and never enter the companion
page.

**Accept functionality first, then appearance.** "The page became native" does not mean the migration is done; buttons, expandable sections, refresh,
write operations, and back navigation must all work.

## 2. Inventory the Web UI, don't translate JSX directly

First read the Web registration code and state protocol, and compile four tables.

### 2.1 Slots and component identity

The Demo's Web half registers two anchor points (`demo/js/lib/client.js`):

```js
ctx.slots.inject("settings.section", () => ctx.slots.register({
  name: "settings.section",
  id: "demo",
  order: 10,
  label: () => t("nav"),
  inject: () => ({ call, t }),
}, DemoSettingsSection));

ctx.slots.inject("conversation.composer.dock", () => ctx.slots.register({
  name: "conversation.composer.dock",
  id: "demo-dock",
  order: 10,
  inject: () => ({ call }),
}, DemoDockChip));
```

The component identities the bridge actually produces are:

```text
dsh-plugin-demo#demo
dsh-plugin-demo#demo-dock
```

Record `slot`, `id`, package name, and page title. Page takeover requires the JS page ID and the native `PageEntry.id`
to be exactly the same (both are `demo` in the Demo); when the dock sends an event back, the `componentId` must also be
`dsh-plugin-demo#demo-dock`.

### 2.2 State structure

Extract the fields the page actually needs from the RPCs called by the Web side and from `lib/catalog.js`. The Demo needs only two groups:

- `demo.status`: `greeting`, `count`, `lastAction`, `updatedAt`, `startedAt`,
  `uptimeMs`;
- `demo.catalog`: `plugin`, `companion.pluginId`, `companion.apkPackage`,
  `companion.sample`, `registrations[].slot`, `registrations[].id`,
  `registrations[].componentId`, `registrations[].nativeSlot`,
  `registrations[].purpose`, `endpoints[]`.

**Pass display data only.** The Demo has no credential fields; real plugins must not put tokens, API keys,
or Authorization headers into RPC return values or slot specs either.

### 2.3 Actions and state changes

| Web action | Host method | Required payload |
| --- | --- | --- |
| Read page state | `demo.status` | `{}` |
| Read integration inventory | `demo.catalog` | `{}` |
| Liveness check | `demo.ping` | `{}` |
| Count +n | `demo.bump` | `{ by?: number }` |
| Reset to 42 | `demo.reset` | `{}` |
| Echo check | `demo.echo` | Any JSON |

These map to three paths on the native side:

| Web action | Native approach |
| --- | --- |
| Open / refresh | `PluginRpcHost.callRpc("demo.status", {})`, or reread after `client/refresh` |
| Server +1 | `dispatchEvent("dsh-plugin-demo#demo", "submit", { actionId:"bump", by:1 })` |
| Reset | Directly `callRpc("demo.reset", {})`, a pure-RPC write path |

### 2.4 Interaction completeness

List each item; migrating only the card shell is not allowed:

- Which fields does the settings page show? How are loading, RPC-unavailable, and empty-data states displayed?
- What does the dock chip show? Does tapping expand a popover, navigate to a page, or something else?
- Do write operations have a busy guard against duplicates? Can they be retried after failure?
- Is the information on the native page consistent with the Web fallback page? Does a refresh lose state?
- Does back navigate only one level up?

The Demo has only two anchor points, but each must be accepted individually: title, data, failure states, actions, and back navigation must all be verifiable.

## 3. Set up the Android project

The companion is an independent **application** module:

```kotlin
// 伴侣 APK 的 build.gradle.kts（关键片段）
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dsh.mobile.dsh_plugin_demo" // 不要用 Java 关键字 native
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dsh.mobile.dsh_plugin_demo"
        minSdk = 26
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    // SDK 依赖：把 artifacts/dsh-native-ui-sdk-release.aar 复制到 libs/
    implementation(files("libs/dsh-native-ui-sdk-release.aar"))
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
```

For the Demo's complete Gradle configuration and build steps, see [demo/README.md](../demo/README.md) (run in the App repository);
when a standalone project has no Gradle version catalog, write explicit Compose and serialization coordinates according to "Dependency integration" in the root [README](../README.md).

### ClassLoader: load by convention

The host uses a **parent-first ClassLoader**: the main App owns the single `DshUiPlugin`, `PageRegistry`,
and Compose classes, and the companion only supplies its own implementation classes. Load by the following conventions so that
the object returned by `Service.plugin()` is recognized by the host as a `DshUiPlugin`:

- The host shares SDK and Compose types; the companion does not package a second copy;
- Do not create child-first/isolated ClassLoaders yourself;
- Do not treat another `PageRegistry` as the host registry;
- Do not dynamically load dex again in the companion to bypass the ABI gate;
- Do not assume classes returned by `createPackageContext()` are naturally compatible with App types.

After the App upgrades the SDK, if the companion ABI is incompatible, it should stop at the gate and fall back to Web, rather than force-casting.

## 4. Register the three identities

These are three different concepts; configure them separately according to the table below:

| Identity | Demo value | Purpose |
| --- | --- | --- |
| `pluginId` | `demo.native-ui` | The companion plugin's own identity and page registration ownership |
| `jsPackage` | `dsh-plugin-demo` | Pairing with the Host's JS plugin |
| `PageEntry.id` | `demo` | The specific `settings.section` to take over from the Web |

The settings page component identity is `dsh-plugin-demo#demo`, and the dock is `dsh-plugin-demo#demo-dock`;
they consist of `<jsPackage>#<id inside slot>`, not of `pluginId`.

### 4.1 Manifest

meta-data must be placed at the **Service level**:

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
            <meta-data
                android:name="dsh.plugin.jsPackage"
                android:value="dsh-plugin-demo" />
            <intent-filter>
                <action android:name="com.deepseek.dsh.action.PLUGIN_UI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

`dsh.plugin.id` **is not required to equal the JS package name**: in the Demo it is `demo.native-ui`, while the JS package name is
`dsh-plugin-demo`. Actual routing uses `jsPackage + "#" + id inside slot` to match Web components, and uses
`pluginId` to isolate pages registered by different companions.

### 4.2 Scoped package names and versions

The `/` in `@scope/pkg` is part of the package name and must not be truncated; `#` is the component separator. Routing splits on the last `#`.
Versioned `@scope/pkg@1.2.3` should be normalized to `@scope/pkg`. Git URLs cannot be used to guess npm package names by string manipulation;
use the Host's actual component identity or explicit pairing metadata instead. The Demo has an unscoped package name; pairing relies on the
manifest's `dsh.plugin.jsPackage`, not on string inference.

## 5. Write the entry point, Service, and page

### 5.1 The Service must provide a static factory

`onBind` serves binding scenarios; when a page is loaded into the App process, the static `plugin()` factory is used. The Service
implements both `onBind` and the static factory:

```kotlin
class DemoPluginService : Service() {
    override fun onBind(intent: Intent): IBinder = DshPluginBinder(DemoNativePlugin())

    companion object {
        @JvmStatic
        fun plugin(): DshUiPlugin = DemoNativePlugin()
    }
}
```

### 5.2 Register pages and anchor points in the entry point's init

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

`DemoSlots` lists 22 native anchor points: 21 of them inject real UI (the dock is handled separately by
`DemoDockContribution`); `WEB_ISLAND_FULL`'s host is ready but must be reserved for the full-island Web fallback, so the
Demo does not inject it, and only lists the reason on the Demo page.

### 5.3 `PageEntry.id` must exactly match the Web registration ID

```kotlin
class DemoSettingsPage : PageContent {
    override val entry = PageEntry(
        id = "demo", // 对应 JS settings.section id，不是显示标题
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

The Web is taken over only when **the companion loads successfully + the corresponding page is found**. Merely discovering the APK, having only a Service, or having only a
`ComponentRenderer` is not enough to take over a `settings.section`.

## 6. Connect Host state and actions

There are two channels; choose by scenario.

### 6.1 Generic events: `dispatchEvent`

Suitable for components that already have a `mobileUi.registerAction` listener. The Demo's "server +1" button:

```kotlin
val payload = buildJsonObject {
    put("actionId", "bump")
    put("by", 1)
}
host.dispatchEvent("dsh-plugin-demo#demo", "submit", payload)
```

The chain is: native event → `client-slots/dispatch` → JS plugin `mobileUi` action (`bump` in `lib/actions.js`)
→ Host state change → bridge queues `client/refresh` → the native page's `refreshEvents`
receives the notification and rereads `demo.status`. Event names use the bridge-allowed `press` / `input` / `toggle` /
`submit`; the Demo uniformly uses `submit`.

### 6.2 Pages directly read/call existing RPCs: `PluginRpcHost`

Suitable for complex pages that already have REST-style plugin RPCs. A complete host page provides an optional `PluginRpcHost`.
The Demo uses `DemoRpc.kt` to wrap the callback-style API into suspend functions, so the page only cares about success/failure:

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
val rpcHost = rpc ?: return@LaunchedEffect
val body = rpcHost.demoCall(DemoRpc.STATUS)
if (!isActive()) return@LaunchedEffect
val value = DemoRpc.value(body) // 解 result.ok 并取 value
if (value != null) status = DemoRpc.statusOf(value)
else error = DemoRpc.errorMessage(body, "无法读取 demo.status")
```

Notes:

1. Use Host routes that **already exist** (the Demo uses `demo.status` / `demo.catalog` /
   `demo.ping` / `demo.bump` / `demo.reset` / `demo.echo`); do not invent
   methods like `component.state`, `component.dispatch` that do not exist;
2. The payload is sent as-is, without an extra `args` wrapper;
3. The host response is usually `{ result: { ok, value | error } }`; check `ok` first and do not treat the whole
   envelope as business state; business failures also return HTTP 200, with the error code in `result.error`;
4. Callbacks are not suspended waits of `LaunchedEffect`. Wrap them in suspend or use `refreshKey` to trigger the next
   read; do not read stale state immediately after calling `callRpc()`;
5. Use `busy` to prevent duplicate submissions, show errors, and make buttons retryable after failure.

A safe refresh skeleton:

```kotlin
var refreshKey by remember { mutableStateOf(0) }
var status by remember { mutableStateOf<DemoStatus?>(null) }
var error by remember { mutableStateOf<String?>(null) }

fun refresh() { refreshKey += 1 }

LaunchedEffect(rpc, refreshKey) {
    val rpcHost = rpc ?: return@LaunchedEffect
    val body = rpcHost.demoCall(DemoRpc.STATUS)
    if (!isActive()) return@LaunchedEffect
    val value = DemoRpc.value(body)
    if (value != null) {
        status = DemoRpc.statusOf(value)
        error = null
    } else {
        error = DemoRpc.errorMessage(body, "无法读取 demo.status")
    }
}
```

## 7. Complete the Web interactions

### 7.1 Local UI state belongs to native

Whether a popover is open, whether a button is busy, and input drafts should all be local native state; do not write them back to the Host:

```kotlin
var open by remember { mutableStateOf(false) }
var busy by remember { mutableStateOf(false) }
val current = status

Text(
    text = "Demo ${current.count}",
    modifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { open = !open })
    },
)
if (open) {
    PluginPopover(theme = theme, onDismiss = { open = false }) {
        // 展示 Host 数据 + 动作
    }
}
```

Implement popover content according to "Host data + write paths + dismissal": the Demo dock shows status, count,
last action, update time, and uptime, and provides three actions:

- "Server +1 · event": `dispatchEvent` → Host action → `client/refresh`;
- "Refresh · RPC": directly reread `demo.status`;
- "Reset · RPC": directly call `demo.reset`.

The settings page is implemented around "state, integration inventory, actions"; a popover must contain at least one write path and a dismissal method.

### 7.2 Use stable identities as local state keys

`remember(componentId)` or `remember(entry.slot)` isolates `open`, drafts, and error states across different anchor points and cards.
Preserve these local states across refreshes or recompositions, so that a popover does not close automatically and input is not cleared after every RPC return.

### 7.3 Mobile interaction mapping

| Web | Native suggestion |
| --- | --- |
| Chip / button tap | `clickable` or `pointerInput(detectTapGestures)`, toggling local state |
| Hover tooltip | Tap / long-press to expand `PluginPopover` |
| Input / form | Compose `TextField` + local draft, calling RPC on submit |
| Checkbox / switch | `Switch`, calling the Host on change, recoverable on failure |
| Modal management dialog | Expandable management area or Compose Dialog; pick one and stay consistent |
| New tab / external link | System browser (`OPEN_URL`) |
| Periodic polling | Loop inside `LaunchedEffect(key)`; automatically cancelled when leaving the page or when conditions change |

The Demo only covers taps, popovers, and RPC actions; choose the remaining mappings according to the real plugin's interactions. Do not replicate nested web layouts
just to match the DOM one-to-one; preserve **functionality and information hierarchy**, and implement them with mobile-appropriate controls.

## 8. Refresh, polling, and page lifecycle

### 8.1 Receive Host refresh events

Pages that need to receive `client/refresh` can override the `Content` overload with `refreshEvents`:

```kotlin
@Composable
override fun Content(
    spec: JsonObject,
    theme: PluginTheme,
    host: PluginHostApi,
    refreshEvents: SharedFlow<String>?,
) {
    DemoSettingsContent(theme, host, refreshEvents)
}

// 只响应本组件 ID，然后重拉权威 RPC
LaunchedEffect(refreshEvents) {
    refreshEvents?.collect { componentId ->
        if (componentId == DemoRpc.PAGE_COMPONENT_ID || componentId == DemoRpc.JS_PACKAGE) {
            refresh()
        }
    }
}
```

Do not assume a generic `component.state` exists; after receiving an event, call the authoritative RPC again.

### 8.2 Polling must have an exit condition

Poll only while an asynchronous task is pending; when the condition disappears or the page is left, the loop ends; do not write a permanent `while (true)` unrelated to the page lifecycle:

```kotlin
// 例如等待授权/上传完成；条件消失、页面离开都会结束循环
val hasPending = /* 由 Host 状态推导 */
LaunchedEffect(hasPending) {
    if (!hasPending) return@LaunchedEffect
    while (hasPending) {
        delay(1_500)
        refresh()
    }
}
```

Periodic refresh is attached to composition and cancelled automatically when the page is left. The Demo dock rereads every 30s:

```kotlin
LaunchedEffect(rpc, status != null) {
    if (rpc == null || status == null) return@LaunchedEffect
    while (true) {
        delay(30_000)
        loadState()?.let { status = it }
    }
}
```

### 8.3 Late responses must not update a page that has been left

Use `PageLifecycle.LocalIsActive`:

```kotlin
val isActive = PageLifecycle.LocalIsActive.current

val body = rpcHost.demoCall(DemoRpc.STATUS)
if (!isActive()) return@LaunchedEffect
DemoRpc.value(body)?.let { status = DemoRpc.statusOf(it) }
```

Page components, RPC callbacks, and uninstall/back navigation race asynchronously. `remember` alone cannot stop late callbacks.

### 8.4 Back stack

Page switches must be keyed by **page identity**; in the `AnimatedContent` that observes route registration, use the current page's
`activePluginComponent` so that one back press goes up only one level. Verify all of the following:

- Native page → settings home;
- Web fallback page → settings home;
- Loading/unloading the companion while the native page is open does not make the page incorrectly go back two levels.

## 9. WebView takeover and uninstall fallback

### 9.1 Correct takeover conditions

All of the following must be satisfied:

1. PackageManager discovers the companion;
2. ABI/signature/App version gates pass;
3. The static `plugin()` successfully returns a `DshUiPlugin` recognized by the main App;
4. The companion registered a `PageContent` (Demo: `PageEntry(id = "demo", placement = SETTINGS)`);
5. `jsPackage#PageEntry.id` matches the current Web component (Demo: `dsh-plugin-demo#demo`).

Once satisfied, the native branch **does not create a WebView**. Otherwise, continue using the original `PluginComponentPane`; a blank native page is not allowed.
The dock follows another path: the companion takes over only after registering a `SlotContribution` for `COMPOSER_DOCK` in `SlotRegistry`
(the Demo uses `DemoDockContribution`); without a contribution, the Web `DemoDockChip` keeps showing.

### 9.2 Package changes and old-instance cleanup

The host watches `PACKAGE_ADDED`, `PACKAGE_REMOVED`, and `PACKAGE_REPLACED` and rescans. On uninstall or load
failure, remove the old companion instance from the page registry, then publish a new route snapshot. Otherwise, an open page keeps
holding the uninstalled APK's ClassLoader and old pages.

### 9.3 Fallback matrix that must be verified

| Operation | Expectation |
| --- | --- |
| Companion not installed, open settings page | `DemoSettingsSection` Web UI, RPC works |
| Companion not installed, view composer | `DemoDockChip` chip shows; tapping can call RPC |
| Install companion, return to App | Native settings page / dock takes over, no Host changes needed |
| Uninstall APK while companion page is open | Current page immediately switches back to Web UI |
| Reinstall companion | Native page restores |
| ABI incompatible / factory fails | Web fallback; no crash, no blank page |

## 10. Full integration checklist

### Build and static checks

```bash
# 1) 把 JS 插件装进 Host profile（在 SDK 仓库根目录）
dsh plugin --profile web add ./demo/js

# 2) RPC / 动作 / 状态隔离
node --test demo/js/test/rpc.test.mjs

# 3) 扫描器：两个落点都必须映射、有宿主、伴生实现
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

For the companion APK and App build steps, see the "Build" section of [demo/README.md](../demo/README.md):
run the Gradle build in the App repository (the SDK repository does not ship a Gradle Wrapper). The scanner includes the
`tools/sdk-mapping.json` mapping snapshot and can run directly in the SDK repository; to verify App-side host sources, optionally add
`--root /path/to/dsh-mobile-app`.

App-side routing unit tests should cover at least: real package name mapping, scoped package names, wrong page IDs, clearing on uninstall,
and fallback when no page matches.

### Device checks

After building and installing the App and companion APK in the App repository according to [demo/README.md](../demo/README.md):

```bash
adb -s emulator-5554 shell am start -n \
  com.dsh.mobile/com.deepseek.dsh.MainActivity
```

Verify in sequence:

- [ ] The native settings page appears, titled "Demo 插件", not just an edited subtitle;
- [ ] The initial count is 42 and the last action is `boot`, coming from `demo.status` rather than being hard-coded;
- [ ] The integration inventory comes from `demo.catalog` (two anchor points and the endpoint list);
- [ ] The native page initializes no WebView; after uninstalling the companion, the settings page returns to `DemoSettingsSection`;
- [ ] The composer shows a "Demo 42" chip; tapping it pops an anchored `PluginPopover`;
- [ ] The popover's "Server +1 · event" changes Host state via `dispatchEvent`, and the page rereads after refresh;
- [ ] "Reset · RPC" directly calls `demo.reset`, and the count returns to 42;
- [ ] The refresh button and 30s polling only read the Host and do not accumulate inside the APK;
- [ ] Back navigates up only one level and does not exit settings directly;
- [ ] Uninstalling the companion while the page is open falls back to Web immediately; reinstalling restores native;
- [ ] Dark mode, long text, empty lists, and Host RPC unavailability all have readable presentations;
- [ ] `node --test demo/js/test/rpc.test.mjs` is 7/7, and the scanner `--check` passes.

## 11. Integration acceptance checklist

| Check item | Correct approach |
| --- | --- |
| Identity values configured separately | Verify `pluginId` / `jsPackage` / `PageEntry.id` separately (Demo: `demo.native-ui` / `dsh-plugin-demo` / `demo`) |
| Service has both `onBind` and a static factory | `@JvmStatic fun plugin(): DshUiPlugin` |
| Parent-first shared host types | The companion does not package a second copy of SDK/Compose types |
| A matching `PageContent` is registered for the settings page | `PageEntry.id` exactly matches the JS `settings.section` id |
| RPC methods really exist | Use Host methods and real payloads that already exist (`demo.status`, etc.) |
| Read values through callbacks or a suspend wrapper | Drive reads with `demoCall` or `refreshKey` |
| Anchor points have complete content and actions | List interactions per section 2.4, then implement them one by one |
| `componentId` matches the JS registration | `dsh-plugin-demo#demo` / `dsh-plugin-demo#demo-dock` |
| Navigation is keyed by page identity | One back press goes up only one level |
| Remove old registrations on package changes | Rescan + remove old registrations + publish a new snapshot |
| Late callbacks check whether the page is still active | `PageLifecycle.LocalIsActive` |
| Do scanning and installation on IO threads | Publish snapshots on the main thread only |
| Accept both functionality and fallback | Exercise each item on a real device and verify RPC and Web fallback |

Complete reference implementation: `demo/native` (Web half `demo/js`; JS identity, RPC, and
event contract in [README](../demo/js/README.md)). App-side routing and lifecycle reference:
[reference/native-host](../reference/native-host/). Scan report:
[Demo plugin UI inventory](plugin-ui-inventory-example.md).
