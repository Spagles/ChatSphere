# ChatSphere

---

## Introduction

**ChatSphere** is a mod that completely overhauls Minecraft's vanilla chat system, delivering a modern, instant-messaging-style experience. Create custom channels, message players privately, execute commands through a graphical interface, and stay notified with sounds, icon flashes, and pop-ups when new messages arrive—all in a clean, organized layout.

---

## Feature Overview

| Feature | Description |
|---------|-------------|
| **Channel System** | Create public or private channels with customizable names, descriptions, admins, and mute lists |
| **Private Messaging** | Click any player name in the right sidebar to start a private chat, with `/msg` and `/tell` support |
| **Command Console** | Type commands starting with `/` directly in the chat interface; outputs appear in a dedicated console session |
| **Graphical Settings** | Press `F7` to open the settings menu—toggle timestamps, sender names, avatars, and notification styles |

---

## Key

| Key | Action |
|-----|--------|
| `T` | Opens the group chat interface (fully replaces vanilla chat) |
| `/` | Opens the chat interface and switches to command input mode automatically |
| `F7` | Opens the settings menu |

---

## Channel System

### Creating a Channel

- Click the **`+`** button next to the **"Channels"** header in the left sidebar, then enter a channel name
- Or type `#channelname` in the input box and press Enter to create and switch to that channel instantly

### Joining a Channel

- Click the **`=`** button next to the **"Channels"** header, then enter the **invite code** to join

### Channel Management

Click the **gear icon ⚙** next to a channel in the sidebar (visible only to the owner) to:

- Toggle **Public/Private** status
- Set a **Display Name** and **Description**
- **Regenerate invite code**
- **Delete the channel** (owner only)

### Switching Channels

- Click any channel name in the left sidebar
- Or type `#channelname` in the input box and press Enter

### Command List

Type `/chatsphere help` in-game to see all available commands:

| Command | Description |
|---------|-------------|
| `/chatsphere help` | Displays the help menu |
| `/chatsphere list` | Lists all available channels |
| `/chatsphere info <name>` | Shows detailed information about a specific channel |

---

## Private Messaging

- Click any **player name** in the right sidebar (online member list) to start a private conversation instantly
- Use `/msg <player> <message>` or `/tell <player> <message>` to automatically create a private chat session
- All private conversations are grouped under the **"Private"** section in the left sidebar for quick access

---

##6. Command Console

- Click **"Commands" → "Console"** in the left sidebar to enter command mode
- Type commands starting with `/` (the `/` is optional) and view execution results directly in the console session
- Recent commands are saved—use the **Up/Down arrow keys** to quickly recall and re-run them

---

## Settings Menu (F7)

Press `F7` to open the settings menu and customize your experience:

| Option | Description |
|--------|-------------|
| **Show Timestamp** | Display message timestamps next to chat bubbles |
| **Show Sender Name** | Display who sent each message |
| **Show Avatar** | Display player skin avatars next to messages |
| **Enable Channels** | Disable to revert to vanilla chat behavior |
| **New Message Sound** | Play a notification sound when new messages arrive |
| **Icon Flash** | Flash the chat icon when new messages arrive |
| **Screen Pop-up** | Show on-screen pop-ups for new messages |
| **Show Right Sidebar** | Show/hide the online member list in channels |

---

## Important Notes

- This mod requires **installation on both the server and client** for full channel synchronization and messaging functionality
- If the server does not have the mod installed, the client will automatically fall back to **local storage mode**—channels and messages are saved locally only
- **Client Data**: In local mode, data is stored in `.minecraft/config/chatsphere_data/`
- **Server Data**: Data is stored in the world save's `data/` directory, with automatic backups every **30 minutes** (up to **20** backups retained)
- **Message Limits**: Client chat history is capped at **100** messages; server history is capped at **200**