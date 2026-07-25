![ChatSphere](https://cdn.modrinth.com/data/cached_images/c3c31427b47c241789f88284f35c5946f68c02d1_0.webp)

# ChatSphere

**A modern instant-messaging chat mod for Minecraft NeoForge 1.21.1.** Replaces vanilla chat with channels, private messaging, emoji, voice rooms, and a full GUI.

[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.228-black?style=flat-square)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-success?style=flat-square)](https://minecraft.net)
[![License](https://img.shields.io/badge/License-LGPLv3-blue?style=flat-square)](LICENSE)

Works on the client side — in singleplayer or on servers without the mod, it runs in local storage mode. On modded servers, install on both sides for channel sync, private messaging, and voice chat.

---

## Features

**Channels** — Create public or private channels with invite codes, display names, descriptions, and explore visibility. Browse all public channels from the Explore screen.

**Private Messaging** — Click any player name in the member list to start a DM. `/msg` and `/tell` are also supported.

**Command Console** — Switch to console mode to run commands directly in the chat interface. Output appears in-session. Arrow keys recall history.

**Emoji Picker** — 349 emoji from twemoji with category tabs, search, and `:shortcode:` autocomplete.

**Voice Chat** — Dual integration with Simple Voice Chat (isolated groups) and PlasmoVoice (broadcast lines). Works with either or both; auto-detected at runtime.

**Chat Search** — Filter messages in the current conversation. Match count and jump navigation included.

**Quote Reply** — Right-click any message to copy or quote-reply with context.

**Anti-Spam** — Duplicate messages collapsed automatically. Configurable server-side.

**Member Management** — Per-channel admins, mute/unmute, kick, invite system, and ownership transfer.

**Full Settings GUI** — Dark theme, bubble color and corner radius, per-type notification sounds, icon flash, screen popups, avatar cache, and more.

**Server Configuration** — In-game config screen for operators. Also available as `serverconfig/chatsphere-server.toml`.

**No Chat Reports Compat** — NCR security status displayed directly in the UI.

---

## Controls

| Key | Action |
|-----|--------|
| `T` | Open chat GUI |
| `/` | Open chat in command mode |
| `F7` | Open settings |

---

## Configuration

### Client — press `F7`

| Tab | Options |
|-----|---------|
| UI | Timestamps, sender name, avatar, dark theme, preserve input, right sidebar, channel toggle |
| Bubbles | Own/other color (ARGB), corner radius |
| Skin | Avatar cache toggle, custom API URL |
| NCR | NCR compatibility toggle, safety status display, preventsChatReports setting (when No Chat Reports is installed) |
| Behavior | Chat history limit, scroll limit |
| Sound | Master toggle, @mention / whisper / system / public sounds, icon flash, screen popup |

### Server — `config/chatsphere-server.toml`

| Option | Default | Description |
|--------|---------|-------------|
| Anti-Spam | `true` | Collapse duplicate messages |
| Max Chat History | `200` | Messages stored per conversation |
| Enable Channels | `true` | Toggle the channel system |
| Explore Enabled | `true` | Public channel discovery |
| Explore Min Members | `2` | Min members to appear in explore |
| Backup Interval | `30` min | Server data backup frequency |
| Prevents Chat Reports | `true` | Advertise `preventsChatReports` in server status (NCR section in client UI) |

---

## Storage

```
ChatSphere/
├── client/singleplayer/<world>/
├── client/multiplayer/<server>/
└── server/
    ├── channels.json
    └── backups/
```

Server data auto-backs up every 30 minutes (configurable).

---

## FAQ

**Does the mod need to be on the server?** It works on the client alone in local mode. Install on both sides to unlock channel sync, private messaging across players, and voice chat.

**Which voice chat mods are supported?** Simple Voice Chat and PlasmoVoice. Both work simultaneously if installed.

**How do I join a channel?** Use the `→` button and enter an invite code, or click the search icon (left of `→`) to browse public channels from the Explore screen.

**How do I create a channel?** Click `+` next to the Channels header, or type `#name` in the input and press Enter.

**Where is my data stored?** Client data in `ChatSphere/client/` per world/server. Server data in `ChatSphere/server/`.

**Can I use this in a modpack?** Yes.

---

## Commands

| Command | Description |
|---------|-------------|
| `/chatsphere help` | Show help |
| `/chatsphere list` | List all channels |
| `/chatsphere info <name>` | Channel details |

---

## License

**GNU LGPLv3** — Free to use, modify, and distribute.

Emoji by [twemoji](https://twemoji.twitter.com/) (Twitter / CC-BY 4.0).
