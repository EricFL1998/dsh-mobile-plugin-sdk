> English | [中文](pitfalls.md)

# Native UI Integration Checklist and Best Practices

> This checklist is organized by integration module: each section first gives the correct
> approach, then lists checkable items. When integrating a new plugin or a new anchor point,
> follow the order 1 → 6. The complete reference implementation is the Demo plugin: the Web half is `demo/js`,
> the companion APK is `demo/native`.

---

## 1. Slot / Host

### 1.1 Provide the host capability required by a slot contribution

**Correct approach**: `SlotBridgeHost` adds `PluginRpcHost` / `PluginModelHost` on top of the
basic `PluginHostApi`; `NativeSlotHost` uses it uniformly to render contributions; the App injects
`NativeHost.rpcHandler`. For contributions that need RPC, first confirm `callRpc` returns a result, then write the UI.
The Demo dock depends on this chain to read `demo.status`; when `rpc == null`, show a readable
"Host RPC not connected" message rather than a blank area.

**Checklist**

- [ ] The scan report shows `rpc:*` dependencies for this anchor point;
- [ ] On first integration, verify `callRpc` returns `{ result: { ok, value } }`;
- [ ] The anchor point has fallback UI when `PluginRpcHost` is missing.

### 1.2 Make registry changes observable

**Correct approach**: `SlotRegistry` exposes a Compose-observable `revision` and increments it on
register/unregister; `resolve()` reads the revision so an already-composed `NativeSlotHost` recomposes
when the registry changes.

**Checklist**

- [ ] When a plugin APK is loaded after the page has composed, existing anchor points appear automatically;
- [ ] After switching sessions and re-entering the chat page, the dock is still present.

### 1.3 Split multiple entries of a built-in slot

**Correct approach**: For the composer dock's tok/s, cache hit, and context, use
`COMPOSER_DOCK_ACTIVITY`, `COMPOSER_DOCK_USAGE`, and `COMPOSER_DOCK_CONTEXT` respectively,
each wrapped with `OfficialSlot`; plugins override only the entries they need.

**Checklist**

- [ ] When a slot has multiple entries, they are split by activity/usage;
- [ ] Plugin entry orders do not conflict with built-in entries.

### 1.4 Provide a host for a keyed slot as well

**Correct approach**: When extending `settings.models.provider-card`, also add the NativeSlot
mapping and the `SettingsModelsPane` host container, then let the companion register its contribution.

**Checklist**

- [ ] `--companion --check` reports no "unmapped/no host";
- [ ] The plugin block is visible on the Settings → Models page.

---

## 2. Companion APK / Loading

### 2.0 Use the full package name

**Correct approach**: `com.dsh.mobile.<full plugin name>`, keeping the `dsh-plugin-` prefix and lowercasing it,
replacing characters outside `[a-z0-9]` with `_`:

```
dsh-plugin-demo        -> com.dsh.mobile.dsh_plugin_demo
dsh-plugin-my-plugin   -> com.dsh.mobile.dsh_plugin_my_plugin
```

For additional capabilities of the same JS package, append a `_<capability>` suffix, for example
`com.dsh.mobile.dsh_plugin_my_plugin_extra`. `dsh.plugin.id` is decoupled from the package name; use the plugin's
own reverse-domain id (Demo: `demo.native-ui`).

**Checklist**

- [ ] `namespace`, `applicationId`, and the manifest `package` are identical;
- [ ] The package name contains only lowercase letters, digits, and underscores;
- [ ] The package name is finalized before the first release.

### 2.1 Load the companion with the host-provided ClassLoader

**Correct approach**: Prefer `packageContext.classLoader` (provided by `createPackageContext`), and fall back to
`DexClassLoader` if loading fails; the failure path must log and show "load failed" in the plugin details.

**Checklist**

- [ ] Logs contain no `Failed to find entry 'classes.dex'`;
- [ ] Companion load failures are visible in the UI, not blank.

### 2.2 Protect an existing companion on the device

**Correct approach**: Confirm the APK's source and version before installing; prefer building from source and keeping
the original APK; for debug installs, use a separate package id, or run `adb pull` to back it up first, then `adb install -r`.

**Checklist**

- [ ] Install commands state the source and version;
- [ ] The existing APK is backed up before overwrite, or a separate package id is used.

### 2.3 Verify jsPackage pairing

**Correct approach**: The manifest's `dsh.plugin.jsPackage` must equal the JS package's `package.json.name`
(the Demo uses `dsh-plugin-demo` for both); after install/uninstall, return to the foreground to trigger a rescan.

**Checklist**

- [ ] The "included components" count in plugin details is correct (Demo: 2);
- [ ] Anchor points update immediately after install/uninstall.

---

## 3. Data / RPC

### 3.1 Retry first-screen RPC with backoff

**Correct approach**: In the contribution, retry the first load 3–6 times with backoff (800ms / 2s), stopping on
success; on failure show an error state and do not fabricate data. The Demo dock retries 3 times to read `demo.status`.

**Checklist**

- [ ] The first frame after cold start gets data, or shows a readable error;
- [ ] After a 401 recovers, the retry succeeds automatically;
- [ ] The failure state does not show fake data.

### 3.2 Refresh periodically or on events

**Correct approach**: Reread every 30–60s, or reread after `client/refresh`, or reread immediately after a user
action; carry the loop with `LaunchedEffect(key)` so leaving the page cancels it automatically; authoritative data stays in the Host,
and the APK does not maintain business state. The Demo dock rereads `demo.status` every 30s.

**Checklist**

- [ ] Data follows Host changes while the page stays open;
- [ ] No polling remains after leaving the page;
- [ ] Counters and similar data are not incremented locally inside the APK.

### 3.3 Selection state is passed by the App through the spec

**Correct approach**: The App puts the current selection (such as `selectedModelId`, `provider`) into the spec of
the corresponding anchor point; native only displays the selection explicitly passed in the spec and combines it
with Host return values, without inferring on its own.

**Checklist**

- [ ] Entry data is correct after switching models/providers;
- [ ] When the spec is missing, show an explicit default or empty state instead of guessing.

### 3.4 Write host state through optional interfaces

**Correct approach**: When a session's model/reasoning effort must change, add `PluginModelHost.selectModel`
(injected by the App, proxied by `SlotBridgeHost`); pure-RPC write paths (such as the Demo's `demo.reset`)
still go through `callRpc`; the two channels are not mixed.

**Checklist**

- [ ] Host state takes effect after the write;
- [ ] The plugin does not use Host-internal protocols directly.

---

## 4. UI Positioning / Styling

### 4.1 Use PluginPopover for anchored overlays

**Correct approach**: Put the trigger element and `PluginPopover` in the same `Box`; the
`PopupPositionProvider` anchors 6dp above, right-aligns, and clamps inside the window; use `Dialog` only for genuine modal flows
(confirmation dialogs, full-screen pickers).

**Checklist**

- [ ] The overlay sits next to the trigger element, not centered;
- [ ] It does not cover the composer's send button;
- [ ] The scanner produces no "anchoring clues present but companion has no Popup" warning.

### 4.2 Overlay materials come from PluginTheme

**Correct approach**: Use `colorPopupSurface`, `colorPopupBorder`, `cornerPopup`,
`textPopupTitle` / `textPopupBody`; do not draw your own Material Card, and do not hard-code colors,
corner radii, or shadows. These fields are mapped by the App from `DshPanelColors` / `DshMenuCard`.

**Checklist**

- [ ] The overlay looks consistent with the model menu and permission menu;
- [ ] Both dark and light modes are correct;
- [ ] No hard-coded overlay colors exist in the code.

### 4.3 Overlay width follows content

**Correct approach**: `PluginPopover` uses `width(IntrinsicSize.Max).widthIn(max = 320.dp)`;
it is as wide as its content, wrapping/scrolling only when content is too long.

**Checklist**

- [ ] Short content (such as `计数 42`, `上次动作 boot`) shows no large blank area;
- [ ] Long content does not overflow or break the window.

### 4.4 Place the dock in the built-in position

**Correct approach**: Put `NativeSlotInline(COMPOSER_DOCK)` into `ComposerDockBar`; tok/s,
cache, and context each have independent slots, and plugin entries are ordered after the context entry.

**Checklist**

- [ ] The dock chip is in the bottom status row, not above the input card;
- [ ] No overlap or misalignment with built-in entries.

---

## 5. Scanner / Workflow

### 5.1 Use placement hints to cover positioning and styling

**Correct approach**: `plugin_ui_scanner.py` extracts clues such as `useAnchoredPosition`, `createPortal`,
`anchorRef`, `role=dialog`, `position: fixed/absolute`, `side`/`align`, and checks whether the companion has
`Popup` / `PluginPopover`; the report keeps a "positioning/interaction" column.

**Checklist**

- [ ] The report includes positioning/interaction information;
- [ ] Anchored overlay anchor points have `PluginPopover` on the companion side.

### 5.2 Scan the plugin's own package

**Correct approach**: `--plugin` points to the plugin package itself: read `.js.map` sourcesContent first,
then `src/*.tsx`, and finally `lib/client.js`. The scanner includes the `tools/sdk-mapping.json`
mapping/host snapshot and can run directly in the SDK repository; to verify App-side host sources, add
`--root <App repository root>`. The built-in slot inventory is maintained on the App side.

**Checklist**

- [ ] `python tools/plugin_ui_scanner.py --plugin <plugin package> --companion <companion sources> --check` passes from the SDK repository root;
- [ ] Falls back to `lib/client.js` when parsing fails.

### 5.3 Compare the plugin UI with the companion implementation

**Correct approach**: `--companion` scans the companion sources for `NativeSlot.X`, `SlotRegistry.register`,
and `PageRegistry.register`; `--check` requires "plugin has UI → App has mapping and host → companion has implementation",
and fails if either side is missing.

**Checklist**

- [ ] Every row's "native implementation" is "Yes";
- [ ] `--check` exits with code 0.

---

## 6. Acceptance Checklist (Run for Every New UI Integration)

- [ ] `python tools/plugin_ui_scanner.py --plugin demo/js --companion demo/native/src/main/kotlin --check` passes from the SDK repository root;
- [ ] Every slot's `native implementation` is "Yes" in the report (Demo: 2/2);
- [ ] App-side built-in slot mappings/hosts: `slot_inventory.py` + `SlotCoverageTest` pass;
- [ ] First-screen RPC has retries (Demo dock: 3 times, 800ms/2s), and periodic refresh works (Demo dock: 30s);
- [ ] Popups use `PluginPopover`, width follows content, and materials come from `PluginTheme` (Demo: `DemoDockContribution`);
- [ ] Events use the correct `componentId` (Demo: `dsh-plugin-demo#demo` / `dsh-plugin-demo#demo-dock`), and actions are registered with `mobileUi`;
- [ ] Data is correct after selection-state changes (the spec is passed by the App, not inferred natively);
- [ ] Web UI falls back correctly when the companion APK is not installed (Demo: settings page + dock chip);
- [ ] `node --test demo/js/test/rpc.test.mjs` passes 7/7;
- [ ] Screenshots are for visual acceptance only, not for discovering UI.

For the Demo's complete implementation and verification commands, see
[Demo plugin README](../demo/js/README.md), [scan inventory](plugin-ui-inventory-example.md),
and [docs-site case](index.html#case).
