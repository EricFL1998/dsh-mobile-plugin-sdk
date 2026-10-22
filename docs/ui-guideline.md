# 原生 UI 设计规范（插件侧）

> 适用对象：给 JS 插件写**原生伴生 APK** 的开发者。
> 目标：插件设置页、详情页、popover 与 App 内置页面共用同一套材质、间距和控件，
> 用户从 App 设置页进入插件页时感觉不到「换了一个 App」。

所有颜色、字号、圆角、行高都从 `PluginTheme` 读取；SDK 的
`design/DshPanel.kt`、`design/DshControls.kt` 已经封装好，优先直接用。

---

## 1. 页面骨架

```
页面（verticalScroll，左右 16dp）
├─ 分组标题            DshSectionLabel        11sp，三级文字色
├─ 面板                DshPanel               16dp 圆角、0.5dp 发丝边、16dp 内边距
│  ├─ 行               DshPanelRow            44dp 最小行高
│  ├─ 分割线           DshPanelDivider        0.5dp
│  └─ 行内动作         DshPanelAction         品牌色文字
├─ 面板间距            8dp
└─ 底部说明            DshPanelCaption        12sp，二级文字色
```

规则：

- 页面左右边距 **16dp**；面板之间 **8dp**；面板内行与行之间用 `DshPanelRow` 自带的
  上下留白，不额外叠 `Spacer`。
- 一个面板只放一类设置；超过 6 行拆成两个面板，不要把所有开关堆进一张卡。
- 首屏不放「设置项本身」以外的营销文案；说明放 `subtitle` 或面板底部 `DshPanelCaption`。

---

## 2. 面板：DshPanel

```kotlin
DshPanel(theme = theme, title = "账号") {
    DshPanelRow(theme, title = "当前账号", value = "demo@example.com", showDivider = true)
    DshPanelRow(theme, title = "自动续费", subtitle = "到期前 1 天提醒", value = "已开启")
    DshPanelAction("管理订阅", theme) { host.openUrl(manageUrl) }
}
```

- `title` 可选；面板标题用 `textBody` + SemiBold。
- 行右侧优先放 `value`（状态值）；需要按钮/开关时用 `trailing`。
- 可点击行才传 `onClick`；不可点击的行不要加点击态。

---

## 3. 行与分组

```kotlin
DshSectionLabel("订阅", theme)
DshPanelRow(theme, title = "套餐", value = "Pro")
DshPanelRow(theme, title = "下次扣费", value = "2026-11-01", showDivider = true)
DshPanelRow(theme, title = "管理", onClick = { /* ... */ })
```

- 行标题：`textBody` + `colorLabelPrimary`。
- 副标题/说明：`textCaption` + `colorLabelSecondary`。
- 右侧值：`textBody` + `colorLabelSecondary`，单行省略。
- 行高由 `DshPanelDefaults.RowMinHeight`（44dp）保证；不要自己写 `height`。

---

## 4. 按钮：DshButton

```kotlin
DshButton("保存", theme, onClick = ::save)                       // PRIMARY
DshButton("取消", theme, onClick = ::dismiss, variant = SECONDARY)
DshButton("删除", theme, onClick = ::delete, variant = DANGER)
DshTextButton("查看全部", theme) { /* ... */ }                    // 行内文字动作
```

- **PRIMARY**：页面唯一主操作（保存/连接/开始）；同屏不要出现两个 PRIMARY。
- **SECONDARY**：取消、次要动作、并列按钮。
- **GHOST / DshTextButton**：行内「编辑/查看全部」等文字动作。
- **DANGER**：删除、退出、取消授权；需要二次确认时配 `DshDialog(danger = true)`。
- 禁用态由 `enabled = false` 统一降到 40% 透明度，不要自己改颜色。
- 按钮高度默认 40dp；对话框内保持默认，不要压到 32dp 以下。

---

## 5. 开关与输入

```kotlin
DshSwitchRow(
    theme = theme,
    title = "自动同步",
    subtitle = "开关尺寸与 App 设置页一致",
    checked = enabled,
    onCheckedChange = { enabled = it },
    showDivider = true,
)
DshSwitch(checked = enabled, theme = theme, onCheckedChange = { enabled = it })
DshTextField(value = draft, theme = theme, onValueChange = { draft = it }, placeholder = "输入模型 id")
```

- 开关优先用 `DshSwitchRow`（整行可读，右侧开关）；只有自定义行布局时才用 `DshSwitch`。
- 输入框必须给 `placeholder`；错误提示放行下方的 `DshPanelCaption`，颜色用 `colorError`。

---

## 6. 标签与对话框

```kotlin
DshChip("Beta", theme)
DshDialog(
    theme = theme,
    title = "取消授权？",
    body = "取消后需要重新登录。",
    confirmText = "取消授权",
    cancelText = "再想想",
    danger = true,
    onConfirm = ::revoke,
    onDismiss = ::dismiss,
)
```

- `DshChip` 只放状态/分类，不放可点击动作。
- 对话框标题一句话；正文说明后果；按钮最多两个。
- 危险操作确认按钮用 `danger = true`，取消按钮用 `cancelText`。

---

## 7. 主题 Token

| Token | 用途 |
| --- | --- |
| `colorPanel` / `colorPanelBorder` | 面板底色 / 0.5dp 发丝边 |
| `colorPanelElevated` | 对话框、浮层内容底色 |
| `colorLabelPrimary` | 行标题、主要文字 |
| `colorLabelSecondary` | 副标题、右侧值 |
| `colorLabelTertiary` | 分组标题、弱提示 |
| `colorLink` | 文字动作、链接 |
| `colorButtonPrimary` / `colorButtonPrimaryHover` | 主按钮底色 / 按压态 |
| `colorButtonTextOnPrimary` | 主按钮文字 |
| `colorButtonSecondaryBorder` / `colorButtonSecondaryText` | 次按钮边框/文字 |
| `colorButtonDanger` | 危险按钮 |
| `colorSwitchTrackOff` / `colorSwitchThumb` | 开关轨道/滑块 |
| `colorInputBackground` / `colorInputBorder` | 输入框 |
| `colorChipBackground` / `colorChipText` | 标签 |
| `cornerCard` / `cornerChip` | 面板/标签圆角 |
| `spacingUnit` | 8dp 基准间距 |

新增颜色需求先反馈到 SDK，不要在插件里补十六进制值。

---

## 8. 禁止项

- 禁止直接用 Material `Card`、`Button`、`OutlinedButton`、`Switch`、`AlertDialog` 作为页面主结构；
  用 SDK 的 `DshPanel` / `DshButton` / `DshSwitch` / `DshDialog`。
- 禁止硬编码 `Color(0x...)`、字号、dp 圆角；全部从 `PluginTheme` 取。
- 禁止写 `isSystemInDarkTheme()` 分支；主题快照已经包含明暗结果。
- 禁止自绘按钮/开关/输入框；需要新控件先加到 SDK。
- 禁止把业务状态放在伴生 APK 内；状态与 RPC 留在 JS/Host，原生只负责渲染与回传动作。

---

## 9. 验收清单

- [ ] 页面左右 16dp，面板间距 8dp。
- [ ] 所有面板使用 `DshPanel`，没有 Material `Card`。
- [ ] 所有按钮使用 `DshButton`/`DshTextButton`，同屏只有一个 PRIMARY。
- [ ] 开关使用 `DshSwitchRow`/`DshSwitch`，没有 Material `Switch`。
- [ ] 颜色/字号/圆角全部来自 `PluginTheme`，无硬编码。
- [ ] 危险操作有二次确认，确认按钮为 DANGER。
- [ ] 明暗主题切换后无需重启页面，颜色随 `PluginTheme` 更新。
- [ ] 页面无网络请求、无 token/secret 读写。
