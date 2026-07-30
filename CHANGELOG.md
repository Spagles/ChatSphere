## 2.1.0

### Added
- Command console message persistence — `ServerboundCommandMessagePayload` sends command input/output to server, stored in `messages.json`, restored on reconnect via `sendMessagesToPlayer()` / `applyServerMessages()`
- Color/formatting preservation across reconnect — Components serialized as JSON via `Component.Serializer.toJson(component, RegistryAccess.EMPTY)`, restored via `Component.Serializer.fromJson(json, RegistryAccess.EMPTY)`
- Multi-line command output — server no longer splits by `\n`; client renders each line separately with proper `CommandHit` registration (click/hover events work per-line)
- System message grouping — 150ms buffer in `ModClientEvents` coalesces consecutive system packets (e.g. `/help`) into a single multi-line entry
- HUD unread count badge — red badge on chat icon (top-right) showing total unread across all conversations, with flashing animation
- Configurable badge toggle — `notificationBadge` (default on) in settings → Sound section
- PlasmoVoice room localization keys — `pv.activation.chatsphere_room` / `pv.source_line.chatsphere_room`
- Voice message offline delivery — undelivered voice persisted to `voice_undelivered/index.json`, delivered on `PlayerLoggedInEvent`
- Local voice message cache — received voice cached to `voice_cache/index.json` for replay after restart
- Voice message animation fix — `voicePlayerCache` reuses `PlaybackPlayer` across frames, `put` hook registers Playback under `voiceMessageId`
- `ChatComponentMixin` — chat component mixin for enhanced interactions
- `ChatDataStore.commandMessages` / `blockedPlayers` storage fields

### Changed
- System messages use event sender UUID (`Util.NIL_UUID`) instead of `localUuid` for server sync — fixes green `"> "` prefix on system output
- `sendMessagesToPlayer` now allows `NIL_UUID` command messages to sync to all players
- `addChatMessage` skips `learnPlayerName` for `NIL_UUID` to avoid junk entries
- `addCommandMessage` no longer splits content by newlines — multi-line output stored as single entry
- HUD overlay command rendering uses `guiGraphics.enableScissor()` to clip text within bubble bounds
- HUD badge rendered inside `drawIcon()` after icon blit, ensuring it appears on top
- Chat icon unread badge reduced in size (padding +3, height 8, tighter positioning)
- Banned words editor width clamped to screen bounds (`Math.min(btnW * 3, width - inputX - 10)`)
- Voice messages routed through ChatSphere custom packets — VM used as recording/playback library only
- `voiceMessageId` (UUID) propagated through `ServerboundVoicePacket` → `ClientboundVoicePacket` → `ModVoiceStorage`
- Version bumped to 2.0.3

### Fixed
- Command console messages all showing green `"> "` prefix after server sync (wrong `isOwn` due to `localUuid` being used for system output)
- Color codes lost on reconnect — Component now serialized/deserialized as JSON through the server persistence pipeline
- Multi-line command output shown as separate bubble entries instead of one multi-line message
- Right-side blank space in multi-line bubbles (width calculation no longer sums all line widths via `font.width(contentText)`)
- Text overflow outside bubble in narrow windows (scissor clip applied)
- Unread badge drawn below chat icon instead of on top (fixed render order)
- Missing PV addon localization causing key display in voice room UI
- Banned words input extending past right screen edge
- ServerboundConfigUpdatePayload registration accidentally dropped during development

## 2.0.2

### Added
- Item NBT sharing — pick an item from inventory via the new item picker panel (item_chest icon); item NBT serialized and sent with chat messages; displayed as item icon + name in chat bubbles (ModChatScreen) and HUD overlay (ChatHudOverlay)
- ItemSerialization utility for NBT-based item serialization/deserialization
- ItemPickerPanel widget for inventory item selection

### Changed
- ChatMessageData, StoredMessage records now carry an optional `itemNbt` field through the entire pipeline: client send → server channel action → bridge relay → message sync → client display
- Duplicate message detection now compares `itemNbt` to avoid merging different items
- HUD bubble rendering adapts height and layout when an item is present
- `addMessage()` skips reply bar rendering when `replyContent`/`replySender` is empty
- ModChatScreen preserves `currentConversation` for PRIVATE conversations on window resize
- Input placeholder uses dynamic `[slotNumber]` format when an item is selected
- Version bumped to 2.0.2

### Fixed
- Empty replyContent no longer creates a visible reply bar in chat bubbles (fix applied during 2.0.2 development)
- Window resize no longer resets current conversation from PRIVATE to default channel (fix applied during 2.0.2 development)
- Sender now sees their own item-share message immediately (pendingItemNbt captured before sendChannelChatPacket — fix applied during 2.0.2 development)
- Missing `bridge_info` payload registration causing handshake failure on proxy setups (fix applied during 2.0.2 development)
- ServerboundConfigUpdatePayload registration accidentally dropped during development, causing ClassCastException on config save (fix applied during 2.0.2 development)

### Notes
All fixes listed above were identified and resolved during the 2.0.2 development cycle, not inherited from 2.0.1.

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
