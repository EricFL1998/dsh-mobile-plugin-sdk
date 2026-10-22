# 内置 slot → 原生落点 → 宿主位置

> 中文 | [English](slot-mapping.en.md)

> 这份表是 App 当前代码的事实映射。当前 31 个内置 slot 已全部有原生落点与宿主容器，
> 不再有“显式不支持”的 slot；新增内置 slot 时由 `SlotCoverageTest` 强制认领。
>
> 代码基准：SDK API v1 / `sdk/src/main/kotlin/.../slots/NativeSlot.kt` /
> `NativeSlotMapping.kt`。

## 原生落点（NativeSlot）

| NativeSlot | 手机上的位置 | 宿主代码 |
| --- | --- | --- |
| `SETTINGS_SHEET` | 设置页 / 插件页的页面入口与整页 | `SettingsRootScreen.kt` |
| `SETTINGS_PROVIDER_CARD` | 设置 → 模型页 provider 卡片扩展区 | `SettingsModelsPane.kt` |
| `SIDEBAR_PANEL` | 左侧导航抽屉面板 | `MainActivity.kt` |
| `RIGHT_PANEL` | 聊天页右侧栏（窄屏整屏覆盖）；带 `tabTitle` 的贡献会成为一个标签 | `RightSidebarPanel.kt` / `NativeRightPaneTabs.kt` |
| `COMPOSER_DOCK` | 输入卡片上方（dock 药丸/状态条） | `ChatScreen.kt` |
| `COMPOSER_LEFT` | 输入栏左侧（加号旁） | `ChatScreen.kt` |
| `COMPOSER_RIGHT` | 输入栏右侧（发送/停止按钮） | `ChatScreen.kt` |
| `COMPOSER_MODEL` | 模型选择触发器 | `ChatScreen.kt` |
| `COMPOSER_PERMISSION` | 权限预设触发器 | `ChatScreen.kt` |
| `COMPOSER_PLAN` | Plan 模式扩展位 | `ChatScreen.kt` |
| `COMPOSER_ATTACHMENTS` | 附件入口扩展位 | `ChatScreen.kt` |
| `COMPOSER_BAR` | 整条 composer 卡片（内置默认内容通过 OfficialSlot 传入） | `ChatScreen.kt` |
| `COMPOSER_OVERLAY` | 输入框悬浮层 | `ChatScreen.kt` |
| `CONVERSATION_VIEW` | 会话视图整体扩展位 | `ChatScreen.kt` |
| `SESSION_HEADER_CORNER` | 会话标题栏角落 | `MainActivity.kt` |
| `PLUGIN_OVERLAY` | App 顶层浮层 | `MainActivity.kt` |
| `MESSAGE_ACTION` | 消息长按 / 助手消息动作区 | `ChatScreen.kt` |
| `TOOL_VIEW_SHEET` | 工具卡片详情弹层 | `ChatScreen.kt` |
| `CHAT_STREAM` | 会话消息流内部 | `ChatScreen.kt` |
| `WEB_ISLAND_FULL` | 全屏 Web 回退容器（宿主已接入；Demo 不注入） | `MainActivity.kt` |

## 内置 slot 明细（31/31 已接入）

| 内置 slot | NativeSlot | 状态 |
| --- | --- | --- |
| `conversation.composer.dock` | `COMPOSER_DOCK` | 已接入 |
| `conversation.input.dock` | `COMPOSER_DOCK` | 已接入 |
| `conversation.input.overlay` | `COMPOSER_OVERLAY` | 已接入 |
| `conversation.input.left` | `COMPOSER_LEFT` | 已接入 |
| `conversation.input.right` | `COMPOSER_RIGHT` | 已接入 |
| `conversation.input.model` | `COMPOSER_MODEL` | 已接入 |
| `conversation.input.permission` | `COMPOSER_PERMISSION` | 已接入 |
| `conversation.input.plan` | `COMPOSER_PLAN` | 已接入 |
| `conversation.input.attachments` | `COMPOSER_ATTACHMENTS` | 已接入 |
| `conversation.composer.bar` | `COMPOSER_BAR` | 已接入 |
| `conversation.view` | `CONVERSATION_VIEW` | 已接入 |
| `conversation.session.header.corner` | `SESSION_HEADER_CORNER` | 已接入 |
| `conversation.chat.flow` | `CHAT_STREAM` | 已接入 |
| `conversation.chat.node` | `CHAT_STREAM` | 已接入 |
| `conversation.user.text.node` | `CHAT_STREAM` | 已接入 |
| `conversation.chat.reasoning.body` | `CHAT_STREAM` | 已接入 |
| `conversation.chat.turnTail` | `CHAT_STREAM` | 已接入 |
| `conversation.chat.assistant-actions` | `MESSAGE_ACTION` | 已接入 |
| `tool.call.toolview` | `TOOL_VIEW_SHEET` | 已接入 |
| `tool.call.images` | `TOOL_VIEW_SHEET` | 已接入 |
| `sidebar.workspaces` | `SIDEBAR_PANEL` | 已接入 |
| `sidebar.workspaces.session.menu.item` | `SIDEBAR_PANEL` | 已接入 |
| `sidebar.workspaces.session.row.action` | `SIDEBAR_PANEL` | 已接入 |
| `sidebar.panellist` | `SIDEBAR_PANEL` | 已接入 |
| `sidebar.right.pane.tab` | `RIGHT_PANEL` | 已接入（插件标签，内置标签由 App 实现） |
| `sidebar.right.pane.tab.title` | `RIGHT_PANEL` | 已接入（标签标题取 `tabTitle`） |
| `rightbar.session` | `RIGHT_PANEL` | 已接入 |
| `shell.overlay` | `PLUGIN_OVERLAY` | 已接入 |
| `settings.section` | `SETTINGS_SHEET` | 已接入 |
| `settings.general.item` | `SETTINGS_SHEET` | 已接入 |
| `settings.models.provider-card` | `SETTINGS_PROVIDER_CARD` | 已接入 |
| `plugins.tab` | `SETTINGS_SHEET` | 已接入 |

> 注：`NativeSlot` 是“承载位置”，多个内置 slot 可以映射到同一个落点；插件通过
> `SlotContribution.slot` 声明落点。内置控件本身也通过 `OfficialSlot` 走同一条
> slot 通道，因此插件可以 ENHANCE（加在旁边）或 OVERRIDE（接管并隐藏内置默认）。

## 新增落点的验收清单

- [ ] `NativeSlot` 枚举新增落点；
- [ ] `DshSlotRegistry` 的内置 slot 在本文档有归属；
- [ ] App 对应界面放 `OfficialSlot` / `NativeSlotHost` 容器；
- [ ] 示例或冒烟测试验证贡献可见；
- [ ] 更新本文档与自动生成的 `docs/slot-inventory.md`。

> 历史问题：`COMPOSER_DOCK` 首版没有宿主容器，插件声明后完全不可见，靠用户指出
> 才补上。现在用 `SlotCoverageTest` + `tools/slot_inventory.py` 避免复发。
