# Demo 插件（虚构案例）

> 这是 SDK 仓库唯一的示例插件，**不是真实插件**：不登录、不联网、没有账号逻辑。
> 它只演示原生 UI 的每一种插入方式，方便对照 `plugin/native-sdk` 的契约。

## 身份

| 字段 | 值 |
| --- | --- |
| 伴侣包名 | `com.dsh.mobile.dsh_plugin_demo` |
| `dsh.plugin.id` | `demo.native-ui` |
| JS 包名 | `dsh-plugin-demo`（虚构） |
| 版本 | 1.0.0 |

包名遵循 `com.dsh.mobile.<完整插件名>`：`dsh-plugin-demo` →
`com.dsh.mobile.dsh_plugin_demo`。

## 覆盖的插入方式

- **PageContent**：`DemoSettingsPage`，设置页出现「Demo 插件」入口；
- **全部 NativeSlot**：`DemoSlots.entries` 列出 21 个落点，`DemoSlotContribution`
  逐个注册；dock 单独由 `DemoDockContribution` 展示 popup；
- **PluginPopover**：点击 dock 的 `Demo 42%` 胶囊，浮层贴着胶囊上方、右对齐，
  材质来自 `PluginTheme` 的 popup 字段（App `DshMenuCard` 映射）；
- **ComponentRenderer**：`DemoRenderer` 演示 Host 卡片渲染入口。

落点清单：

```
SETTINGS_SHEET            SETTINGS_PROVIDER_CARD   SIDEBAR_PANEL
COMPOSER_DOCK             COMPOSER_DOCK_ACTIVITY   COMPOSER_DOCK_USAGE
COMPOSER_DOCK_CONTEXT     COMPOSER_LEFT            COMPOSER_RIGHT
COMPOSER_MODEL            COMPOSER_PERMISSION      COMPOSER_PLAN
COMPOSER_ATTACHMENTS      COMPOSER_BAR             CONVERSATION_VIEW
SESSION_HEADER_CORNER     COMPOSER_OVERLAY         PLUGIN_OVERLAY
MESSAGE_ACTION            TOOL_VIEW_SHEET          CHAT_STREAM
WEB_ISLAND_FULL
```

## 构建

Demo 的 `build.gradle.kts` 使用 App 仓库的 Gradle 版本目录。把 `demo/native`
复制到 App 仓库的 `samples/demo-native`，在 `settings.gradle.kts` 中加入：

```kotlin
include(":samples:demo-native")
```

然后：

```bash
./gradlew :samples:demo-native:assembleDebug
adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk
```

## 验证

安装后：

1. 设置页 → 插件界面出现「Demo 插件」，点进去能看到 21 个落点清单；
2. 输入栏出现 `Demo` 标记、dock 出现 `Demo 42%`；
3. 点击/长按 `Demo 42%`，popup 紧贴胶囊，使用 App 菜单材质；
4. 消息流、工具详情、设置页等位置出现 `Demo · ...` 标记；
5. 未安装 Demo APK 时，所有位置自动消失，不影响 App。

> 真实插件不要把状态/动作留在 Compose 里；Demo 只展示 UI。真实实现请用
> `PluginRpcHost` / `PluginModelHost`，数据留在 Host。
