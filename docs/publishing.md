# 发布与打 tag

> 中文 | [English](publishing.en.md)

这份文档说明 DSH Mobile 原生 UI 插件的发布物，以及 GitHub topic / Release / tag 的约定。
插件商店只依赖这些约定发现插件，不读取任何私有索引。

## 0. 发布物

| 发布物 | 去哪里 | 谁消费 |
| --- | --- | --- |
| JS 插件 | npm（`dsh-plugin-*`） | Host 的 `dsh plugin add` / 插件页「添加插件」 |
| 伴侣 APK | GitHub Releases（`.apk` asset） | 插件商店、系统安装器 |
| SDK AAR（SDK 仓库自身） | GitHub Releases（`.aar` asset） | 插件作者编译伴侣 APK |

一个插件可以只发 JS，也可以 JS + APK 成对发布。只发 APK 时，APK 必须通过
`dsh.plugin.jsPackage` 指向可安装的 JS 插件，否则 App 只能显示 APK 本身，无法补全原生
界面内容。

本文示例统一使用仓库里的 Demo 插件：
`dsh-plugin-demo` / `demo.native-ui` / `com.dsh.mobile.dsh_plugin_demo`。

## 1. JS 插件发布

### 1.1 命名

- npm 包名必须以 `dsh-plugin-` 开头，例如 `dsh-plugin-demo`；
- `package.json` 的 `name` 必须与 APK manifest 里的 `dsh.plugin.jsPackage` 完全一致
  （含 scope，如果使用 `@scope/name`）；
- 版本用 semver：`0.1.0`、`1.0.0`。

### 1.2 发布

```bash
npm publish --access public
```

发布前确认：

```bash
npm pack --dry-run          # 只包含运行需要的文件
npm view dsh-plugin-demo version   # 确认 registry 上是这次版本
```

### 1.3 GitHub 侧

JS 插件对应的仓库建议打 topic：

```bash
gh api -X PUT repos/<owner>/<repo>/topics \
  -f 'names[]=dsh-plugin' \
  -f 'names[]=dsh-mobile-plugin'
```

`dsh-mobile-plugin` 是插件商店发现伴生 APK 的 topic；`dsh-plugin` 用于普通插件生态检索。

## 2. 伴侣 APK 发布

### 2.1 版本与 tag

推荐 tag 格式：

```text
v<js-version>                 # 与 JS 插件同版本，例如 v1.0.0
v<js-version>-native          # 只更新原生伴生 APK 时，例如 v0.1.0-native
```

tag 名会作为 Release 标题展示，插件商店详情里显示的是 Release 的 `tag_name`，所以 tag
要能让人看懂这是哪个插件的哪个版本。

### 2.2 构建

把 SDK 仓库的 `demo/native` 复制到 App 仓库的 `samples/demo-native`，在 App 仓库
`settings.gradle.kts` 中 `include(":samples:demo-native")` 后构建：

```bash
./gradlew :samples:demo-native:assembleDebug
# 或 assembleRelease；产物在
# samples/demo-native/build/outputs/apk/{debug,release}/
```

SDK 仓库不带 Gradle Wrapper，不要在本仓库根目录执行 `./gradlew`。完整的复制与构建步骤见
[`demo/README.md`](../demo/README.md#构建在-app-仓库中完成)。

APK 文件名建议：

```text
<plugin-name>-companion-<version>.apk
```

例如：

```text
dsh-plugin-demo-companion-1.0.0.apk
```

### 2.3 签名

- 测试版可以用 debug 签名；
- 正式分发前换 release keystore，并在 README 说明签名指纹；
- 同一 `dsh.plugin.id` 的签名指纹发生变化时，App 会标记 `SIGNATURE_MISMATCH`，不会加载。

### 2.4 创建 Release

```bash
git tag -a v1.0.0 -m "dsh-plugin-demo companion 1.0.0"
git push origin v1.0.0

gh release create v1.0.0 \
  dsh-plugin-demo-companion-1.0.0.apk \
  --title "v1.0.0" \
  --notes "Companion APK for dsh-plugin-demo 0.1.0"
```

注意：

- 插件商店读取 GitHub 的 **latest release**；预发布（prerelease）不会被 `/releases/latest`
  返回；
- Release 至少要有一个 `.apk` asset，否则详情页显示「最新 Release 中没有 APK」；
- 资产名以 `.apk` 结尾（大小写不敏感）；
- 更新 APK 时创建新的 tag + Release，不要覆盖已有资产。

### 2.5 让插件商店发现（tag / topic 必做）

商店不是“发到 npm 就自动出现”，必须同时满足下面三项：

| 项 | 值 | 作用 |
| --- | --- | --- |
| 仓库 topic | `dsh-mobile-plugin` | 商店索引按 `topic:dsh-mobile-plugin` 搜索仓库 |
| Release tag | 例如 `v0.17.18` / `v0.17.18-native` | 商店读 **latest release**，tag 是它的锚点 |
| Release 资产 | 至少一个 `.apk` | 详情页列出 APK；没有资产会显示“没有 APK” |

仓库必须是公开仓库；tag 对应的 Release 不能是 prerelease（`/releases/latest` 会跳过）。

```bash
# 1) 给仓库加 topic（GitHub 仓库页右上角 About → Topics 也可以）
gh api -X PUT repos/<owner>/<repo>/topics \
  -f 'names[]=dsh-plugin' \
  -f 'names[]=dsh-mobile-plugin' \
  -f 'names[]=android'

# 2) 打 tag 并推上去
git tag -a v0.17.18 -m "companion 0.17.18"
git push origin v0.17.18

# 3) 用这个 tag 创建 Release，并上传 APK 资产
gh release create v0.17.18 dsh-plugin-<name>-companion-0.17.18.apk \
  --title "v0.17.18" \
  --notes "Companion APK for <js-package> 0.17.18"
```

商店索引由 SDK 仓库的 GitHub Action 定时生成（`docs/store/index.json`，约每 30 分钟
刷新一次）：

1. Action 搜索 `topic:dsh-mobile-plugin` 得到仓库列表；
2. 对每个仓库读取 `releases/latest` 与 `.apk` 资产，写入索引；
3. App 只读这份索引，不直接请求 GitHub API；索引生成后最多约 30 分钟生效。

因此：

- 只改 README 不会出现在商店，**必须有 topic + Release（tag）**；
- 更新 APK 要用新 tag + 新 Release，不要覆盖旧资产；
- 商店列表没出现时，先检查 topic 是否保存成功、Release 是否 latest、资产是否以 `.apk` 结尾。

### 2.6 更新提醒

插件商店用“仓库最近 push 时间”和“本机已安装 APK 的 `lastUpdateTime`”做更新提醒：

- 仓库在安装之后有新的 push → 列表/详情显示「有新版本」；
- 点进详情安装最新 Release APK；
- 只改 README 也会触发提醒，发版后以 Release 为准。

## 3. SDK AAR 发布（SDK 仓库自身）

SDK 仓库的发布物是 `dsh-native-ui-sdk-release.aar`。`sdk/` 是独立工程（不带 Gradle
Wrapper），用 Android Studio 打开该目录，或用兼容 AGP 8.9 的 Gradle 8.11+ 构建：

```bash
# 在 sdk/ 目录
gradle assembleRelease

# 复制为仓库分发的固定文件名
cp build/outputs/aar/*-release.aar ../artifacts/dsh-native-ui-sdk-release.aar
```

打 tag 与发布：

```bash
git tag -a sdk-v1.0.0 -m "DSH Native UI SDK 1.0.0"
git push origin sdk-v1.0.0
gh release create sdk-v1.0.0 \
  artifacts/dsh-native-ui-sdk-release.aar \
  --title "SDK v1.0.0" \
  --notes "Native UI SDK AAR"
```

SDK tag 用 `sdk-v` 前缀，避免和插件伴生 APK 的 `v` 混淆。

## 4. 版本对照

| 位置 | 字段 | 说明 |
| --- | --- | --- |
| `AndroidManifest.xml` | `dsh.plugin.version` | 伴侣 APK 描述符版本（整数） |
| `AndroidManifest.xml` | `dsh.plugin.apiVersion` | SDK/宿主协议版本，当前 `1` |
| `AndroidManifest.xml` | `dsh.plugin.minAppVersion` | 最低 App versionCode |
| `AndroidManifest.xml` | `dsh.plugin.jsPackage` | 配对的 JS npm 包名 |
| `build.gradle.kts` | `versionName` | APK 展示版本，建议等于 JS 版本 |
| `package.json` | `version` | JS 插件版本 |
| git tag | `v...` / `sdk-v...` | Release 标识 |

## 5. 发布检查清单

- [ ] JS `package.json.name` = APK `dsh.plugin.jsPackage`
- [ ] npm 已发布目标版本，`npm view <name> version` 正确
- [ ] APK `apiVersion` / `minAppVersion` 与目标 App 匹配
- [ ] Release tag 唯一，资产名以 `.apk` 结尾
- [ ] Release 不是 prerelease（否则商店读不到 latest）
- [ ] 仓库 topics 包含 `dsh-mobile-plugin`
- [ ] Release notes 写明插件名、版本、配对 JS 包名
- [ ] 正式分发使用 release keystore，并记录签名指纹
- [ ] 安装后到「插件 → 插件商店」确认列表/详情/安装/更新提醒正常

## 6. 相关文档

- [`../demo/README.md`](../demo/README.md)：Demo 插件的身份、契约与构建；
- [`../README.md`](../README.md)：SDK 接入与版本；
- [`plugin-sdk-guide.md`](plugin-sdk-guide.md)：原生插件开发规范；
- [`../sdk/README.md`](../sdk/README.md)：SDK 源码与独立构建。
