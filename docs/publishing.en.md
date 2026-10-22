> English | [中文](publishing.md)

# Publishing and Tagging

This document describes the release artifacts of DSH Mobile native UI plugins, along with the
GitHub topic / Release / tag conventions. The plugin store relies only on these conventions to
discover plugins and does not read any private index.

## 0. Release Artifacts

| Artifact | Destination | Consumer |
| --- | --- | --- |
| JS plugin | npm (`dsh-plugin-*`) | Host's `dsh plugin add` / the "Add plugin" entry on the Plugins page |
| Companion APK | GitHub Releases (`.apk` asset) | Plugin store, system installer |
| SDK AAR (SDK repository itself) | GitHub Releases (`.aar` asset) | Plugin authors building companion APKs |

A plugin can ship JS only, or ship JS + APK as a pair. When shipping APK only, the APK must point
to an installable JS plugin through `dsh.plugin.jsPackage`; otherwise the App can only show the APK itself and cannot fill in the native
UI content.

The examples in this document all use the Demo plugin in the repository:
`dsh-plugin-demo` / `demo.native-ui` / `com.dsh.mobile.dsh_plugin_demo`.

## 1. Publishing the JS Plugin

### 1.1 Naming

- The npm package name must start with `dsh-plugin-`, for example `dsh-plugin-demo`;
- The `name` in `package.json` must exactly match `dsh.plugin.jsPackage` in the APK manifest
  (including the scope, if using `@scope/name`);
- Use semver versions: `0.1.0`, `1.0.0`.

### 1.2 Publishing

```bash
npm publish --access public
```

Before publishing, confirm:

```bash
npm pack --dry-run          # 只包含运行需要的文件
npm view dsh-plugin-demo version   # 确认 registry 上是这次版本
```

### 1.3 GitHub Side

It is recommended to add topics to the repository corresponding to the JS plugin:

```bash
gh api -X PUT repos/<owner>/<repo>/topics \
  -f 'names[]=dsh-plugin' \
  -f 'names[]=dsh-mobile-plugin'
```

`dsh-mobile-plugin` is the topic by which the plugin store discovers companion APKs; `dsh-plugin` is used for general plugin ecosystem search.

## 2. Publishing the Companion APK

### 2.1 Versions and Tags

Recommended tag formats:

```text
v<js-version>                 # 与 JS 插件同版本，例如 v1.0.0
v<js-version>-native          # 只更新原生伴生 APK 时，例如 v0.1.0-native
```

The tag name is shown as the Release title, and the plugin store details show the Release's `tag_name`, so the tag
must make it clear which plugin and which version it belongs to.

### 2.2 Building

Copy `demo/native` from the SDK repository into the App repository's `samples/demo-native`, add
`include(":samples:demo-native")` to the App repository's `settings.gradle.kts`, then build:

```bash
./gradlew :samples:demo-native:assembleDebug
# 或 assembleRelease；产物在
# samples/demo-native/build/outputs/apk/{debug,release}/
```

The SDK repository does not ship a Gradle Wrapper; do not run `./gradlew` from this repository's root. For the complete copy and build steps, see
[`demo/README.md`](../demo/README.md#构建在-app-仓库中完成).

Recommended APK file name:

```text
<plugin-name>-companion-<version>.apk
```

For example:

```text
dsh-plugin-demo-companion-1.0.0.apk
```

### 2.3 Signing

- Test builds may use debug signing;
- Switch to a release keystore before official distribution, and document the signing fingerprint in the README;
- If the signing fingerprint for the same `dsh.plugin.id` changes, the App marks it `SIGNATURE_MISMATCH` and will not load it.

### 2.4 Creating a Release

```bash
git tag -a v1.0.0 -m "dsh-plugin-demo companion 1.0.0"
git push origin v1.0.0

gh release create v1.0.0 \
  dsh-plugin-demo-companion-1.0.0.apk \
  --title "v1.0.0" \
  --notes "Companion APK for dsh-plugin-demo 0.1.0"
```

Notes:

- The plugin store reads GitHub's **latest release**; prereleases are not returned by `/releases/latest`;
- A Release must have at least one `.apk` asset; otherwise the details page shows "No APK in the latest Release";
- Asset names must end with `.apk` (case-insensitive);
- When updating the APK, create a new tag + Release; do not overwrite existing assets.

### 2.5 Getting Discovered by the Plugin Store (tag / topic required)

The store does not "appear automatically once published to npm". All three of the following must be satisfied:

| Item | Value | Effect |
| --- | --- | --- |
| Repository topic | `dsh-mobile-plugin` | The store index searches repositories by `topic:dsh-mobile-plugin` |
| Release tag | e.g. `v0.17.18` / `v0.17.18-native` | The store reads the **latest release**; the tag is its anchor |
| Release asset | At least one `.apk` | The details page lists APKs; without an asset it shows "No APK" |

The repository must be public; the Release behind the tag must not be a prerelease (`/releases/latest` skips those).

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

The store index is generated periodically by a GitHub Action in the SDK repository (`docs/store/index.json`, refreshed
about every 30 minutes):

1. The Action searches `topic:dsh-mobile-plugin` to get the repository list;
2. For each repository it reads `releases/latest` and the `.apk` assets, and writes them into the index;
3. The App only reads this index and does not call the GitHub API directly; changes take effect at most about 30 minutes after index generation.

Therefore:

- Editing only the README will not put the plugin in the store; **a topic + Release (tag) is required**;
- To update the APK, use a new tag + new Release; do not overwrite old assets;
- If the plugin does not appear in the store list, first check whether the topic was saved, whether the Release is the latest, and whether the asset name ends with `.apk`.

### 2.6 Update Notifications

The plugin store uses "the repository's latest push time" and "the locally installed APK's `lastUpdateTime`" to notify about updates:

- A new push to the repository after installation → the list/details show "Update available";
- Open the details and install the latest Release APK;
- Editing only the README also triggers the notification; after a release, the Release is authoritative.

## 3. Publishing the SDK AAR (SDK Repository Itself)

The SDK repository's release artifact is `dsh-native-ui-sdk-release.aar`. `sdk/` is a standalone project (without a Gradle
Wrapper); open that directory in Android Studio, or build with Gradle 8.11+ compatible with AGP 8.9:

```bash
# 在 sdk/ 目录
gradle assembleRelease

# 复制为仓库分发的固定文件名
cp build/outputs/aar/*-release.aar ../artifacts/dsh-native-ui-sdk-release.aar
```

Tag and publish:

```bash
git tag -a sdk-v1.0.0 -m "DSH Native UI SDK 1.0.0"
git push origin sdk-v1.0.0
gh release create sdk-v1.0.0 \
  artifacts/dsh-native-ui-sdk-release.aar \
  --title "SDK v1.0.0" \
  --notes "Native UI SDK AAR"
```

SDK tags use the `sdk-v` prefix to avoid confusion with the `v` prefix of plugin companion APKs.

## 4. Version Reference

| Location | Field | Description |
| --- | --- | --- |
| `AndroidManifest.xml` | `dsh.plugin.version` | Companion APK descriptor version (integer) |
| `AndroidManifest.xml` | `dsh.plugin.apiVersion` | SDK/host protocol version, currently `1` |
| `AndroidManifest.xml` | `dsh.plugin.minAppVersion` | Minimum App versionCode |
| `AndroidManifest.xml` | `dsh.plugin.jsPackage` | Paired JS npm package name |
| `build.gradle.kts` | `versionName` | APK display version; recommended to equal the JS version |
| `package.json` | `version` | JS plugin version |
| git tag | `v...` / `sdk-v...` | Release identifier |

## 5. Publishing Checklist

- [ ] JS `package.json.name` = APK `dsh.plugin.jsPackage`
- [ ] The target version is published to npm, and `npm view <name> version` is correct
- [ ] APK `apiVersion` / `minAppVersion` match the target App
- [ ] Release tag is unique, and asset names end with `.apk`
- [ ] Release is not a prerelease (otherwise the store cannot read latest)
- [ ] Repository topics include `dsh-mobile-plugin`
- [ ] Release notes state the plugin name, version, and paired JS package name
- [ ] Official distribution uses a release keystore, and the signing fingerprint is recorded
- [ ] After installation, check Plugins → Plugin Store to confirm the list/details/install/update notification work

## 6. Related Documents

- [`../demo/README.md`](../demo/README.md): Demo plugin identity, contract, and build;
- [`../README.md`](../README.md): SDK integration and versions;
- [`plugin-sdk-guide.md`](plugin-sdk-guide.md): native plugin development specification;
- [`../sdk/README.md`](../sdk/README.md): SDK sources and standalone build.
