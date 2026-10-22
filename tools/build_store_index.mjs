/**
 * 生成插件商店静态索引：topic:dsh-mobile-plugin 的仓库 + latest release。
 *
 * 由 .github/workflows/store-index.yml 定时运行，输出 docs/store/index.json；
 * App 只读这一份 JSON，不再逐台设备请求 api.github.com，规避匿名限流。
 *
 * 需要环境变量 GH_TOKEN（GitHub Actions 用 GITHUB_TOKEN 即可）。
 */

const token = process.env.GH_TOKEN
if (!token) {
  console.error('GH_TOKEN is required')
  process.exit(1)
}

const headers = {
  Authorization: `Bearer ${token}`,
  Accept: 'application/vnd.github+json',
  'User-Agent': 'dsh-mobile-plugin-store-index',
}

async function api(path) {
  const response = await fetch(`https://api.github.com${path}`, { headers })
  if (!response.ok) {
    throw new Error(`GitHub API ${response.status} for ${path}: ${(await response.text()).slice(0, 200)}`)
  }
  return response.json()
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

const search = await api('/search/repositories?q=topic:dsh-mobile-plugin&sort=updated&order=desc&per_page=50')

const repos = []
for (const item of search.items ?? []) {
  let release = null
  try {
    const rel = await api(`/repos/${item.full_name}/releases/latest`)
    release = {
      tag: rel.tag_name ?? '',
      htmlUrl: rel.html_url ?? '',
      publishedAt: rel.published_at ? Date.parse(rel.published_at) : 0,
      apks: (rel.assets ?? [])
        .filter(asset => (asset.name ?? '').toLowerCase().endsWith('.apk'))
        .map(asset => ({
          name: asset.name,
          url: asset.browser_download_url,
          sizeBytes: asset.size ?? 0,
        })),
    }
  } catch (error) {
    // 仓库没有 release 时 API 返回 404；索引里保留 null，详情页会显示“暂无 APK”。
    console.error(`release skipped for ${item.full_name}: ${error.message}`)
  }
  repos.push({
    fullName: item.full_name,
    description: item.description ?? '',
    stars: item.stargazers_count ?? 0,
    htmlUrl: item.html_url ?? '',
    pushedAt: item.pushed_at ? Date.parse(item.pushed_at) : 0,
    release,
  })
  await sleep(150)
}

process.stdout.write(JSON.stringify({
  generatedAt: new Date().toISOString(),
  repos,
}, null, 2) + '\n')
