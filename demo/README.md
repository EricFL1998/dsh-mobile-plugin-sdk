# Demo 插件（JS + 原生）

> 这是 SDK 仓库唯一的示例插件，由两份产物组成：
>
> - **JS 半边**：[`demo/js`](js/README.md) —— npm 包 `dsh-plugin-demo`；
> - **原生半边**：[`demo/native`](native/) —— Kotlin/Compose 伴侣 APK。
>
> 纯演示：不访问网络、不读写账号、不持有任何凭证；状态只存在 Host 进程内，Host
> 重启后回到初始值 42。它演示原生 UI 的每一种接入方式，代码可直接跑。

## 身份

| 字段 | 值 |
| --- | --- |
| JS 包名 | `dsh-plugin-demo`（0.1.0） |
| 伴生 `pluginId`（manifest `dsh.plugin.id`） | `demo.native-ui` |
| 伴生 APK 包名 | `com.dsh.mobile.dsh_plugin_demo` |
| manifest `dsh.plugin.jsPackage` | `dsh-plugin-demo` |
| manifest `dsh.plugin.version` | `1`（整数） |
| 伴生 APK `versionName` | `1.0.0` |
| 设置页 Web 注册 id / 原生 `PageEntry.id` | `demo` |
| 设置页组件身份 `componentId` | `dsh-plugin-demo#demo` |
| dock Web 注册 id | `demo-dock` |
| dock 组件身份 `componentId` | `dsh-plugin-demo#demo-dock` |

包名遵循 `com.dsh.mobile.<完整插件名>`：`dsh-plugin-demo` →
`com.dsh.mobile.dsh_plugin_demo`。`dsh.plugin.id` 与包名解耦，两个都已发布后不要改。

## 目录

```text
demo/
├─ README.md                        ← 本文件
├─ js/                              ← JS 半边（npm 包 dsh-plugin-demo）
│  ├─ package.json                  ← npm 身份 + dsh.client / dsh.native 声明
│  ├─ cordis.patch.yml              ← bundle 自激活：挂载 lib/index.js
│  ├─ lib/
│  │  ├─ index.js                   ← 插件入口：name / apply
│  │  ├─ constants.js               ← RPC 前缀、componentId、settings ns（三方约定）
│  │  ├─ state.js                   ← 内存状态仓（计数 42、上次动作、更新时间）
│  │  ├─ rpc.js                     ← /api/demo.* 路由 + 请求/响应信封
│  │  ├─ actions.js                 ← mobileUi 动作：bump / reset
│  │  ├─ catalog.js                 ← demo.catalog 返回的注册清单
│  │  └─ client.js                  ← Web 半边：settings.section + composer.dock
│  ├─ locale/{zh,en}.json           ← Web 回退界面文案
│  └─ test/rpc.test.mjs             ← node:test：信封、路由、动作、状态隔离
└─ native/                          ← 原生半边（伴侣 APK）
   ├─ build.gradle.kts              ← App 版本目录 + :plugin:native-sdk
   └─ src/main/
      ├─ AndroidManifest.xml
      └─ kotlin/com/dsh/mobile/dsh_plugin_demo/
         ├─ DemoPluginService.kt    ← Service 绑定 + 静态工厂 plugin()
         ├─ DemoNativePlugin.kt     ← DshUiPlugin：descriptor、渲染器、落点注册
         ├─ DemoSettingsPage.kt     ← PageContent：设置页整页（RPC 驱动）
         ├─ DemoDockContribution.kt ← COMPOSER_DOCK 胶囊 + PluginPopover
         ├─ DemoSlotContribution.kt ← 其余 NativeSlot 的演示贡献
         └─ DemoRpc.kt              ← 信封解析 + demoCall
```

## 覆盖的接入方式

- **PageContent**：`DemoSettingsPage`，`id = "demo"`，设置页出现「Demo 插件」入口，
  整页原生 Compose；
- **NativeSlot**：`DemoSlots.entries` 列出 22 个落点，其中 **21 个注入真实界面**；
  `WEB_ISLAND_FULL` 是整岛 Web 回退容器，Demo 不注入（会整屏覆盖 App），只在 Demo
  设置页列出并说明；
- **PluginPopover**：点击 dock 的 `Demo 42` 胶囊，浮层贴着胶囊上方、右对齐，材质
  来自 `PluginTheme` 的 popup 字段（App 菜单材质）；
- **ComponentRenderer**：`DemoRenderer` 演示 Host 卡片渲染入口（`supportedTypes =
  setOf("demo.card")`）；
- **RPC + 事件闭环**：设置页与 dock 都通过 RPC 读 Host 状态，通过
  `dispatchEvent` 把用户动作回传给 JS 插件。

落点清单：

```text
SETTINGS_SHEET            SETTINGS_PROVIDER_CARD   SIDEBAR_PANEL
COMPOSER_DOCK             COMPOSER_DOCK_ACTIVITY   COMPOSER_DOCK_USAGE
COMPOSER_DOCK_CONTEXT     COMPOSER_LEFT            COMPOSER_RIGHT
COMPOSER_MODEL            COMPOSER_PERMISSION      COMPOSER_PLAN
COMPOSER_ATTACHMENTS      COMPOSER_BAR             CONVERSATION_VIEW
SESSION_HEADER_CORNER     COMPOSER_OVERLAY         PLUGIN_OVERLAY
MESSAGE_ACTION            TOOL_VIEW_SHEET          CHAT_STREAM
WEB_ISLAND_FULL           ← 列出但不注入
```

22 个条目中，除 `WEB_ISLAND_FULL` 外的 21 个都注册了 `SlotContribution`（dock 单独用
`DemoDockContribution`）。落点与宿主位置的完整映射见
[`../slot-mapping.md`](../slot-mapping.md)。

## RPC 契约

原生端通过 `PluginRpcHost.callRpc("demo.<endpoint>", payload)` 调用；Web 端通过
`connection.rpc.call("/api", "demo.<endpoint>", payload)` 调用。两边走同一个通道。

请求体（`payload` 原样传递，不包 `args`）：

```json
{ "type": "client-request", "rpcId": "…", "method": "demo.status", "payload": {} }
```

成功响应：

```json
{ "type": "server-response", "rpcId": "…", "result": { "ok": true, "value": { } } }
```

业务失败也是 HTTP 200，错误放在 `result.error`：

```json
{ "type": "server-response", "rpcId": "…",
  "result": { "ok": false, "error": { "code": "demo/unknown-endpoint", "message": "…", "details": {} } } }
```

传输层错误才用非 200：`400`（JSON 无法解析或信封不匹配）、`415`（非
`application/json`）。错误码：`demo/bad-request`（400）、
`demo/unsupported-media-type`（415）、`demo/unknown-endpoint`（HTTP 200 +
`result.error`）。

| 端点 | payload | value |
| --- | --- | --- |
| `demo.status` | `{}` | `{ greeting, count, lastAction, updatedAt, startedAt, uptimeMs }` |
| `demo.catalog` | `{}` | `{ plugin, companion, registrations[], endpoints[] }` |
| `demo.ping` | `{}` | `{ pong: true, serverTime }` |
| `demo.bump` | `{ by?: number }` | 更新后的 status 快照 |
| `demo.reset` | `{}` | 重置后的 status 快照 |
| `demo.echo` | 任意 JSON | `{ received: <原样回显> }` |

设置页读 `demo.status` + `demo.catalog`；「服务端 +1」走事件回传，「重置」直接调
`demo.reset`。dock 同样读 `demo.status`，并在 popover 里提供「服务端 +1 · 事件」
「刷新 · RPC」「重置 · RPC」。

## 原生事件契约

原生控件不执行插件 JS，只上报「哪个组件、哪个动作、什么 payload」：

| componentId | actionId | payload | 效果 |
| --- | --- | --- | --- |
| `dsh-plugin-demo#demo` | `bump` | `{ actionId: "bump", by?: number }` | 计数 +n |
| `dsh-plugin-demo#demo` | `reset` | `{ actionId: "reset" }` | 重置为 42 |
| `dsh-plugin-demo#demo-dock` | `bump` | `{ actionId: "bump", by?: number }` | 计数 +n |
| `dsh-plugin-demo#demo-dock` | `reset` | `{ actionId: "reset" }` | 重置为 42 |

事件名由 bridge 限定为 `press` / `input` / `toggle` / `submit`；Demo 统一用 `submit`。
完整链路：

```text
原生控件 onClick
  → host.dispatchEvent(componentId, "submit", { actionId: "bump", by: 1 })
  → App bridge client-slots/dispatch
  → bridge 命中 JS 插件的 mobileUi.registerAction
  → Host 状态变化，bridge 排 client/refresh
  → 原生页 collect refreshEvents → 重新 callRpc("demo.status")
```

「重置」是纯 RPC 写路径，不经过事件。`DemoRpc.kt` 集中了方法名、组件身份与信封解析。

## 构建（在 App 仓库中完成）

SDK 仓库不带 Gradle Wrapper，`demo/native` 的构建放到 App 开发仓库里执行。
`demo/native/build.gradle.kts` 使用 App 仓库的版本目录，并依赖 App 内的
`:plugin:native-sdk` 模块。

1. 把 `demo/native` 复制到 App 仓库的 `samples/demo-native`；
2. 在 App 仓库 `settings.gradle.kts` 中加入：

   ```kotlin
   include(":samples:demo-native")
   ```

3. 在 **App 仓库根目录**构建：

   ```bash
   ./gradlew :samples:demo-native:assembleDebug
   ```

4. 产物：

   ```text
   samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk
   ```

为你自己的插件构建伴侣 APK 时，不需要 App 内模块：用
[`../artifacts/dsh-native-ui-sdk-release.aar`](../artifacts/dsh-native-ui-sdk-release.aar)
或 [`../sdk/`](../sdk/README.md) 源码，写法见根目录
[README](../README.md) 第 2 节。

## 安装与真机验证

```bash
# 1) 安装 JS 插件到 Host profile（本地路径）
dsh plugin --profile web add ./demo/js

# 2) 安装伴侣 APK
adb install -r samples/demo-native/build/outputs/apk/debug/demo-native-debug.apk

# 3) 回到 App 前台，等待重扫（安装/卸载后 App 会重新发现 APK）
```

验证步骤：

1. 设置 → 插件界面出现「Demo 插件」，点进去是原生页面，显示计数、上次动作、更新时间；
2. 输入栏上方出现 `Demo 42` 胶囊；
3. 点按胶囊，popover 紧贴胶囊上方、右对齐，使用 App 菜单材质；
4. 点「服务端 +1」计数变化，点「重置」回到 42；
5. 消息流、工具详情、设置页等区块出现 `Demo · ...` 标记，其余落点出现 `D` 徽标
   （共 21 个注入落点）；
6. 卸载 Demo APK 后，设置页与 dock 回到 Web 版本，仍能读 `demo.status`。

## 自测

```bash
# RPC / 动作 / 状态隔离（无需 Host、Android、网络）
node --test demo/js/test/rpc.test.mjs

# 扫描器：JS 注册的 2 个落点都有 NativeSlot 映射、宿主与伴生实现
python tools/plugin_ui_scanner.py \
  --plugin demo/js \
  --companion demo/native/src/main/kotlin \
  --check
```

扫描器自带 [`../tools/sdk-mapping.json`](../tools/sdk-mapping.json) 映射/宿主快照，在
SDK 仓库根目录可直接运行，不需要 App 仓库；输出 `plugin UI coverage check OK`、
2 个落点 `impl=True`。需核对 App 侧宿主源码时，可加可选参数 `--root <App 仓库>`。结果
存档在 [`../docs/plugin-ui-inventory-example.md`](../docs/plugin-ui-inventory-example.md)。

## 相关文档

- [SDK 主文档](../README.md)：AAR 接入、manifest、页面/落点、RPC、构建与发布；
- [SDK 开发者文档](../docs/plugin-sdk-guide.md)：从装包到落点、RPC、生命周期；
- [源码驱动接入流程](../docs/source-driven-native-ui.md)：扫描 → 缺口 → 实现；
- [Plugin UI 清单示例](../docs/plugin-ui-inventory-example.md)：Demo 的真实扫描输出；
- [发布与打 tag](../docs/publishing.md)：插件与 SDK 的发布约定；
- [文档站案例](../docs/index.html#case)：Demo 插件的接入清单与验证输出。
