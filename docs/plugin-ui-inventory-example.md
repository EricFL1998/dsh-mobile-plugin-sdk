# 插件 UI / 功能清单：dsh-plugin-subscription-oauth

> 由 `tools/plugin_ui_scanner.py` 直接扫描插件源码（含 `.js.map` 内嵌 TSX）生成。
> 不再需要用户截图：slot、entry、组件、RPC、文案与组件树都在这里。

- 包：`dsh-plugin-subscription-oauth` 0.17.17
- 源文件：10
- 注册落点：4
- RPC 端点：24

## 落点与原生状态

| slot | entry | order | component | 原生落点 | 宿主 | 数据依赖 |
| --- | --- | --- | --- | --- | --- | --- |
| `conversation.composer.dock` | `subscription-usage` | — | `UsageChip` | `COMPOSER_DOCK` | 是 | `modelSelection`, `rpc:usage` |
| `conversation.input.right` | `subscription-fast-mode` | — | `FastModeButton` | `COMPOSER_RIGHT` | 是 | `modelSelection`, `rpc:model-variants` |
| `settings.models.provider-card` | `—` | — | `ProviderCardExtras` | `未映射` | **否** | `rpc:cancel`, `rpc:login`, `rpc:status`, `rpc:usage` |
| `settings.section` | `subscriptions` | — | `SubscriptionsSection` | `SETTINGS_SHEET` | 是 | `rpc:addModel`, `rpc:auto-state`, `rpc:cancel`, `rpc:checkin`, `rpc:discover`, `rpc:login`, `rpc:logout`, `rpc:refresh`, `rpc:reload`, `rpc:removeModel`, `rpc:setAutoOn`, `rpc:setDefault`, `rpc:setModelOrder`, `rpc:setModelVisible`, `rpc:status`, `rpc:tasks`, `rpc:usage` |

## 组件与可见 UI

### `UsageChip`（conversation.composer.dock）

- 文案：`usageChipTitle`、`:bonus`、`true`、`button`、`dialog`

```json
[
  {
    "type": "ChipReading",
    "text": "",
    "children": [
      {
        "type": "readonly",
        "text": "",
        "children": [
          {
            "type": "readonly",
            "text": "",
            "children": [
              {
                "type": "string",
                "text": "",
                "children": [
                  {
                    "type": "readonly",
                    "text": "",
                    "children": [
                      {
                        "type": "HTMLElement",
                        "text": "",
                        "children": [
                          {
                            "type": "HTMLElement",
                            "text": "",
                            "children": [
                              {
                                "type": "HTMLElement",
                                "text": "",
                                "children": [
                                  {
                                    "type": "T",
                                    "text": "",
                                    "children": [
                                      {
                                        "type": "ChipFrame",
                                        "text": "",
                                        "children": [
                                          {
                                            "type": "span",
                                            "text": "",
                                            "children": []
                                          }
                                        ]
                                      },
                                      {
                                        "type": "span",
                                        "text": "",
                                        "children": []
                                      },
                                      {
                                        "type": "span",
                                        "text": "",
                                        "children": [
                                          {
                                            "type": "span",
                                            "text": "",
                                            "children": [
                                              {
                                                "type": "span",
                                                "text": "",
                                                "children": []
                                              },
                                              {
                                                "type": "span",
                                                "text": "",
                                                "children": []
                                              }
                                            ]
                                          }
                                        ]
                                      },
                                      {
                                        "type": "span",
                                        "text": "",
                                        "children": []
                                      }
                                    ]
                                  },
                                  {
                                    "type": "ChipFrame",
                                    "text": "usageChipTitle",
                                    "children": [
                                      {
                                        "type": "span",
                                        "text": ":bonus",
                                        "children": [
                                          {
                                            "type": "span",
                                            "text": "",
                                            "children": []
                                          }
                                        ]
                                      },
                                      {
                                        "type": "span",
                                        "text": "",
                                        "children": []
                                      },
                                      {
                                        "type": "span",
                                        "text": ":bonus",
                                        "children": [
                                          {
                                            "type": "span",
                                            "text": "",
                                            "children": [
                                              {
                                                "type": "span",
                                                "text": "",
                                                "children": []
                                              },
                                              {
                                                "type": "span",
                                                "text": "",
                                                "children": []
                                              }
                                            ]
                                          }
                                        ]
                                      },
                                      {
                                        "type": "span",
                                        "text": "",
                                        "children": []
                                      }
                                    ]
                                  },
                                  {
                                    "type": "ChipFrame",
      
```

### `FastModeButton`（conversation.input.right）

- 文案：`button`、`0 0 24 24`、` fill=`

```json
[
  {
    "type": "string",
    "text": "",
    "children": [
      {
        "type": "unknown",
        "text": "",
        "children": [
          {
            "type": "typeof",
            "text": "",
            "children": [
              {
                "type": "VARIANT_READ_ATTEMPTS",
                "text": "",
                "children": [
                  {
                    "type": "VARIANT_READ_ATTEMPTS",
                    "text": "",
                    "children": [
                      {
                        "type": "style",
                        "text": "",
                        "children": []
                      },
                      {
                        "type": "button",
                        "text": "button",
                        "children": [
                          {
                            "type": "svg",
                            "text": "0 0 24 24",
                            "children": [
                              {
                                "type": "path",
                                "text": " fill=",
                                "children": []
                              },
                              {
                                "type": "span",
                                "text": "",
                                "children": []
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
]
```

### `ProviderCardExtras`（settings.models.provider-card）

- 文案：` dsh-subscription-models-card-dot-live`、`true`、`button`

```json
[
  {
    "type": "string",
    "text": "",
    "children": [
      {
        "type": "SubscriptionStatus",
        "text": "",
        "children": [
          {
            "type": "readonly",
            "text": "",
            "children": [
              {
                "type": "void",
                "text": "",
                "children": [
                  {
                    "type": "void",
                    "text": "",
                    "children": [
                      {
                        "type": "div",
                        "text": "",
                        "children": [
                          {
                            "type": "style",
                            "text": "",
                            "children": []
                          },
                          {
                            "type": "div",
                            "text": "",
                            "children": [
                              {
                                "type": "span",
                                "text": " dsh-subscription-models-card-dot-live",
                                "children": [
                                  {
                                    "type": "span",
                                    "text": "",
                                    "children": []
                                  },
                                  {
                                    "type": "span",
                                    "text": "",
                                    "children": []
                                  }
                                ]
                              },
                              {
                                "type": "div",
                                "text": "",
                                "children": [
                                  {
                                    "type": "div",
                                    "text": "",
                                    "children": [
                                      {
                                        "type": "span",
                                        "text": "",
                                        "children": [
                                          {
                                            "type": "span",
                                            "text": "",
                                            "children": []
                                          },
                                          {
                                            "type": "span",
                                            "text": "",
                                            "children": []
                                          }
                                        ]
                                      },
                                      {
                                        "type": "span",
                                        "text": "true",
                                        "children": [
                                          {
                                            "type": "span",
                                            "text": "",
                                            "children": []
                                          }
                                        ]
                                      }
                                    ]
                                  },
                                  {
                                    "type": "div",
                                    "text": "",
                                    "children": [
                                      {
                                        "type": "button",
                                        "text": "button",
                                        "children": []
                                      },
                                      {
                                        "type": "button",
                                        "text": "button",
                                        "children": []
                                      }
                                    ]
                                  }
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
]
```

### `SubscriptionsSection`（settings.section）

- 文案：`button`、`true`、`none`、`checkbox`、`accountNeedsLoginHint`、`text`、`submit`、`inset 0 2px 0 0 var(--dsw-alias-brand-primary)`、`pointer`

```json
[
  {
    "type": "string",
    "text": "",
    "children": [
      {
        "type": "SubscriptionsSectionInjected",
        "text": "",
        "children": [
          {
            "type": "string",
            "text": "",
            "children": [
              {
                "type": "string",
                "text": "",
                "children": [
                  {
                    "type": "string",
                    "text": "",
                    "children": [
                      {
                        "type": "div",
                        "text": "",
                        "children": [
                          {
                            "type": "div",
                            "text": "",
                            "children": [
                              {
                                "type": "span",
                                "text": "",
                                "children": []
                              },
                              {
                                "type": "span",
                                "text": "",
                                "children": [
                                  {
                                    "type": "button",
                                    "text": "button",
                                    "children": []
                                  },
                                  {
                                    "type": "button",
                                    "text": "button",
                                    "children": []
                                  }
                                ]
                              }
                            ]
                          },
                          {
                            "type": "div",
                            "text": "",
                            "children": [
                              {
                                "type": "div",
                                "text": "",
                                "children": [
                                  {
                                    "type": "span",
                                    "text": "",
                                    "children": []
                                  },
                                  {
                                    "type": "span",
                                    "text": "",
                                    "children": []
                                  }
                                ]
                              },
                              {
                                "type": "div",
                                "text": "",
                                "children": [
                                  {
                                    "type": "span",
                                    "text": "",
                                    "children": []
                                  }
                                ]
                              },
                              {
                                "type": "p",
                                "text": "",
                                "children": []
                              }
                            ]
                          },
                          {
                            "type": "div",
                            "text": "",
                            "children": [
                              {
                                "type": "div",
                                "text": "",
                                "children": [
                                  {
                                    "type": "span",
                                    "text": "",
                                    "children": []
                                  },
                                  {
                                    "type": "button",
                                    "text": "button",
                                    "children": []
                                  }
                                ]
                              },
                              {
                                "type": "p",
                                "text": "",
                                "children": []
                              },
                              {
                                "type": "p",
                                "text": "",
                                "children": []
                              },
                              {
                                "type": "div",
                                "text": "",
                                "children": [
                                  {
                                    "type": "div",
                                    "text": "",
                                    "children": [
                                      {
                                        "type": "span",
                                        "text": "",
                                        "children": []
                                      },
                                      {
                                        "type": "span",
                                        "text": "",
                                        "children": []
                                      }
                                    ]
                                  },
                                  {
                                    "type": "div",
                                    "text": "",
                                    "children": [
                                      {
                                        "type": "div",
                                        "text": "",
                                        "children": [
                                          {
                                            "type": "span",
                                            "text": "",
  
```

## RPC / 数据

| 端点/方法 |
| --- |
| `1.2` |
| `addModel` |
| `auto-state` |
| `cancel` |
| `checkin` |
| `conversation.composer.dock` |
| `conversation.input.right` |
| `discover` |
| `login` |
| `logout` |
| `model-variants` |
| `refresh` |
| `reload` |
| `removeModel` |
| `setAutoOn` |
| `setDefault` |
| `setModelOrder` |
| `setModelVisible` |
| `settings.models.provider-card` |
| `settings.section` |
| `settings.subscriptions` |
| `status` |
| `tasks` |
| `usage` |

## 需要处理

- 注册了未知/非官方 slot: settings.models.provider-card (E:/work/deepseek mobile/.captures/sub-pkg/dsh-plugin-subscription-oauth/lib/../src/client/index.ts)
- slot 没有 NativeSlot 映射: settings.models.provider-card (ProviderCardExtras)
