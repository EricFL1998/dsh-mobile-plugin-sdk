> English | [中文](ui-guideline.md)

# Native UI Design Guidelines (Plugin Side)

> Audience: developers building a **native companion APK** for a JS plugin.
> Goal: plugin settings pages, detail pages, and popovers share the same materials,
> spacing, and controls as built-in App pages, so users do not feel they have
> "switched to another app" when moving from App settings into a plugin page.

All colors, font sizes, corner radii, and row heights come from `PluginTheme`.
The SDK's `design/DshPanel.kt` and `design/DshControls.kt` already wrap these;
prefer using them directly.

---

## 1. Page Skeleton

```
Page (verticalScroll, 16dp horizontal padding)
├─ Section label        DshSectionLabel        11sp, tertiary text color
├─ Panel                DshPanel               16dp radius, 0.5dp hairline, 16dp padding
│  ├─ Row               DshPanelRow            44dp minimum row height
│  ├─ Divider           DshPanelDivider        0.5dp
│  └─ Inline action     DshPanelAction         brand-color text
├─ Panel gap            8dp
└─ Footer note          DshPanelCaption        12sp, secondary text color
```

Rules:

- Use **16dp** page padding; **8dp** between panels. Rows inside a panel rely on
  `DshPanelRow`'s built-in vertical spacing; do not stack extra `Spacer`s.
- One panel holds one category of settings. If a panel would exceed 6 rows, split it
  into two panels instead of piling every switch into one card.
- Do not put marketing copy on the first screen beyond the settings themselves; put
  explanations in `subtitle` or a `DshPanelCaption` at the bottom of the panel.

---

## 2. Panels: DshPanel

```kotlin
DshPanel(theme = theme, title = "账号") {
    DshPanelRow(theme, title = "当前账号", value = "demo@example.com", showDivider = true)
    DshPanelRow(theme, title = "自动续费", subtitle = "到期前 1 天提醒", value = "已开启")
    DshPanelAction("管理订阅", theme) { host.openUrl(manageUrl) }
}
```

- `title` is optional; panel titles use `textBody` + SemiBold.
- Prefer putting a status `value` on the right side of a row; use `trailing` when you
  need a button or switch.
- Only pass `onClick` for rows that are actually clickable; do not add a pressed
  state to non-clickable rows.

---

## 3. Rows and Sections

```kotlin
DshSectionLabel("订阅", theme)
DshPanelRow(theme, title = "套餐", value = "Pro")
DshPanelRow(theme, title = "下次扣费", value = "2026-11-01", showDivider = true)
DshPanelRow(theme, title = "管理", onClick = { /* ... */ })
```

- Row title: `textBody` + `colorLabelPrimary`.
- Subtitle/description: `textCaption` + `colorLabelSecondary`.
- Right-side value: `textBody` + `colorLabelSecondary`, single line with ellipsis.
- Row height is enforced by `DshPanelDefaults.RowMinHeight` (44dp); do not set your
  own `height`.

---

## 4. Buttons: DshButton

```kotlin
DshButton("保存", theme, onClick = ::save)                       // PRIMARY
DshButton("取消", theme, onClick = ::dismiss, variant = SECONDARY)
DshButton("删除", theme, onClick = ::delete, variant = DANGER)
DshTextButton("查看全部", theme) { /* ... */ }                    // inline text action
```

- **PRIMARY**: the single main action on the page (save/connect/start). Never show
  two PRIMARY buttons on the same screen.
- **SECONDARY**: cancel, minor actions, and buttons in a row.
- **GHOST / DshTextButton**: inline text actions such as "Edit" or "View all".
- **DANGER**: delete, sign out, revoke authorization; pair with
  `DshDialog(danger = true)` when a second confirmation is needed.
- Disabled state is uniformly reduced to 40% opacity via `enabled = false`; do not
  change colors yourself.
- Buttons default to 40dp height; keep the default inside dialogs and do not shrink
  them below 32dp.

---

## 5. Switches and Inputs

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

- Prefer `DshSwitchRow` for switches (readable full row, switch on the right); use
  `DshSwitch` only for custom row layouts.
- Always provide a `placeholder` for inputs; put error messages in a
  `DshPanelCaption` below the row and use `colorError`.

---

## 6. Chips and Dialogs

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

- `DshChip` is for status/category labels only, never for clickable actions.
- Dialog title: one sentence; body: explain the consequence; at most two buttons.
- For dangerous actions use `danger = true` on the confirm button and provide
  `cancelText`.

---

## 7. Theme Tokens

| Token | Purpose |
| --- | --- |
| `colorPanel` / `colorPanelBorder` | Panel background / 0.5dp hairline |
| `colorPanelElevated` | Dialog and overlay content background |
| `colorLabelPrimary` | Row titles, primary text |
| `colorLabelSecondary` | Subtitles, right-side values |
| `colorLabelTertiary` | Section labels, weak hints |
| `colorLink` | Text actions, links |
| `colorButtonPrimary` / `colorButtonPrimaryHover` | Primary button fill / pressed state |
| `colorButtonTextOnPrimary` | Primary button text |
| `colorButtonSecondaryBorder` / `colorButtonSecondaryText` | Secondary button border/text |
| `colorButtonDanger` | Dangerous button |
| `colorSwitchTrackOff` / `colorSwitchThumb` | Switch track/thumb |
| `colorInputBackground` / `colorInputBorder` | Text input |
| `colorChipBackground` / `colorChipText` | Chips |
| `cornerCard` / `cornerChip` | Panel/chip corner radius |
| `spacingUnit` | 8dp base spacing |

Send new color requirements to the SDK first; do not add hex values in plugins.

---

## 8. Prohibitions

- Do not use Material `Card`, `Button`, `OutlinedButton`, `Switch`, or `AlertDialog`
  as the main page structure; use the SDK's `DshPanel` / `DshButton` / `DshSwitch` /
  `DshDialog` instead.
- Do not hard-code `Color(0x...)`, font sizes, or dp corner radii; take all of them
  from `PluginTheme`.
- Do not branch on `isSystemInDarkTheme()`; the theme snapshot already contains the
  resolved light/dark values.
- Do not draw your own buttons/switches/inputs; if you need a new control, add it to
  the SDK first.
- Do not keep business state inside the companion APK; state and RPC stay in JS/Host,
  and the native side only renders and sends actions back.

---

## 9. Acceptance Checklist

- [ ] Page uses 16dp horizontal padding and 8dp panel gaps.
- [ ] All panels use `DshPanel`; no Material `Card`.
- [ ] All buttons use `DshButton`/`DshTextButton`; only one PRIMARY per screen.
- [ ] Switches use `DshSwitchRow`/`DshSwitch`; no Material `Switch`.
- [ ] Colors/font sizes/corner radii all come from `PluginTheme`, with no hard-coded
      values.
- [ ] Dangerous actions have a second confirmation with a DANGER confirm button.
- [ ] Toggling light/dark updates colors through `PluginTheme` without restarting the
      page.
- [ ] The page makes no network requests and reads/writes no token/secret.
