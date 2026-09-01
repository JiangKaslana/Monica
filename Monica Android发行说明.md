### Monica for Android

## 中文
### 简要
* 开发者设置“关闭密码验证”升级为全场景免验证，覆盖自动填充、Monica 键盘和危险操作（#116）。
* 大幅优化大量密码数据下的首屏加载、页面切换和列表滚动性能，感谢 @JiangKaslana 贡献 [PR #117](https://github.com/Monica-Pass/Monica/pull/117)。
* 设置页分组卡片改为轻量、独立的圆角卡片布局。
* 密码库新增 PixelPlayer 风格的常驻快速滚动条，支持按住展开、跟手拖动和当前位置字母提示，操作时自动隐藏悬浮按钮。
* Monica 键盘的验证器与卡包页面现在会在数据加载期间显示统一加载态，不再短暂误显示为空。
* 修复卡包页面拖动排序后卡片二次交换的问题，松手后顺序保持不变。
* 修复导入页面文件名显示为内部 `document:<id>` 的问题，现在优先显示文件原名。
* 卡包的全部、银行卡、证件和账单地址筛选从右上角溢出菜单移入分类菜单的快捷筛选区。

### 详细
* 开启后，进入自动填充选择器、执行自动填充、打开 Monica 键盘密码库，以及删除密码、验证器、笔记、卡片、Passkey 和分类时不再要求主密码或指纹。
* 删除和清空数据等危险操作仍保留二次确认；ZIP 导出等加密流程仍必须设置加密密码。
* 统一了危险操作确认组件，并为免验证模式增加本机 Keystore 主密钥旁路，避免只跳过界面但无法解密数据。
* [PR #117](https://github.com/Monica-Pass/Monica/pull/117)（@JiangKaslana）重构密码列表关键运行路径：首屏不再全量解密密码，只对幽灵条目冲突候选按需解析；Bitwarden 离线缓存预热和遗留修复等维护任务移出首屏关键路径，密码卡片图标 fallback 也改为按需执行。
* PR #117 同时加入不接收密码、TOTP、卡号或私钥等敏感数据的 Rust 批处理核心，用于非敏感元数据投影、搜索和去重；JNI 不可用时自动使用 Kotlin fallback，并补充 Rust Core/JNI 的测试与 CI 构建检查。
* 设置页移除分组整体外框和描边，各设置项使用独立的 `surfaceContainer` 背景和 2dp 紧凑间距；首尾外侧采用 24dp 大圆角，相邻边采用 4dp 小圆角，准确还原 PixelPlayer 的分组轮廓。
* 密码库列表右侧始终显示细滚动条；按住时手柄平滑展开，拖动会实时定位到对应字母分组并显示当前位置，同时为滚动条预留列表空间。交互期间历史、回到顶部和添加按钮会淡出，松手后恢复，避免遮挡列表与手势。
* Monica 键盘在切换、解锁恢复或重新打开验证器和卡包页面时，会优先显示与密码页一致的加载状态，真实加载完成后才显示空列表。
* 卡包列表的手动顺序此前会被条目更新时间压过，且拖动结果在数据写入期间被旧列表回写，松手后卡片会再交换一次。现在手动顺序优先生效、并以条目 ID 作为稳定兜底，写入完成前保持松手时的顺序；在筛选状态下拖动时，未显示的条目也会保留原有位置。
* Android 文档选择器现在通过 `OpenableColumns.DISPLAY_NAME` 读取用户可见的原始文件名，并在无法查询时从 URI 路径安全回退；查询放在 IO 调度器，避免导入页面卡顿。KDBX 密钥文件选择也使用同一套解析逻辑。
* 卡包的类型筛选此前只在右上角溢出菜单里，与收藏、未分类等快捷筛选分处两个入口。现在这四个类型作为快捷筛选磁贴显示在分类菜单顶部，点击后即时生效并关闭菜单；使用底部弹窗分类样式时仍保留原有的溢出菜单入口。

## English
### Summary
* The developer “Disable Password Verification” option now provides a full identity-verification bypass for Autofill, Monica Keyboard, and destructive actions (#116).
* Initial loading, page switching, and list scrolling with large vaults are substantially faster. Thanks to @JiangKaslana for [PR #117](https://github.com/Monica-Pass/Monica/pull/117).
* Settings groups now use lightweight, individually rounded cards.
* The vault now has a persistent PixelPlayer-style fast scrollbar with press expansion, direct dragging, live section-letter feedback, and automatic FAB hiding while interacting.
* The Monica Keyboard now shows a consistent loading state for authenticator and card-wallet panels instead of briefly presenting an incorrect empty state.
* Fixed card-wallet items swapping a second time after a drag; the order you release now stays put.
* Fixed imported files showing an internal `document:<id>` value instead of the original filename.
* Card-wallet All, Bank Cards, Documents, and Billing Addresses filters moved from the overflow menu into the folder menu's quick filters.

### Details
* Autofill selection and filling, the Monica Keyboard vault, and deletion of passwords, authenticators, notes, cards, passkeys, and folders no longer request a master password or biometric check while enabled.
* Destructive actions still require an explicit confirmation. Encryption passwords for ZIP exports and similar protected exports remain mandatory.
* Destructive confirmation UI is now shared, and a device-local Keystore wrapper keeps encrypted vault data available in bypass mode.
* [PR #117](https://github.com/Monica-Pass/Monica/pull/117) by @JiangKaslana refactors the vault list's critical path: initial rendering no longer decrypts every password, ghost-entry filtering decrypts only conflict candidates, non-critical Bitwarden cache warm-up and legacy repairs are deferred, and icon fallbacks are resolved only when needed.
* PR #117 also adds a Rust batch-processing core for non-sensitive metadata projection, search, and deduplication. Passwords, TOTP secrets, card numbers, and private keys never cross the JNI boundary; a Kotlin fallback remains available, with Rust Core/JNI tests and CI checks included.
* Settings groups no longer draw a shared outline or container. Items use independent `surfaceContainer` backgrounds with 2dp spacing, 24dp outer corners on the first and last cards, and 4dp adjacent corners, matching PixelPlayer's grouped silhouette.
* A slim scrollbar is always visible beside the vault list. Its handle expands smoothly while pressed, follows the finger during fast scrolling, and shows the current alphabetical section. History, back-to-top, and add buttons fade out during interaction and return on release, keeping the list and gesture path unobstructed.
* When the Monica Keyboard switches to, restores after unlock, or reopens an authenticator or card-wallet panel, it shows the same loading state as the password panel until the real result is available.
* Manual card-wallet order was previously overridden by item update time, and a drag result could be overwritten by the stale list while the new order was still being saved, causing a second swap after release. Manual order now takes precedence with item ID as a stable tiebreaker, the released order is held until the write completes, and dragging inside a filtered view keeps hidden items in their original slots.
* Android imports now query `OpenableColumns.DISPLAY_NAME` for the user-visible filename, safely fall back to a decoded URI path when providers omit it, and perform the query on the IO dispatcher. KDBX key-file selection uses the same resolver.
* Card-wallet type filters previously lived only in the overflow menu, separate from quick filters such as Favorites and Uncategorized. All four types now appear as quick-filter chips at the top of the folder menu, applying immediately and dismissing the menu. The bottom-sheet folder style keeps its original overflow entry.
