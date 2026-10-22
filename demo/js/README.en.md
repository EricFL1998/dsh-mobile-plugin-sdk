> English | [中文](README.md)

# Demo plugin (JS half)

The SDK's **integration case**: a real npm plugin package paired with
[`demo/native`](../native) (a Kotlin/Compose companion APK). It shrinks the full
"JS plugin + native UI" path into a few hundred readable lines and demonstrates:

- Host-side RPC: `demo.status` / `demo.catalog` / `demo.ping` / `demo.bump` /
  `demo.reset` / `demo.echo`;
- Native event round-trip: native button → `dispatchEvent` → JS plugin `mobileUi`
  action → bridge `client/refresh` → native page re-reads RPC;
- Component identity pairing: the Web registrations for `settings.section` and
  `conversation.composer.dock` match the native slots, `PageEntry.id` and
  `componentId` one-to-one;
- Web fallback: without the companion APK installed, the React components for the
  settings page and dock still work, just over the Web.

> Purely fictional demo: no network access, no account access, no credentials. State
> lives only in the Host process and returns to its initial value (count 42) after a
> Host restart.

## Layout

```
demo/js/
├── package.json          # npm identity + dsh.client / dsh.native declarations
├── cordis.patch.yml      # bundle self-activation: mounts lib/index.js
├── lib/
│   ├── index.js          # plugin entry: name / apply
│   ├── constants.js      # RPC prefix, componentId, settings ns (shared contract)
│   ├── state.js          # in-memory state store (count 42, last action, updatedAt)
│   ├── rpc.js            # /api/demo.* routes + request/response envelopes
│   ├── actions.js        # mobileUi actions: bump / reset
│   ├── catalog.js        # registration list returned by demo.catalog
│   └── client.js         # Web half: settings.section + composer.dock
├── locale/{zh,en}.json   # Web fallback UI copy
└── test/rpc.test.mjs     # node:test: envelopes, routes, actions, state isolation
```

## Identity pairing with the companion APK

| Location | Value |
| --- | --- |
| JS package name | `dsh-plugin-demo` |
| Companion `pluginId` (manifest `dsh.plugin.id`) | `demo.native-ui` |
| Companion APK package name | `com.dsh.mobile.dsh_plugin_demo` |
| Manifest `dsh.plugin.jsPackage` | `dsh-plugin-demo` |
| Settings-page Web registration id / native `PageEntry.id` | `demo` |
| Settings-page component identity `componentId` | `dsh-plugin-demo#demo` |
| Dock Web registration id | `demo-dock` |
| Dock component identity `componentId` | `dsh-plugin-demo#demo-dock` |

Two conditions are required for the native side to take over the settings page:

1. The JS side registers `settings.section` with `id = "demo"`;
2. The companion APK registers `PageEntry(id = "demo", placement = SETTINGS)`.

Both component identities are `dsh-plugin-demo#demo`; only then does the App skip the
WebView.

## RPC contract

The App / native side calls `PluginRpcHost.callRpc("demo.<endpoint>", payload)`; the Web
side calls `connection.rpc.call("/api", "demo.<endpoint>", payload)`.

Request body (same as real plugins: `payload` is passed through as-is and is not wrapped
in `args`):

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

Response body:

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

Business failures also return HTTP 200; the error is in `result.error`:

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

| Endpoint | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | status snapshot after the update |
| `demo.reset` | `{}` | status snapshot after the reset |
| `demo.echo` | any JSON | `{ received: <echoed as-is> }` |

## Native event contract

Native controls never execute plugin JS; they only report "which component, which
action, what payload":

| componentId | actionId | payload | Effect |
| --- | --- | --- | --- |
| `dsh-plugin-demo#demo` | `bump` | `{ actionId:"bump", by?:number }` | count += n |
| `dsh-plugin-demo#demo` | `reset` | `{ actionId:"reset" }` | reset to 42 |
| `dsh-plugin-demo#demo-dock` | `bump` | `{ actionId:"bump", by?:number }` | count += n |
| `dsh-plugin-demo#demo-dock` | `reset` | `{ actionId:"reset" }` | reset to 42 |

The bridge limits event names to `press` / `input` / `toggle` / `submit`; Demo uses
`submit` everywhere. After the bridge hits `mobileUi.registerAction`, it runs the plugin
logic and queues `client/refresh` for the phone.

## Integration testing

```bash
# 1) Install the JS plugin into the Host profile (run from the SDK repository root)
dsh plugin --profile web add ./demo/js

# 2) Copy demo/native to samples/demo-native in the App repository, then build and install there
./gradlew :samples:demo-native:assembleDebug
adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk

# 3) Open the App → Settings → Plugin UI → Demo plugin (the native page should take over)
#    A "Demo 42" chip appears above the chat input; tap it to open the popover.
```

Without the APK installed, step 3 opens the Web fallback page (which can also read
`demo.status`).

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

## Where this fits in the docs

- [SDK developer guide](../../docs/plugin-sdk-guide.md): from packaging to slots, RPC and lifecycle;
- [Source-driven native UI flow](../../docs/source-driven-native-ui.md): scan → gaps → implementation;
- [Docs site case](../../docs/index.html#case): Demo's integration checklist and verification output.
