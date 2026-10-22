> English | [中文](source-driven-native-ui.md)

# Source-Driven Native UI Integration Flow

> This document defines a fixed workflow: first use the scanner to read the plugin's own
> package and obtain the UI inventory and gaps, then choose an implementation with
> `PageContent` / `SlotContribution` / `OfficialSlot` / dsh-ui spec.
> The complete example is the Demo plugin: the Web half is `demo/js`, the companion APK is `demo/native`.

## 1. Input: The Plugin's Own Package and Source

The SDK ships a scanner that reads the plugin package directly. Run it from the SDK repository root:

```bash
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

The scanner includes `tools/sdk-mapping.json` (a snapshot of built-in slot mappings and hosts),
so it can determine "mapping / host / companion implementation" without any additional repository.
When you need to verify App-side host sources, optionally add `--root /path/to/dsh-mobile-app`.
`--plugin` and `--companion` are resolved relative to the current directory.
To switch to your own plugin:

```bash
python tools/plugin_ui_scanner.py \
  --plugin /path/to/your-plugin \
  --companion /path/to/native-companion/src \
  --check
```

Add `--write` / `--json` when you need to keep a record.

Source acquisition priority:

1. `sourcesContent` in `lib/*.js.map` (original TSX, recommended);
2. `src/**/*.tsx` (plugin repository sources);
3. `lib/client.js` (compiled output, heuristic).

`--companion` is optional: when passed, it scans the companion APK sources for `NativeSlot.X` /
`SlotRegistry.register` / `PageRegistry.register` to determine whether each plugin slot is actually implemented.
`--check` fails in the following cases:

- the plugin registers a slot, but the App has no NativeSlot mapping;
- a NativeSlot is mapped, but the App has no host container;
- `--companion` is passed, but the companion does not register the corresponding contribution/page.

## 2. Output: A Directly Actionable Inventory

The report contains:

- **Anchor points**: slot / entry / order / component / mode;
- **Native status**: NativeSlot, host, and whether the companion implements it;
- **Data**: RPC endpoints, `useProjection`, useChat, inject;
- **UI**: JSX component tree, visible copy, button actions;
- **localized copy**: `locale/*.json`;
- **Gaps**: unmapped, no host, not implemented under `--companion`.

Scan result for the Demo plugin (`demo/js`):

| slot | entry | order | component | Native anchor | Host | Native implementation | Data dependencies |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `conversation.composer.dock` | `demo-dock` | 10 | `DemoDockChip` | `COMPOSER_DOCK` | Yes | Yes | `rpc:bump`, `rpc:status` |
| `settings.section` | `demo` | 10 | `DemoSettingsSection` | `SETTINGS_SHEET` | Yes | Yes | `rpc:bump`, `rpc:status` |

Scan summary: package `dsh-plugin-demo` 0.1.0, 7 source files, 2 registered anchor points; `--check` outputs
`plugin UI coverage check OK`. The 2 RPC dependencies counted in the report are the ones actually called by the Web
components: `demo.bump` / `demo.status`; the complete Host-side contract also includes `demo.catalog` / `demo.ping` /
`demo.reset` / `demo.echo`, see [Demo README](../demo/js/README.md).

The full report is in [Plugin UI Inventory: dsh-plugin-demo](plugin-ui-inventory-example.md). The Demo's
two anchor points are all mapped, have ready hosts, and have companion implementations, so they can serve as the minimum coverage baseline for subsequent plugins.

## 3. Choosing an Implementation

| Scan result | Implementation |
| --- | --- |
| Settings page / full-page entry | `PageContent` + `PageRegistry.register` (Demo: `DemoSettingsPage`) |
| Input bar, messages, dock, card extensions | `SlotContribution` (Demo: `DemoDockContribution`) |
| Built-in controls (tok/s, cache, model selector, etc.) | `OfficialSlot` wrapper, preserving the default UI |
| Informational cards, lists, metrics | `SpecSlotContribution` + dsh-ui spec |
| Data | `PluginRpcHost.callRpc`, calling the plugin's existing RPCs (Demo: `demo.status` / `demo.catalog`) |
| Need to switch model/reasoning effort | `PluginModelHost.selectModel` |
| Anchored overlay / popover / click-to-expand panel | SDK `PluginPopover` (anchored), not a centered `Dialog` |
| No host available yet | Explicit Web fallback; silent loss is not allowed |

## 4. Positioning and Styling: Popups Must Stick to Their Anchor and Use Host Materials

Positioning and styling follow the rules below so that structure and placement stay consistent.
The scanner extracts positioning clues from source: `useAnchoredPosition`, `createPortal`, `anchorRef`, `role="dialog"`,
`position: fixed/absolute`, `side: 'top'`, `align: 'end'`, etc.; if the plugin uses anchored
overlays but the companion sources have no `Popup` / `PopupPositionProvider`, `--check` emits a warning.

### 4.1 Positioning Rules

- When the Web version uses an anchored popover (`side: top` / `align: end`), native must anchor to it:
  use the SDK's `PluginPopover` and place it in the same `Box` as the trigger element;
- Use `Dialog` only for genuine modal flows (confirmation dialogs, full-screen pickers);
- Align the overlay's right edge to the trigger's right edge, leave 6dp above it, and keep it inside the window;
- Do not write your own `PopupPositionProvider`; the SDK already provides a unified implementation.

How the Demo dock does it (`DemoDockContribution.kt`):

```kotlin
val current = status
var open by remember { mutableStateOf(false) }

Box {
    Text(
        text = "Demo ${current.count}",
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { open = !open }, onLongPress = { open = true })
        },
    )
    if (open) {
        PluginPopover(theme = theme, onDismiss = { open = false }) {
            Text("Demo 插件", style = theme.textPopupTitle)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("计数", style = theme.textPopupBody)
                Text(current.count.toString(), style = theme.textPopupBody)
            }
        }
    }
}
```

### 4.2 Styling Rules

Overlays must use the App menu system's (`DshMenuCard`) material; do not build your own Material `Card`:

- Background: `theme.colorPopupSurface` (the App composites `menuSurfaceFill` over `bgBase`);
- Stroke: `theme.colorPopupBorder` (dark `borderL3` / light `borderL1`);
- Corner radius: `theme.cornerPopup` / `theme.shapePopup` (16dp);
- Padding: `theme.spacingUnit * 1.75f`;
- Text: title `theme.textPopupTitle`, body `theme.textPopupBody`;
- Shadow is handled by `PluginPopover`; plugins do not pass elevation.

The `PluginTheme` popup fields are mapped by the App's `rememberPluginTheme()` from
`DshPanelColors` / `DshPanelDefaults`, so the model menu, permission menu, and plugin overlays
look like one design system.

### 4.3 Checklist

- [ ] The plugin source contains `useAnchoredPosition` / `createPortal` / `side: top`;
- [ ] The companion uses `PluginPopover`, not `Dialog` (Demo: `DemoDockContribution`);
- [ ] Overlay width follows content (`IntrinsicSize.Max`), at most `320dp`, and wraps/scrolls when content is long;
- [ ] Uses the `theme` popup fields; no hard-coded colors/corners/shadows;
- [ ] `--check` produces no "plugin uses anchored overlay but companion has no Popup" warning;
- [ ] On a real device, tapping makes the overlay sit tight against the chip without covering the send button.

## 5. Data Contract

- The native side does not open network connections or hold tokens; all data comes from the Host plugin via RPC (Demo:
  `demo.status` state, `demo.catalog` integration inventory).
- Slot specs carry display data only; selection state such as the current provider/model is passed by the App through the spec
  (concrete fields depend on the anchor point); the native side does not locally guess "who it probably is".
- Long-running/polling work stays in the Host; native only calls `callRpc` periodically or waits for `client/refresh` (the Demo
  dock rereads `demo.status` every 30s).
- Degrade when a capability is missing: `host as? PluginRpcHost ?: return` is allowed, but the scanner must clearly state
  "which host capability is required"; you can also show a readable "Host RPC not connected" like the Demo settings page, instead of
  rendering a blank area.

## 6. Complete Order for Adding a UI

1. Run the scanner to confirm slot/entry/component and data dependencies;
2. On the App side, confirm the `NativeSlot` mapping and host container (Demo: `SETTINGS_SHEET` /
   `COMPOSER_DOCK`); add them if missing (mapping table + `NativeSlotHost`/`OfficialSlot`);
3. On the companion side, register a `SlotContribution` (or `PageContent`); use RPC for data;
4. When a new host capability is needed, add an optional SDK interface (see `PluginRpcHost`, `PluginModelHost`),
   implement it in `SlotBridgeHost`, and have the App inject a handler;
5. Re-run `plugin_ui_scanner.py --check` (command in section 1); it must pass;
6. On the App side, run `slot_inventory.py` / `SlotCoverageTest`; built-in anchor points must not fall back;
7. Screenshots are for visual acceptance only, not a source for discovering UI.

## 7. Demo Plugin Integration Inventory and Verification Commands

Use the following commands to verify the Demo's JS registration, native implementation, and RPC/event contract:

```bash
# 扫描：两个落点都必须有映射、宿主和伴生实现
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check

# RPC / 动作 / 状态隔离
node --test demo/js/test/rpc.test.mjs
```

Expected output:

```text
plugin UI coverage check OK
conversation.composer.dock      DemoDockChip         impl=True
settings.section                DemoSettingsSection  impl=True

# tests 7 / pass 7 / fail 0
```

Integration inventory and implementation approach:

- `settings.section` (id `demo`) → `SETTINGS_SHEET`: Web component
  `DemoSettingsSection`, native `DemoSettingsPage` (`PageContent`,
  `PageEntry(id="demo")`). Reads `demo.status` / `demo.catalog`; "server +1" goes through
  `dispatchEvent("dsh-plugin-demo#demo", "submit", {actionId:"bump"})`;
  the bridge queues `client/refresh`, then `refreshEvents` triggers a reread; "reset" directly calls
  `demo.reset`.
- `conversation.composer.dock` (id `demo-dock`) → `COMPOSER_DOCK`: Web component
  `DemoDockChip`, native `DemoDockContribution` (`SlotContribution`). The chip shows the
  Host's `count`; tapping opens `PluginPopover`; `demo.status` is reread every 30s. It also provides
  an event write path and a pure-RPC write path via `demo.reset`.
- Other native anchor points: `DemoNativePlugin` uses `DemoSlotContribution` to cover
  21 of the 22 `DemoSlots` entries; `WEB_ISLAND_FULL` is the full-island Web fallback container, which the Demo does not inject,
  to avoid covering the whole App; it is only described on the Demo page.

Verification steps:

1. `dsh plugin --profile web add ./demo/js` to install the JS plugin;
2. Follow "Build" in [demo/README.md](../demo/README.md) to build and install the companion APK in the App repository;
3. Open App → Settings → Plugin UI → Demo plugin, and confirm the native page and `demo.status` data;
4. Uninstall the companion APK and confirm the settings page and dock fall back to the Web components.

For the acceptance checklist and the docs-site case, see [Checklist](pitfalls.en.md) and
[docs-site case](index.html#case).
