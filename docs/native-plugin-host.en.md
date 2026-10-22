> English | [中文](native-plugin-host.md)

# Native Plugin Host Interfaces (App-Side Development Specification)

> Status: **Proposal (pending review)**. This document defines all the general interfaces and modules the App must build to support the "native UI plugin store".
> Audience: the App (`com.dsh.mobile`) development team.
> Related documents: [plugin-side SDK specification](native-plugin-sdk.md), [mobile bridge protocol](mobile-bridge.en.md).
> Recorded: 2026-10-20.

## Table of Contents

- [0. Summary](#0-summary)
- [1. Overview: Eight Modules](#1-overview-eight-modules)
- [2. Module 1: Plugin Discovery](#2-module-1-plugin-discovery)
- [3. Module 2: Trust Verification SignaturePinner](#3-module-2-trust-verification-signaturepinner)
- [4. Module 3: ABI Gate](#4-module-3-abi-gate)
- [5. Module 4: Render Host PluginHostContainer](#5-module-4-render-host-pluginhostcontainer)
- [6. Module 5: Host Capability Bridge PluginHostApi Implementation](#6-module-5-host-capability-bridge-pluginhostapi-implementation)
- [7. Module 6: Theme Injection PluginTheme](#7-module-6-theme-injection-plugintheme)
- [8. Module 7: Store Client StoreClient](#8-module-7-store-client-storeclient)
- [9. Module 8: Management UI](#9-module-8-management-ui)
- [10. Integration with bridge and the Existing Architecture](#10-integration-with-bridge-and-the-existing-architecture)
- [11. Security Baseline](#11-security-baseline)
- [12. Rollout Order and Acceptance](#12-rollout-order-and-acceptance)
- [13. Risks and Mitigations](#13-risks-and-mitigations)

---

## 0. Summary

The plugin-side contract (manifest, SDK API, capability declarations) is defined by [native-plugin-sdk.md](native-plugin-sdk.md); this document is the other side of the coin: **the general interfaces the App must build so that these APKs "can be discovered, safely loaded, contained, and managed"**.

Design principles:

1. **Enhancement layer positioning**: the native plugin system is an enhancement layer alongside the Web-first path, not a replacement; the Web fallback is always present;
2. **Iron-rule gatekeeping**: the App-side implementation guarantees "logic stays in the Host, credentials never leave the Host" — `PluginHostApi` provides no network/credential/arbitrary-file surface;
3. **Crashes do not propagate upward**: any plugin render exception is captured by the fence, leaving the App and other plugins unaffected;
4. **Protocol reuse**: all communication with the Host goes through the existing bridge (`dispatch` / `client/refresh` / `events`); no new protocol is invented.

---

## 1. Overview: Eight Modules

| # | Module | Responsibility | Reuses existing | Effort |
| --- | --- | --- | --- | --- |
| 1 | PluginDiscovery | Discover installed plugin APKs; watch installs/uninstalls | PackageManager | Medium |
| 2 | SignaturePinner | Pin the signing fingerprint to prevent swapping | `PackageInfo.signingInfo` | Medium |
| 3 | AbiGate | apiVersion/minAppVersion/Compose version gate | New | Small |
| 4 | PluginHostContainer | Compose host container: constraints + error fence + fallback | Genui.kt container experience | Large |
| 5 | PluginHostApi implementation | Capability bridge: dispatchEvent/openUrl/storage/notify/share/clipboard/workspace | Bridge dispatch chain is ready | Large |
| 6 | PluginTheme | App theme tokens → read-only plugin snapshot | core/designsystem | Small |
| 7 | StoreClient | Store index, compatibility annotations, download, launch installer | Existing HTTP stack | Medium |
| 8 | Management UI | Plugin list, capability display, global switch, uninstall entry | feature/settings skeleton | Medium |

Effort concentrates on 4 and 5: the former determines stability, the latter determines the security boundary.

Reference implementation: `reference/native-host/` provides host container and registry code to follow.

---

## 2. Module 1: Plugin Discovery

```kotlin
interface PluginDiscovery {
    /** 当前已装且通过初步校验的插件；状态流，UI 直接监听 */
    val plugins: StateFlow<List<InstalledPlugin>>

    data class InstalledPlugin(
        val pluginId: String,          // dsh.plugin.id
        val packageName: String,       // APK 包名
        val version: Int,              // descriptor.version
        val versionCode: Int,          // APK versionCode
        val apiVersion: Int,
        val minAppVersion: Int,
        val signatureFingerprint: String,
        val status: Status,            // READY / ABI_TOO_NEW / SIGNATURE_MISMATCH / DISABLED
        val serviceComponent: ComponentName,
    )

    enum class Status { READY, ABI_TOO_NEW, SIGNATURE_MISMATCH, DISABLED }
}
```

Implementation notes:

- Discovery: `PackageManager.queryIntentServices(Intent("com.deepseek.dsh.action.PLUGIN_UI"), MATCH_ALL)`, reading each service's four meta-data entries;
- Live updates: register broadcast listeners for `PACKAGE_ADDED` / `PACKAGE_REMOVED` / `PACKAGE_REPLACED` (filtered by a data scheme of our own), adding/removing entries immediately; an uninstall must **remove the render slot in the same frame**, leaving no dangling references;
- Do a full rescan at startup (covering installs/uninstalls that happened while the App was not running);
- Read meta-data only; **do not bind or load any code at this stage** — loading decisions happen after 2 and 3.

---

## 3. Module 2: Trust Verification SignaturePinner

```kotlin
interface SignaturePinner {
    /** 通过：商店登记指纹与实装一致，且与首次钉死一致 */
    fun verify(plugin: InstalledPlugin): VerifyResult

    sealed interface VerifyResult {
        data object Trusted : VerifyResult
        data class FirstSeen(val fingerprint: String) : VerifyResult   // 钉死并继续
        data class Mismatch(val expected: String, val actual: String) : VerifyResult
        data object UnknownToStore : VerifyResult                       // 商店索引查无此 id
    }
}
```

Rules:

- **Trust on first use (TOFU)**: the first time a pluginId is seen, its fingerprint must appear in the store index (see module 7); if it matches, pin it locally and load;
- **Signature change = swap**: if a later version's fingerprint changes → `SIGNATURE_MISMATCH`, refuse to load, and clearly tell the user "identity mismatch"; silent acceptance is not allowed;
- Pinned data persists to disk (Room/DataStore) and resets when App data is cleared;
- If the store index is unreachable (offline): already-pinned plugins keep working; never-seen new plugins are temporarily unavailable with a prompt to go online.

---

## 4. Module 3: ABI Gate

```kotlin
interface AbiGate {
    fun check(plugin: InstalledPlugin): GateResult

    sealed interface GateResult {
        data object Pass : GateResult
        data class PluginTooNew(val requiredApi: Int, val appApi: Int) : GateResult
        data class AppTooOld(val minApp: Int, val app: Int) : GateResult
        data class ComposeMismatch(val pluginCompose: String, val appCompose: String) : GateResult
    }
}
```

- Three-way comparison: `apiVersion` (≤ the version the App supports), `minAppVersion` (≤ App version code), and the Compose version recorded in the plugin APK's meta-data (= the Compose version in the App);
- If any check fails → set status to `ABI_TOO_NEW` etc.; the UI shows "plugin requires update / please upgrade App"; **never try to load it** (loading a Compose-ABI-incompatible plugin results in `IncompatibleClassChangeError`, which must be blocked outside the gate);
- The list of supported apiVersions is updated with each release and written into `BuildConfig`.

---

## 5. Module 4: Render Host PluginHostContainer

Every plugin UI runs inside this container; this is the core of "containment".

```kotlin
@Composable
fun PluginHostContainer(
    plugin: InstalledPlugin,
    spec: JsonObject,
    fallback: @Composable () -> Unit,   // 降级内容（WebView 入口/转译树占位）
) {
    when {
        plugin.status != Status.READY -> fallback()
        else -> Box(Modifier.pluginConstraints()) {   // 最大尺寸/滚动边界/触控约束
            ResultCatching(onError = { PluginFallbackCard(plugin.name, it) }) {
                val renderer = remember(plugin) { PluginRendererHolder.get(plugin) }
                renderer.Render(spec, hostApiFor(plugin))
            }
        }
    }
}
```

Responsibility list:

- **Size constraints**: plugin content is confined to the area allocated by the host (max height, scroll clipping); it must not break the layout;
- **Error fence**: any exception thrown by the renderer → captured → placeholder block (plugin name + "This plugin UI failed" + retry); **never propagate upward** to crash the chat/settings page;
- **Renderer cache**: `PluginRendererHolder` caches the renderer instance obtained by `bindService` per pluginId; release it on uninstall/disable;
- **Recomposition boundary**: spec updates only trigger recomposition inside that container; plugins share no CompositionLocal (except the read-only theme snapshot);
- **Timeout protection**: first-frame render timeout (e.g. 2s) degrades to a placeholder plus an asynchronous retry.

---

## 6. Module 5: Host Capability Bridge PluginHostApi Implementation

This is the only App surface a plugin can touch; each capability is implemented with **declaration checks + allowlists + auditing**.

```kotlin
class PluginHostApiImpl(
    private val plugin: InstalledPlugin,
    private val bridge: BridgeRegistry,        // 现成
    private val audit: PluginAuditLog,
) : PluginHostApi {

    override fun dispatchEvent(componentId: String, event: String, payload: JsonObject) {
        require(componentId.startsWith(plugin.pluginId)) { "componentId 前缀不符" }
        bridge.dispatch(componentId, event, payload)     // → client-slots/dispatch，现成链路
        audit.record(plugin.pluginId, "dispatch:$event")
    }

    override fun openUrl(url: String) {
        if (!UrlAllowlist.check(url)) { /* 转系统浏览器并确认 */ }
        audit.record(plugin.pluginId, "openUrl")
    }

    override fun currentTheme(): PluginTheme = ThemeBridge.snapshot()   // 只读

    override fun requestCapability(capability: String): Boolean =
        capability in plugin.declaredCapabilities && CapabilitySwitches.isEnabled(capability)

    override fun storage(): PluginStorage =
        NamespacedStorage(plugin.pluginId)   // 每插件独立 namespace，禁读他人
}
```

Capability implementation matrix (v1):

| Capability | Implementation | Allowlist/limits |
| --- | --- | --- |
| `openUrl` | Custom Tabs | Domain allowlist; others go to the system browser with user confirmation |
| `storage` | DataStore partitioned by pluginId | Cross-plugin reads/writes forbidden |
| `notify` | Shell foreground-service notification channel (reusing Host event notification facilities) | User global switch; deep link back to the plugin page |
| `share` | `ACTION_SEND` | System sheet |
| `clipboard` | `ClipboardManager.setPrimaryClip` | Write-only |
| `workspace` | Host workspace service mapped through SAF | Path prefix validation; only inside authorized directories |

Auditing: `PluginAuditLog` records every capability invocation in a rolling log (pluginId, capability, time); viewable on the settings page, redacted before export.

---

## 7. Module 6: Theme Injection PluginTheme

```kotlin
/** 插件可见的主题只读快照；深色/字体缩放变化时更新并触发重组 */
data class PluginTheme(
    val colorPrimary: Color, val colorSurface: Color, val colorOnSurface: Color,
    val textTitle: TextStyle, val textBody: TextStyle, val textCaption: TextStyle,
    val shapeCard: Shape, val spacingUnit: Dp, val isDark: Boolean,
)

object ThemeBridge {
    fun snapshot(): PluginTheme          // 从 core/designsystem 取当前 token
    val flow: StateFlow<PluginTheme>     // 变化流
}
```

- The snapshot is read-only; plugins cannot access `Context` or `Resources`;
- Dark mode / system font scaling is mapped into the snapshot by the App; plugins do not write branches;
- Same source as Web mobile adaptation: token values should align with the DSH Web theme as closely as possible to reduce visual drift between the two frontends.

---

## 8. Module 7: Store Client StoreClient

v1 stays simple: the store index is a **signed JSON** distributed through the existing Release channel.

```kotlin
interface StoreClient {
    suspend fun fetchIndex(): StoreIndex?   // 离线返回 null，已钉死插件不受影响

    data class StoreIndex(
        val plugins: List<Entry>,
        val signature: String,               // 商店私钥签名，App 内置公钥验
    )
    data class Entry(
        val pluginId: String, val name: String, val version: Int,
        val apkUrl: String, val sha256: String,
        val signatureFingerprint: String,     // 登记指纹 → SignaturePinner
        val apiVersion: Int, val minAppVersion: Int,
        val capabilities: Set<String>,
        val screenshots: List<String>, val description: String,
    )
}
```

- **Compatibility annotations**: after fetching, compare with the current App version and show "compatible ✓ / requires App upgrade" on the store page;
- **Download and install**: HTTP download → SHA-256 verification → launch via `PackageInstaller`/system installer Intent; after installation, PluginDiscovery's broadcast takes over;
- **Update check**: index version > installed versionCode → prompt on the settings/store page; update flow same as install;
- If index signature verification fails or the format is wrong → discard the whole index; do not partially trust it.

---

## 9. Module 8: Management UI

Add a "Plugin Store" group to the Plugins page (reusing the `feature/settings` skeleton):

- **List**: icon, name, version, status (normal/update pending/identity mismatch/disabled), declared capabilities, abbreviated signing fingerprint;
- **Details page**: capability switches (per item), audit records, uninstall entry (jump to the system uninstall page);
- **Global switch**: "Disable all plugin UI" — one tap disables all native rendering and uniformly falls back to WebView;
- **Store entry**: open the store page (embedded WebView or system browser).

---

## 10. Integration with bridge and the Existing Architecture

```
┌─ App ────────────────────────────────────────────────┐
│  reference/native-host/                                  │
│   PluginDiscovery → SignaturePinner → AbiGate         │
│        ↓ READY                                        │
│   PluginHostContainer ─→ PluginHostApiImpl            │
│        ↑ spec                ↓ dispatchEvent          │
│  core/bridge（现成）: client-slots/dispatch、events    │
└───────↕───────────────────────────────────────────────┘
   Host: bridge 插件 → JS 插件逻辑层（不动）
```

- **Communication**: `dispatchEvent` → `client-slots/dispatch` op → `ui/<componentId>/<event>` bus → JS; state flows back via `client/refresh` → App re-fetches the spec. All of this already exists; zero new protocol;
- **Spec source**: the spec consumed by native renderers shares the same source as the bridge's RenderTree data model (JSON snapshot), produced by `client/refresh` patches or refetches on the Host side;
- **Relationship to Web-first**: native plugins are an enhancement layer. The chat/settings/plugins pages remain Web (architecture document route C→D); native render slots only appear locally where "the plugin declares supportedTypes and it matches" (settings sections, tool cards, etc.); non-matching/not-installed always stays WebView;
- **Relationship to the legacy transpilation chain**: the Web half of the bridge's transpile/client-slots UI falls back to third place on slots covered by native plugins (Web → native plugin → transpiled tree).

---

## 11. Security Baseline

1. Plugin APKs run inside the App process: store review + signature pinning are the baseline (see SDK documentation §11);
2. `PluginHostApi` has no network, no credentials, no arbitrary files, and no reflection surface;
3. Audit every capability invocation; redact before exporting logs;
4. Global switch and per-item switches coexist; plugin uninstall removes it immediately;
5. Verify store index signatures; only hand an APK to the installer after SHA-256 verification;
6. Plugin service binding validates the signature-level permission `com.deepseek.dsh.permission.BIND_PLUGIN` to prevent third-party apps from forging plugin endpoints.

---

## 12. Rollout Order and Acceptance

### Phase 0: Contract and self-proof (discovery + gate + host)

- Deliverables: 1, 3, 4 + `dsh-plugin-sdk` 1.0;
- **Key self-proof**: extract `Genui.kt` from the App and turn it into the first native plugin that goes through the complete chain. If it cannot be extracted, the contract is wrong; fix the contract before continuing;
- Acceptance: App remains stable on sample plugin install/uninstall/exception crashes, and fallback works correctly.

### Phase 1: Capability bridge and theme

- Deliverables: 5, 6; start with the `openUrl` + `storage` capabilities, then fill in the rest per the SDK table;
- Acceptance: the Demo sample plugin (SDK documentation "complete example") runs RPC + events end to end
  (`demo.status` → `dispatchEvent` → `client/refresh`).

### Phase 2: Trust and management

- Deliverables: 2, 8; signature pinning, global switch, settings-page management UI, audit viewing;
- Acceptance: update packages with changed signatures are rejected; uninstall linkage is immediate; the global switch degrades everything.

### Phase 3: Store

- Deliverables: 7; signed index JSON + download/install flow + compatibility annotations; first-party plugins first, then invite-only;
- Acceptance: complete loop from store page to working native rendering; installed plugins remain usable offline.

---

## 13. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Compose ABI coupling | Version mismatch causes crashes | AbiGate blocks before loading + SDK version lock + fallback path |
| Third-party code misbehaving in-process | Data leakage/crashes | Signature pinning + store review + narrow PluginHostApi + error fence |
| Store index tampering | Installing malicious plugins | Index signature verification + APK SHA-256 + fingerprint pinning |
| Leftovers after plugin uninstall | Dangling-reference crashes | Remove in the same frame on PACKAGE_REMOVED + full rescan at startup |
| Dual-frontend experience drift | Visual/behavioral inconsistency | PluginTheme shares tokens with Web; spec requires usable fallback |
| Ecosystem cold start | No third-party plugins | Phase 0 self-extracts Genui as proof; first-party plugins first |
| Resource contention with Web-first | Split direction | Enhancement-layer positioning written into ADR-006; Web fallback always present |
