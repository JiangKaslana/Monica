### Monica for Android

## 中文
### 简要
* Monica 键盘的验证器与卡包页面现在会在数据加载期间显示统一加载态，不再短暂误显示为空。
* 修复卡包页面拖动排序后卡片二次交换的问题，松手后顺序保持不变。
* 修复导入页面文件名显示为内部 `document:<id>` 的问题，现在优先显示文件原名。
* 卡包的全部、银行卡、证件和账单地址筛选从右上角溢出菜单移入分类菜单的快捷筛选区。
* 验证器页右上角菜单新增布局切换，可直接在标准列表和磁贴视图之间切换。
* 新增触觉反馈总开关，可一次关闭下拉手势、长按等交互的震动。

### 详细
* Monica 键盘在切换、解锁恢复或重新打开验证器和卡包页面时，会优先显示与密码页一致的加载状态，真实加载完成后才显示空列表。
* 卡包列表的手动顺序此前会被条目更新时间压过，且拖动结果在数据写入期间被旧列表回写，松手后卡片会再交换一次。现在手动顺序优先生效、并以条目 ID 作为稳定兜底，写入完成前保持松手时的顺序；在筛选状态下拖动时，未显示的条目也会保留原有位置。
* Android 文档选择器现在通过 `OpenableColumns.DISPLAY_NAME` 读取用户可见的原始文件名，并在无法查询时从 URI 路径安全回退；查询放在 IO 调度器，避免导入页面卡顿。KDBX 密钥文件选择也使用同一套解析逻辑。
* 卡包的类型筛选此前只在右上角溢出菜单里，与收藏、未分类等快捷筛选分处两个入口。现在这四个类型作为快捷筛选磁贴显示在分类菜单顶部，点击后即时生效并关闭菜单；使用底部弹窗分类样式时仍保留原有的溢出菜单入口。
* 验证器的布局样式此前只能在设置的页面调整里修改。现在验证器页右上角菜单直接提供切换项，菜单文字与图标会显示将要切换到的目标样式；切换结果沿用原有设置项的存储，重启和备份恢复后保持一致。
* 此前各页面的震动逻辑分散在各自实现里，只有验证器倒计时有独立开关，其余交互无法关闭。现在下拉手势、长按和滑动操作的震动统一走同一套触觉反馈实现，并由「触觉反馈」总开关控制；验证器震动作为它的子项，总开关关闭时一并停用并置灰。设置页和扩展页都提供该开关，且不需要 Plus。

## English
### Summary
* The Monica Keyboard now shows a consistent loading state for authenticator and card-wallet panels instead of briefly presenting an incorrect empty state.
* Fixed card-wallet items swapping a second time after a drag; the order you release now stays put.
* Fixed imported files showing an internal `document:<id>` value instead of the original filename.
* Card-wallet All, Bank Cards, Documents, and Billing Addresses filters moved from the overflow menu into the folder menu's quick filters.
* The authenticator overflow menu can now switch between the standard list and the tile layout directly.
* Added a master haptic feedback toggle that turns off vibration for pull gestures, long presses, and other interactions at once.

### Details
* When the Monica Keyboard switches to, restores after unlock, or reopens an authenticator or card-wallet panel, it shows the same loading state as the password panel until the real result is available.
* Manual card-wallet order was previously overridden by item update time, and a drag result could be overwritten by the stale list while the new order was still being saved, causing a second swap after release. Manual order now takes precedence with item ID as a stable tiebreaker, the released order is held until the write completes, and dragging inside a filtered view keeps hidden items in their original slots.
* Android imports now query `OpenableColumns.DISPLAY_NAME` for the user-visible filename, safely fall back to a decoded URI path when providers omit it, and perform the query on the IO dispatcher. KDBX key-file selection uses the same resolver.
* Card-wallet type filters previously lived only in the overflow menu, separate from quick filters such as Favorites and Uncategorized. All four types now appear as quick-filter chips at the top of the folder menu, applying immediately and dismissing the menu. The bottom-sheet folder style keeps its original overflow entry.
* The authenticator layout style was previously reachable only through Settings, under page adjustments. The authenticator overflow menu now offers the switch directly, with its label and icon showing the style you are switching to. The choice reuses the existing setting, so it survives restarts and backup restores.
* Vibration logic was previously scattered across individual screens, and only the authenticator countdown had its own toggle, leaving other interactions impossible to silence. Pull gestures, long presses, and swipe actions now share a single haptic feedback implementation governed by a master "Haptic Feedback" switch. Authenticator vibration becomes a sub-item that is disabled and greyed out when the master switch is off. The toggle appears in both Settings and Extensions, and does not require Plus.
