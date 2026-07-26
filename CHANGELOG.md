## 2.0.1

### Added
- Right-click context menu: Block Messages from a player
- BlockListScreen — manage blocked players (click to unblock), blur background
- Online status indicator — green/gray dot in private chat header, sidebar, and member list
- Banned words library — server-side regex filtering in SEND_CHAT handler
- Skin cache refresh button in settings UI
- Context menu type system (CTX_BUBBLE) for future extensibility
- Block icon SVG resource

### Changed
- Context menu: now only activates on bubble hit area, third option "Block Messages" added
- PlayerSkinCache: network I/O moved to background thread; `pendingFetches` removal after successful cache
- `drawContextMenu`/`handleContextMenuClick` use `contextType` instead of raw `contextMsgIndex`
- Skin config screen: "Refresh Skin Cache" button added
- Duplicate message "xN" label no longer shifts own message bubble position
- Translation: blocklist empty state text corrected

### Fixed
- Channel config not persisting after `applyServerChannels()` — missing `loaded = true`
- Reply/quote data not transmitted over network — missing fields in 3 payload classes
- "N new messages" bar scrolled to top instead of bottom — reversed scroll direction
- Skin race condition causing duplicate fetches — `pendingFetches.remove()` before cache populated
- Server crash on startup — `ModNetworkSetup` reading client config on server side
- Duplicate-detected own message bubbles shifted left — `dupW` wrongly subtracted from `bubbleX`

---

## 2.0.0

### Added
- Full IM-style chat GUI with left sidebar (channels/DMs) and right sidebar (online members)
- Emoji picker — 349 twemoji, category tabs, search, `:shortcode:` autocomplete
- Voice chat — Simple Voice Chat and PlasmoVoice integration
- Channel explore/discovery screen
- Chat search, quote reply, right-click context menu
- Member management: admins, mute, invite, transfer ownership
- Server configuration GUI
- Quick phrases panel, @mention autocomplete
- NCR compatibility settings tab

### Changed
- Complete UI rewrite — tabbed settings, bubble customization, per-type sound toggles
- Network protocol updated (requires matching server version)

### Fixed
- Various chat history and synchronization issues
