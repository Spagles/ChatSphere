![Views](https://komarev.com/ghpvc/?username=mcraftbbs&repo=ChatSphere&label=Views&color=brightgreen)

# ChatSphere

![ChatSphere](https://cdn.modrinth.com/data/cached_images/8cc6c14cc43b82f8053acb11d80eed267154bdab_0.webp)

一款现代化的即时通讯风格 Minecraft NeoForge (1.21.1) 聊天模组。彻底取代原版聊天，提供频道、私信、语音房间、表情和完整图形界面。

> **许可协议:** GNU LGPLv3
> **Mod ID:** `chatsphere`
> **版本:** 2.0.0

---

## 目录

- [功能总览](#功能总览)
- [安装](#安装)
- [快捷键](#快捷键)
- [聊天界面概览](#聊天界面概览)
- [频道系统](#频道系统)
- [私信](#私信)
- [命令控制台](#命令控制台)
- [表情系统](#表情系统)
- [语音聊天集成](#语音聊天集成)
- [客户端设置](#客户端设置)
- [服务端配置](#服务端配置)
- [命令列表](#命令列表)
- [数据存储](#数据存储)
- [兼容性](#兼容性)
- [源码构建](#源码构建)
- [网络协议](#网络协议)

---

## 功能总览

| 类别 | 详情 |
|------|------|
| **全 GUI 聊天** | 完全取代原版聊天，IM 风格界面 — 左侧边栏（频道/私信）、右侧边栏（在线成员）、可滚动消息区域、输入栏 |
| **频道系统** | 公开/私人频道，支持邀请码、可配置名称、描述和探索可见性 |
| **私信** | 点击玩家名称即可私聊；支持 `/msg` 和 `/tell`；侧边栏分组展示 |
| **命令控制台** | 内置命令控制台，支持历史记录上下翻查 |
| **表情选择器** | 349 个 twemoji 表情，通过自定义字体（PUA 字形）渲染；分类标签页、搜索、`:shortcode:` 自动补全 |
| **语音聊天** | 双集成 — 同时支持 **Simple Voice Chat**（隔离群组）和 **PlasmoVoice**（广播源线路） |
| **聊天搜索** | 在当前会话中搜索消息，显示匹配数并支持跳转导航 |
| **引用回复** | 右键引用回复任意消息；输入栏显示引用预览 |
| **右键菜单** | 右键消息 → 复制或引用回复 |
| **提及系统** | `@用户名` 自动补全弹窗，过滤在线玩家 |
| **快捷短语** | 用户自定义消息快捷方式，支持添加/删除 |
| **反垃圾** | 自动折叠重复消息（服务端可开关） |
| **聊天气泡 (HUD)** | 头顶叠加层显示最近消息，含头像、名称、时间戳，支持自定义颜色和圆角 |
| **消息历史** | 每个会话持久化存储（本地 100 条，服务端 200 条）；服务端每 30 分钟自动备份 |
| **频道探索** | 浏览公开频道，显示成员数和在线数 |
| **No Chat Reports 兼容** | 在 UI 中显示 NCR 安全状态 |
| **深色主题** | 完整深色主题，支持自定义气泡颜色 |
| **通知** | 按类型独立开关音效（提及、私信、系统、公开）、图标闪烁、屏幕弹窗 |
| **Mixin: 原版聊天拦截** | 完全禁用原版聊天渲染 — 所有消息通过 ChatSphere 显示 |

---

## 安装

1. 安装 **NeoForge 21.1.228**（Minecraft 1.21.1）
2. 将 ChatSphere `.jar` 放入客户端和服务端的 `mods/` 文件夹
3. （可选）安装 **Simple Voice Chat** 和/或 **PlasmoVoice** 以使用语音房间功能

模组需要在**服务端和客户端同时安装**才能使用完整功能。如果服务端未安装，客户端会自动回退到本地存储模式。

---

## 快捷键

| 按键 | 默认 | 功能 |
|------|------|------|
| `T` | `T` | 打开主聊天界面 |
| `/` | `/` | 以命令模式打开聊天 |
| `F7` | `F7` | 打开客户端设置菜单 |

---

## 聊天界面概览

主聊天界面 (`ModChatScreen`) 分为以下几个区域：

- **左侧边栏** — 会话列表：频道按分类排列，私信归入 "Private" 分组；显示头像、名称、未读提及数；点击切换会话；顶部有探索（搜索图标）、邀请码加入 (`→`)、创建 (`+`) 按钮
- **消息区域** — 可滚动历史消息，含时间戳、发送者头像/名称、消息内容（表情已渲染）、重复计数徽章、时间分隔线
- **右侧边栏** — 当前频道在线成员列表；显示皮肤头像 + 名称；包含语音房间加入/离开按钮
- **输入栏** — 文本框 + 表情按钮 + 快捷短语开关 + 回复预览；支持 `:shortcode:` 和 `@提及` 自动补全
- **搜索栏** — 按关键词过滤消息；上下箭头跳转匹配项

---

## 频道系统

### 创建频道
- 点击 "Channels" 标题旁的 `+` 按钮，或输入 `#名称` 后回车
- 创建时可设置显示名称、描述和公开/私人状态

### 加入频道
- 点击 `→` 按钮并输入邀请码
- 点击搜索图标通过 **探索** 界面浏览公开频道

### 频道管理（齿轮图标 ⚙）
- **常规标签页：** 显示名称、描述、公开/私人切换、在探索中显示切换、邀请码（重新生成）
- **成员标签页：** 成员列表（含管理员徽章、在线指示器）；提升/降级管理员、禁言/解除禁言、踢出、转让所有权
- **语音标签页：** 创建/删除语音房间；按房间加入/离开
- **删除标签页：** 确认删除（所有者）或离开频道（非所有者）
- **邀请玩家：** 可搜索玩家列表；发送/撤销邀请

### 探索界面
浏览全服公开频道：名称、描述、成员数、在线数、加入按钮。服主可设置探索最小成员数。

---

## 私信

- 点击右侧边栏任意玩家名称 → 打开私聊会话
- `/msg <玩家> <消息>` 或 `/tell <玩家> <消息>` → 自动创建私聊
- 所有私聊归入左侧边栏 "Private" 分组，方便快速访问
- 私聊记录在重连后仍保留

---

## 命令控制台

- 点击左侧边栏 **"Commands" → "Console"**
- 输入命令时可带或不带前导 `/`
- 执行结果直接显示在控制台会话中
- 上下方向键可回查最近命令

---

## 表情系统

ChatSphere 内置 **349 个表情**（共 351 个，隐藏 2 个），来自 [twemoji](https://twemoji.twitter.com/)。

- **选择器：** 点击输入栏的表情按钮 → 分类标签页（笑脸、人物、动物、食物、旅行、活动、物品、符号、旗帜）+ 搜索框 → 点击插入
- **短代码：** 输入 `:smile:` → 自动补全弹窗（最多 12 个候选，键盘可导航）→ 回车或点击插入
- **渲染：** 通过自定义位图字体（PUA 码位）渲染；在每条消息显示时动态解析
- **字体表：** 由 `EmojiSheetGenerator` 构建工具生成（16×16 网格精灵表）

---

## 语音聊天集成

ChatSphere 同时支持**两种**语音聊天模组，无需额外配置 — 自动检测已安装的模组。

### Simple Voice Chat (SVC)

| 特性 | 支持情况 |
|------|:-------:|
| 插件入口点 | `ChatSphereSvcPlugin`（`@ForgeVoicechatPlugin`） |
| 群组类型 | `ISOLATED` — 每个频道独立语音群组 |
| 加入/离开 | `VoiceIntegration.joinSvcGroup()` / `leaveSvcGroup()` 反射调用 |
| API 版本 | `voicechat-api:2.1.12` |

- 每个频道房间对应一个隔离的 SVC 群组，绑定频道 ID
- 玩家加入/离开语音房间时自动移入/移出群组
- 除 SVC 本体外无需服务端附加组件

### PlasmoVoice (PV)

| 特性 | 支持情况 |
|------|:-------:|
| 附加组件入口点 | `PlasmoRoomAddon`（`@Addon`、`AddonInitializer`） |
| 音频路由 | 自定义 `ServerActivation` + `ServerSourceLine` → 每玩家 `ServerBroadcastSource` |
| 加入/离开 | 通过 `VoiceIntegration.joinPlasmoBroadcast()` 反射调用 |
| API 版本 | `server:2.1.13` |

- 创建 `chatsphere_room` 激活和源线路
- 语音房间中的每个玩家获得一个广播源，仅过滤到其他房间成员
- 房间成员记录在 `ConcurrentHashMap` 中；房间为空时自动清理

### 双模组检测

`VoiceIntegration` 在启动时同时检测两个模组：

```java
svcAvailable = ModList.get().isLoaded("voicechat");
plasmoAvailable = ModList.get().isLoaded("plasmovoice");
```

玩家加入/离开语音房间时，**两个**集成都会激活（如果相应模组已安装）。

---

## 客户端设置

按 `F7` 或通过 **Mod Menu** 打开客户端设置。

### 常规 (UI)
| 选项 | 默认值 | 描述 |
|------|--------|------|
| 显示时间戳 | `true` | 消息旁显示时间 |
| 显示发送者名称 | `true` | 消息上方显示发送者 |
| 显示头像 | `true` | 侧边栏显示皮肤头像 |
| 深色主题 | `true` | 深色模式界面 |
| 显示强提示 | `true` | 增强 UI 提示 |
| 保留输入 | `true` | 关闭聊天后保留已输入文字 |
| 显示右侧边栏 | — | 切换在线成员列表 |
| 启用频道 | — | 关闭后恢复原版聊天 |

### 气泡
| 选项 | 默认值 | 描述 |
|------|--------|------|
| 自己的气泡颜色 | `0x80000000` | 自己消息的背景色 (ARGB) |
| 他人气泡颜色 | `0x80404040` | 他人消息的背景色 (ARGB) |
| 圆角半径 | `8` | 气泡圆角像素值 |

### 行为
| 选项 | 默认值 | 描述 |
|------|--------|------|
| 反垃圾 | `true`（服务端） | 折叠重复消息 |
| 最大聊天历史 | `200`（服务端） | 每个会话存储的消息数 |
| 滚动历史限制 | `200` | 最大可滚动历史行数 |

### 声音设置
| 选项 | 默认值 | 描述 |
|------|--------|------|
| 启用音效 | `true` | 总开关 |
| @提及音效 | `true` | 被提及时播放 |
| 私信音效 | `true` | 收到私信时播放 |
| 系统消息音效 | `true` | 系统通知时播放 |
| 公开聊天音效 | `false` | 公开频道消息时播放 |
| 图标闪烁 | `true` | 新消息时闪烁图标 |
| 屏幕弹窗 | `true` | 新消息时弹窗 |

### 头像
| 选项 | 默认值 | 描述 |
|------|--------|------|
| 头像缓存 | `true` | 缓存皮肤到磁盘 |
| 自定义皮肤 API | Mojang API | 覆盖皮肤解析地址 |

### NCR（No Chat Reports）
| 选项 | 默认值 | 描述 |
|------|--------|------|
| NCR 兼容 | `true` | 启用 NCR 兼容功能（安装 NCR 后显示） |
| NCR 安全状态 | — | 显示当前 NCR 安全等级（SECURE / INSECURE / SINGLEPLAYER / UNKNOWN） |
| 阻止聊天举报 | `true` | 在服务端状态中声明 `preventsChatReports`（服务端配置，可从客户端 UI 切换） |

---

## 服务端配置

服主可通过游戏内 **服务端配置** 界面或 `config/chatsphere-server.toml` 配置。

| 选项 | 默认值 | 描述 |
|------|--------|------|
| 反垃圾 | `true` | 自动折叠重复消息 |
| 启用频道 | `true` | 启用频道系统 |
| 最大聊天历史 | `200` | 每会话最大消息数 |
| 同步默认频道 | `true` | 新玩家自动加入 `#general` |
| 启用频道历史 | `true` | 持久化消息历史 |
| 启用探索 | `true` | 启用公开频道发现 |
| 探索最小成员 | `2` | 出现在探索中的最小成员数 |
| 备份间隔 | `30` 分钟 | 频道数据备份频率 |
| 备份保留数 | `10` | 保留的最大备份文件数 |
| 显示强提示 | `true` | 加密状态提示 |
| 阻止聊天举报 | `true` | 在服务端状态中声明 `preventsChatReports`（客户端 UI 的 NCR 分类中也可设置） |

---

## 命令列表

| 命令 | 描述 |
|------|------|
| `/chatsphere help` | 显示帮助菜单 |
| `/chatsphere list` | 列出所有可用频道 |
| `/chatsphere info <名称>` | 显示频道详细信息 |
| `#频道名` | 快速切换到频道（在输入框中输入） |

---

## 数据存储

```
{gamedir}/ChatSphere/
├── client/
│   ├── singleplayer/<世界名>/
│   │   ├── chatsphere_data.json      # 频道、邀请、禁言列表、语音房间
│   │   └── (头像缓存)
│   └── multiplayer/<服务器IP>/
│       ├── chatsphere_data.json      # 同上结构
│       └── (头像缓存)
└── server/
    ├── channels.json                 # 所有频道数据（服务端权威）
    └── backups/
        └── channels_<时间戳>.json     # 定期备份
```

客户端数据在变更时异步保存。服务端数据按配置时间间隔自动备份（默认 30 分钟，最多保留 10 份）。

---

## 兼容性

| 模组 | 状态 | 详情 |
|------|:----:|------|
| **Mod Menu** | ✅ | 注册了 `IConfigScreenFactory` 用于客户端设置 |
| **Simple Voice Chat** | ✅ | 自动检测；为每个频道创建隔离语音群组 |
| **PlasmoVoice** | ✅ | 自动检测；通过广播源线路实现语音房间 |
| **No Chat Reports** | ✅ | 显示 NCR 安全状态颜色指示器 |
| **原版聊天** | 🔄 已替换 | `ChatComponentMixin` 取消所有原版聊天显示 |
| **服务端状态** | 🔄 已修补 | `ServerStatusSerializerMixin` 注入 `preventsChatReports:true` |

### Mixin

| 目标 | 用途 |
|------|------|
| `ChatComponent.addMessage(Component)` | 取消原版聊天渲染；消息路由到 ChatSphere 历史 |
| `ServerStatus.getInstance()` | 在服务端状态 JSON 中注入 `preventsChatReports: true` |

---

## 源码构建

```bash
./gradlew build
```

生成表情精灵表（需联网）：
```bash
./gradlew runEmojiSheetGenerator
```
从 CDN 下载 twemoji 资源，生成 `emoji.png` + `emoji.json` 供自定义字体使用。

---

## 网络协议

版本: `"1.0"`。所有数据包使用 NeoForge 自定义数据包 API (`CustomPacketPayload`)。

| 数据包 | ID | 方向 | 用途 |
|--------|----|:----:|------|
| `ClientboundChatPayload` | `chatsphere:chat` | S→C | 转发新聊天消息 |
| `ClientboundChannelSyncPayload` | `chatsphere:channel_sync` | S→C | 完整频道列表 + 玩家名称映射 |
| `ClientboundMessageSyncPayload` | `chatsphere:message_sync` | S→C | 登录时回放消息历史 |
| `ClientboundPublicChannelListPayload` | `chatsphere:public_channel_list` | S→C | 可发现的公开频道（探索界面） |
| `ClientboundPermissionResponsePayload` | `chatsphere:perm_response` | S→C | 布尔权限检查响应 |
| `ServerboundChannelActionPayload` | `chatsphere:channel_action` | C→S | 频道 CRUD（15 种操作类型） |
| `ServerboundPermissionCheckPayload` | `chatsphere:perm_check` | C→S | 权限检查请求（OP 等级 2） |
| `ServerboundConfigUpdatePayload` | `chatsphere:config_update` | C→S | 运行时服务端配置更新（仅 OP） |

### 频道动作类型

`ServerboundChannelActionPayload.Action` 枚举：

`CREATE`（创建）, `UPDATE_CONFIG`（更新配置）, `JOIN_MEMBER`（加入）, `JOIN_BY_CODE`（通过邀请码加入）, `SEND_CHAT`（发送消息）, `REMOVE_CHANNEL`（删除频道）, `TOGGLE_MUTE`（切换禁言）, `TOGGLE_ADMIN`（切换管理员）, `TOGGLE_INVITE`（切换邀请）, `LEAVE_CHANNEL`（离开频道）, `LIST_PUBLIC`（列出公开频道）, `CREATE_VOICE_ROOM`（创建语音房间）, `DELETE_VOICE_ROOM`（删除语音房间）, `JOIN_VOICE_ROOM`（加入语音房间）, `LEAVE_VOICE_ROOM`（离开语音房间）

---

## 界面参考

| 界面 | 打开方式 | 用途 |
|------|----------|------|
| **ModChatScreen** | 按 `T` 键 | 主聊天界面 |
| **ConfigScreen** | 按 `F7` 键 | 客户端设置（标签页：UI、气泡、皮肤、NCR、行为、声音） |
| **ServerConfigScreen** | 设置 → 服务端 | 服主设置 |
| **ChannelConfigScreen** | 频道齿轮图标 ⚙ | 频道设置（常规、成员、语音、删除） |
| **CreateChannelScreen** | `+` 按钮 | 创建新频道 |
| **JoinChannelScreen** | `→` 按钮 | 通过邀请码加入 |
| **ExploreServersScreen** | 搜索图标 | 浏览公开频道 |
| **InvitePlayerScreen** | 频道配置 → 邀请 | 发送/撤销玩家邀请 |
| **ChannelMemberScreen** | 频道配置 → 成员 | 管理成员（管理员、禁言、踢出、转让） |
| **ChannelInfoScreen** | 频道上下文 | 只读频道详情 |
| **ConfirmDeleteChannelScreen** | 频道配置 → 删除 | 确认删除/离开频道 |

---

## 组件参考

| 组件 | 用途 |
|------|------|
| `StyledButton` | 可配置按钮（Builder 模式）；多种样式预设（开关、普通、小型、确认/取消） |
| `ReplyBarWidget` | "正在回复 <名称>: <文本>" 输入栏上方的引用条；关闭按钮 |
| `QuickPhrasesPanel` | 用户自定义消息快捷方式；添加/编辑/删除 |
| `MentionPopup` | `@用户名` 自动补全：玩家列表、键盘导航、点击/回车插入 |
| `EmojiPanel` | 完整表情选择器：分类标签页、搜索、可滚动网格 (8×5)、点击插入 |
| `EmojiAutoComplete` | `:短代码:` 弹窗：最多 12 个候选、键盘选择 |
| `ChatSearchWidget` | 搜索栏 + 匹配数 + 上下导航 |
| `ChatContextMenu` | 右键菜单：复制、回复 |
| `CopyToast` | "已复制!" 淡出提示 |

---

## 致谢

- **作者:** xwwsdd
- **表情:** [twemoji](https://twemoji.twitter.com/) — Twitter (CC-BY 4.0)
- **许可协议:** GNU LGPLv3
