> English | [中文](README.md)

# Demo plugin (JS + native)

> The only sample plugin in this SDK repository, made of two artifacts:
>
> - **JS half**: [`demo/js`](js/README.md) — npm package `dsh-plugin-demo`;
> - **Native half**: [`demo/native`](native/) — Kotlin/Compose companion APK.
>
> Pure demo: no network access, no account access, no credentials. State lives only in
> the Host process and returns to the initial value 42 after a Host restart. It
> demonstrates every native-UI integration path, and the code runs as-is.

## Identity

| Field | Value |
| --- | --- |
| JS package name | `dsh-plugin-demo` (0.1.0) |
| Companion `pluginId` (manifest `dsh.plugin.id`) | `demo.native-ui` |
| Companion APK package name | `com.dsh.mobile.dsh_plugin_demo` |
| Manifest `dsh.plugin.jsPackage` | `dsh-plugin-demo` |
| Manifest `dsh.plugin.version` | `1` (integer) |
| Companion APK `versionName` | `1.0.0` |
| Settings-page Web registration id / native `PageEntry.id` | `demo` |
| Settings-page component identity `componentId` | `dsh-plugin-demo#demo` |
| Dock Web registration id | `demo-dock` |
| Dock component identity `componentId` | `dsh-plugin-demo#demo-dock` |

Package names follow `com.dsh.mobile.<full-plugin-name>`: `dsh-plugin-demo` →
`com.dsh.mobile.dsh_plugin_demo`. `dsh.plugin.id` is decoupled from the package name;
do not change either once published.

## Layout

```text
demo/
├─ README.md                        ← this file
├─ js/                              ← JS half (npm package dsh-plugin-demo)
│  ├─ package.json                  ← npm identity + dsh.client / dsh.native declarations
│  ├─ cordis.patch.yml              ← bundle self-activation: mounts lib/index.js
│  ├─ lib/
│  │  ├─ index.js                   ← plugin entry: name / apply
│  │  ├─ constants.js               ← RPC prefix, componentId, settings ns (shared contract)
│  │  ├─ state.js                   ← in-memory state store (count 42, last action, updatedAt)
│  │  ├─ rpc.js                     ← /api/demo.* routes + request/response envelopes
│  │  ├─ actions.js                 ← mobileUi actions: bump / reset
│  │  ├─ catalog.js                 ← registration list returned by demo.catalog
│  │  └─ client.js                  ← Web half: settings.section + composer.dock
│  ├─ locale/{zh,en}.json           ← Web fallback UI copy
│  └─ test/rpc.test.mjs             ← node:test: envelopes, routes, actions, state isolation
└─ native/                          ← native half (companion APK)
   ├─ build.gradle.kts              ← App version catalog + :plugin:native-sdk
   └─ src/main/
      ├─ AndroidManifest.xml
      └─ kotlin/com/dsh/mobile/dsh_plugin_demo/
         ├─ DemoPluginService.kt    ← Service binding + static factory plugin()
         ├─ DemoNativePlugin.kt     ← DshUiPlugin: descriptor, renderer, slot registration
         ├─ DemoSettingsPage.kt     ← PageContent: full settings page (RPC-driven)
         ├─ DemoDockContribution.kt ← COMPOSER_DOCK chip + PluginPopover
         ├─ DemoSlotContribution.kt ← demo contributions for the remaining NativeSlots
         └─ DemoRpc.kt              ← envelope parsing + demoCall
```

## Integration paths covered

- **PageContent**: `DemoSettingsPage`, `id = "demo"`. The settings page shows a
  "Demo plugin" entry and opens a full native Compose page;
- **NativeSlot**: `DemoSlots.entries` lists 22 slots, of which **21 inject real UI**;
  `WEB_ISLAND_FULL` is the full-island Web fallback container. Demo intentionally does
  not inject it (it would cover the whole App); the Demo settings page lists and
  explains it instead;
- **PluginPopover**: tapping the `Demo 42` chip in the dock opens a popover anchored
  above the chip, right-aligned, using the popup material from `PluginTheme` (the App
  menu material);
- **ComponentRenderer**: `DemoRenderer` demonstrates the Host card rendering entry
  (`supportedTypes = setOf("demo.card")`);
- **RPC + event loop**: the settings page and the dock both read Host state over RPC and
  send user actions back to the JS plugin through `dispatchEvent`.

Slot list:

```text
SETTINGS_SHEET            SETTINGS_PROVIDER_CARD   SIDEBAR_PANEL
COMPOSER_DOCK             COMPOSER_DOCK_ACTIVITY   COMPOSER_DOCK_USAGE
COMPOSER_DOCK_CONTEXT     COMPOSER_LEFT            COMPOSER_RIGHT
COMPOSER_MODEL            COMPOSER_PERMISSION      COMPOSER_PLAN
COMPOSER_ATTACHMENTS      COMPOSER_BAR             CONVERSATION_VIEW
SESSION_HEADER_CORNER     COMPOSER_OVERLAY         PLUGIN_OVERLAY
MESSAGE_ACTION            TOOL_VIEW_SHEET          CHAT_STREAM
WEB_ISLAND_FULL           ← listed but not injected
```

Of the 22 entries, all except `WEB_ISLAND_FULL` register a `SlotContribution` (the dock
uses `DemoDockContribution` separately). See
[`../slot-mapping.md`](../slot-mapping.md) for the full slot-to-host mapping.

## RPC contract

The native side calls `PluginRpcHost.callRpc("demo.<endpoint>", payload)`; the Web side
calls `connection.rpc.call("/api", "demo.<endpoint>", payload)`. Both use the same
channel.

Request body (`payload` is passed through as-is; it is not wrapped in `args`):

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

Successful response:

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

Business failures also return HTTP 200; the error is in `result.error`:

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

Only transport errors use a non-200 status: `400` (JSON cannot be parsed or the
envelope does not match) and `415` (not `application/json`). Error codes:
`demo/bad-request` (400), `demo/unsupported-media-type` (415), `demo/unknown-endpoint`
(HTTP 200 + `result.error`).

| Endpoint | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | status snapshot after the update |
| `demo.reset` | `{}` | status snapshot after the reset |
| `demo.echo` | any JSON | `{ received: <echoed as-is> }` |

The settings page reads `demo.status` + `demo.catalog`; "Server +1" goes through the
event path, while "Reset" calls `demo.reset` directly. The dock also reads
`demo.status` and offers "Server +1 · event", "Refresh · RPC" and "Reset · RPC" in its
popover.

## Native event contract

Native controls never execute plugin JS; they only report "which component, which
action, what payload":

| componentId | actionId | payload | Effect |
| --- | --- | --- | --- |
| `dsh-plugin-demo#demo` | `bump` | `{ actionId: "bump", by?: number }` | count += n |
| `dsh-plugin-demo#demo` | `reset` | `{ actionId: "reset" }` | reset to 42 |
| `dsh-plugin-demo#demo-dock` | `bump` | `{ actionId: "bump", by?: number }` | count += n |
| `dsh-plugin-demo#demo-dock` | `reset` | `{ actionId: "reset" }` | reset to 42 |

The bridge limits event names to `press` / `input` / `toggle` / `submit`; Demo uses
`submit` everywhere. Full path:

```text
native control onClick
  → host.dispatchEvent(componentId, "submit", { actionId: "bump", by: 1 })
  → App bridge client-slots/dispatch
  → bridge hits the JS plugin's mobileUi.registerAction
  → Host state changes; bridge queues client/refresh
  → native page collects refreshEvents → calls callRpc("demo.status") again
```

"Reset" is a pure RPC write path and does not go through events. `DemoRpc.kt` centralizes
method names, component identities and envelope parsing.

## Build (performed in the App repository)

The SDK repository has no Gradle Wrapper; `demo/native` is built inside the App
development repository. `demo/native/build.gradle.kts` uses the App repository's version
catalog and depends on the App's `:plugin:native-sdk` module.

1. Copy `demo/native` into the App repository as `samples/demo-native`;
2. Add the module to `settings.gradle.kts` in the App repository:

   ```kotlin
   include(":samples:demo-native")
   ```

3. Build from the **App repository root**:

   ```bash
   ./gradlew :samples:demo-native:assembleDebug
   ```

4. Output:

   ```text
   samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk
   ```

To build a companion APK for your own plugin you do not need an App module: use
[`../artifacts/dsh-native-ui-sdk-release.aar`](../artifacts/dsh-native-ui-sdk-release.aar)
or the sources in [`../sdk/`](../sdk/README.md); see section 2 of the root
[README](../README.md).

## Install and device verification

```bash
# 1) Install the JS plugin into the Host profile (local path)
dsh plugin --profile web add ./demo/js

# 2) Install the companion APK
adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk

# 3) Bring the App to the foreground and wait for the rescan (APKs are rediscovered after install/uninstall)
```

Verification steps:

1. Settings → Plugin UI shows "Demo plugin"; opening it shows the native page with the
   count, last action and update time;
2. A `Demo 42` chip appears above the input bar;
3. Tap the chip: the popover sits above it, right-aligned, using the App menu material;
4. Tap "Server +1" and the count changes; tap "Reset" and it returns to 42;
5. Areas such as the message stream, tool detail and settings page show `Demo · ...`
   markers, and the remaining slots show a `D` badge (21 injected slots in total);
6. After uninstalling the Demo APK, the settings page and dock fall back to the Web
   version and can still read `demo.status`.

## Self-test

```bash
# RPC / actions / state isolation (no Host, Android or network required)
node --test demo/js/test/rpc.test.mjs

# Scanner: both JS-registered slots have a NativeSlot mapping, host and companion implementation
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

The scanner ships with the mapping/host snapshot in
[`../tools/sdk-mapping.json`](../tools/sdk-mapping.json) and runs directly from the SDK
repository root without the App repository; it prints `plugin UI coverage check OK` with
2 slots `impl=True`. To verify host sources on the App side, pass the optional
`--root <App repo>`. The result is archived in
[`../docs/plugin-ui-inventory-example.md`](../docs/plugin-ui-inventory-example.md).

## Related documents

- [SDK main document](../README.md): AAR integration, manifest, pages/slots, RPC, build and publishing;
- [SDK developer guide](../docs/plugin-sdk-guide.md): from packaging to slots, RPC and lifecycle;
- [Source-driven native UI flow](../docs/source-driven-native-ui.md): scan → gaps → implementation;
- [Plugin UI inventory example](../docs/plugin-ui-inventory-example.md): Demo's real scanner output;
- [Publishing and tagging](../docs/publishing.md): publishing conventions for plugins and the SDK;
- [Docs site case](../docs/index.html#case): Demo's integration checklist and verification output.
