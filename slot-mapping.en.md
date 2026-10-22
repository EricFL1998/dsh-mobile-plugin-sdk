> English | [中文](slot-mapping.md)

# Built-in slot → native slot → host location

> This table is the factual mapping of the App's current code. All 31 built-in slots now
> have a native slot and host container; there are no "explicitly unsupported" slots
> anymore. When a built-in slot is added, `SlotCoverageTest` forces it to be claimed.
>
> Code baseline: SDK API v1 / `sdk/src/main/kotlin/.../slots/NativeSlot.kt` /
> `NativeSlotMapping.kt`.

## Native slots (NativeSlot)

| NativeSlot | Location on the phone | Host code |
| --- | --- | --- |
| `SETTINGS_SHEET` | Page entry and full page for Settings / Plugin UI | `SettingsRootScreen.kt` |
| `SETTINGS_PROVIDER_CARD` | Settings → Models provider card extension area | `SettingsModelsPane.kt` |
| `SIDEBAR_PANEL` | Left navigation drawer panel | `MainActivity.kt` |
| `COMPOSER_DOCK` | Above the input card (dock pills / status bar) | `ChatScreen.kt` |
| `COMPOSER_LEFT` | Left side of the input bar (next to the plus button) | `ChatScreen.kt` |
| `COMPOSER_RIGHT` | Right side of the input bar (send/stop button) | `ChatScreen.kt` |
| `COMPOSER_MODEL` | Model selector trigger | `ChatScreen.kt` |
| `COMPOSER_PERMISSION` | Permission preset trigger | `ChatScreen.kt` |
| `COMPOSER_PLAN` | Plan mode extension slot | `ChatScreen.kt` |
| `COMPOSER_ATTACHMENTS` | Attachment entry extension slot | `ChatScreen.kt` |
| `COMPOSER_BAR` | Entire composer card (built-in default content passed via OfficialSlot) | `ChatScreen.kt` |
| `COMPOSER_OVERLAY` | Input overlay layer | `ChatScreen.kt` |
| `CONVERSATION_VIEW` | Whole-conversation view extension slot | `ChatScreen.kt` |
| `SESSION_HEADER_CORNER` | Conversation header corner | `MainActivity.kt` |
| `PLUGIN_OVERLAY` | App top-level overlay | `MainActivity.kt` |
| `MESSAGE_ACTION` | Message long-press / assistant message action area | `ChatScreen.kt` |
| `TOOL_VIEW_SHEET` | Tool card detail sheet | `ChatScreen.kt` |
| `CHAT_STREAM` | Inside the conversation message stream | `ChatScreen.kt` |
| `WEB_ISLAND_FULL` | Full-screen Web fallback container (host already wired; Demo does not inject) | `MainActivity.kt` |

## Built-in slot detail (31/31 wired)

| Built-in slot | NativeSlot | Status |
| --- | --- | --- |
| `conversation.composer.dock` | `COMPOSER_DOCK` | Wired |
| `conversation.input.dock` | `COMPOSER_DOCK` | Wired |
| `conversation.input.overlay` | `COMPOSER_OVERLAY` | Wired |
| `conversation.input.left` | `COMPOSER_LEFT` | Wired |
| `conversation.input.right` | `COMPOSER_RIGHT` | Wired |
| `conversation.input.model` | `COMPOSER_MODEL` | Wired |
| `conversation.input.permission` | `COMPOSER_PERMISSION` | Wired |
| `conversation.input.plan` | `COMPOSER_PLAN` | Wired |
| `conversation.input.attachments` | `COMPOSER_ATTACHMENTS` | Wired |
| `conversation.composer.bar` | `COMPOSER_BAR` | Wired |
| `conversation.view` | `CONVERSATION_VIEW` | Wired |
| `conversation.session.header.corner` | `SESSION_HEADER_CORNER` | Wired |
| `conversation.chat.flow` | `CHAT_STREAM` | Wired |
| `conversation.chat.node` | `CHAT_STREAM` | Wired |
| `conversation.user.text.node` | `CHAT_STREAM` | Wired |
| `conversation.chat.reasoning.body` | `CHAT_STREAM` | Wired |
| `conversation.chat.turnTail` | `CHAT_STREAM` | Wired |
| `conversation.chat.assistant-actions` | `MESSAGE_ACTION` | Wired |
| `tool.call.toolview` | `TOOL_VIEW_SHEET` | Wired |
| `tool.call.images` | `TOOL_VIEW_SHEET` | Wired |
| `sidebar.workspaces` | `SIDEBAR_PANEL` | Wired |
| `sidebar.workspaces.session.menu.item` | `SIDEBAR_PANEL` | Wired |
| `sidebar.workspaces.session.row.action` | `SIDEBAR_PANEL` | Wired |
| `sidebar.panellist` | `SIDEBAR_PANEL` | Wired |
| `sidebar.right.pane.tab` | `SIDEBAR_PANEL` | Wired |
| `sidebar.right.pane.tab.title` | `SIDEBAR_PANEL` | Wired |
| `shell.overlay` | `PLUGIN_OVERLAY` | Wired |
| `settings.section` | `SETTINGS_SHEET` | Wired |
| `settings.general.item` | `SETTINGS_SHEET` | Wired |
| `settings.models.provider-card` | `SETTINGS_PROVIDER_CARD` | Wired |
| `plugins.tab` | `SETTINGS_SHEET` | Wired |

> Note: `NativeSlot` is a "carrying location"; multiple built-in slots may map to the
> same native slot. Plugins declare a slot through `SlotContribution.slot`. Built-in
> controls also go through the same slot channel via `OfficialSlot`, so plugins can
> ENHANCE (render alongside) or OVERRIDE (take over and hide the built-in default).

## Acceptance checklist for adding a native slot

- [ ] Add the new slot to the `NativeSlot` enum;
- [ ] Every `DshSlotRegistry` built-in slot has an entry in this document;
- [ ] Place an `OfficialSlot` / `NativeSlotHost` container in the corresponding App UI;
- [ ] Verify the contribution is visible via a sample or smoke test;
- [ ] Update this document and the generated `docs/slot-inventory.md`.

> Historical issue: the first version of `COMPOSER_DOCK` had no host container, so a
> plugin declaration was completely invisible and was only fixed after a user reported
> it. `SlotCoverageTest` + `tools/slot_inventory.py` now prevent regressions.
