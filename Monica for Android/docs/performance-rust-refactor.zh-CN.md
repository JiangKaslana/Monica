# Monica Android 性能与 Rust 重构方案

状态：第一阶段 Rust 核心与落地路线。此阶段**不直接替换生产列表**，避免在缺少基准、回归测试和 FFI 对齐时破坏密码数据语义。

## 结论

不要把 Compose、导航、生命周期、Room、Android Keystore、生物识别或系统服务整体重写成 Rust。那会把帧关键路径变成跨语言状态同步，增加 FFI 拷贝、崩溃面和维护成本。

Rust 应只承担可批处理、确定性、平台无关的核心：列表投影/排序/去重、导入导出解析、合并冲突算法、大批量校验与哈希。Android 平台边界继续留在 Kotlin。

## 已定位的主要卡顿来源

### P0：密码列表为“不显示的密码”做全量解密

`PasswordViewModel.passwordEntriesSource` 在筛选和去重后，对每条记录调用 `inspectSecretState(...).plainValueOrEmpty()`；`allPasswordsSource` 还会再解密一次整表。密码卡片绘制主要使用标题、用户名、网站、备注预览、更新时间、来源、图标和可选的验证器数据，并不需要登录密码明文。

后果：进入密码页、切换筛选、Room 失效通知或同步更新，都可能触发 O(n) 解密和 O(n) 对象复制。

第一优先修复：

1. 增加真正的 `PasswordListEntryUi`，结构中不允许出现 password；
2. 在解密 map 之前建立元数据列表流；
3. `PasswordListContent` 只订阅元数据流；
4. 详情、复制、编辑按 ID 加载一条记录并解密；
5. 内联 TOTP 只按可见条目解析验证器密钥，不能因此解密登录密码；
6. 现有需要明文的批处理命令保留独立命令链路，不能复用 UI 列表对象。

这一步通常比“把同样的全量解密改写成 Rust”收益更大，因为它直接删除了无用工作。

### P0：一个 Composable 订阅过多状态

`PasswordListContent` 同时收集密码列表、加载状态、全量元数据、搜索、分类、认证、完整设置对象、MDBX 操作、同步计数、KeePass 分组与远端同步、Bitwarden 状态、文件夹、附件父项和全局传输进度；它还在组合内部取得数据库与仓库。

后果：任何不相关状态变化都可能让巨大组合范围失效，切换标签和同步状态变化时容易出现明显顿挫。

修复：

- 新建 `PasswordListUiState` 与专用协调 ViewModel；
- 对小而稳定的状态切片使用 `distinctUntilChanged`；
- 屏幕级 Flow 改用 `collectAsStateWithLifecycle`；
- DAO/Repository 获取移出 Composable；
- 顶栏、同步状态、选择状态、列表行、对话框拆成独立稳定边界；
- UI model 标记不可变，所有 LazyList 条目使用稳定 key/contentType。

### P0：巨型编排类造成状态扇出

当前存在多份超大文件：`MainActivity`、`PasswordViewModel`、`MdbxViewModel`、`LocalKeePassViewModel`、`SimpleMainScreen`、`PasswordListContent`。文件大小本身不是性能指标，但这里对应的是导航、数据库、同步、导入、选择和展示职责交叉，任何小更新都可能扩散到不相关系统。

建议边界：

- `MainActivity`：只做 Android 入口；
- `AppGraph`：应用级依赖；
- `RootNavHost`：只注册路由与转场；
- `VaultSessionCoordinator`：锁定、解锁、会话恢复；
- `PasswordListViewModel`：只管理列表状态与列表命令；
- `PasswordDetailViewModel`：单条密文按需加载、编辑；
- Monica/KeePass/MDBX/Bitwarden 通过来源适配器实现统一接口。

### P1：首帧前构造过多依赖

`MainActivity.onCreate` 在 `setContent` 前同步构造 Room、SecurityManager、MDBX/密码/安全项仓库、设置与日志；Application 启动还初始化多个全局系统，并使用 `GlobalScope` 启动维护任务。

修复：

- 先用轻量会话元数据显示锁屏/外壳；
- 首帧后、认证后再按来源懒加载仓库；
- Room 单例由应用级容器持有；
- 附件清理、缓存预热、同步发现等放到结构化后台任务；
- 替换 `GlobalScope`，使用 Application 生命周期拥有的 CoroutineScope/WorkManager。

### P1：每张密码卡片可能启动多条图标解析链

卡片会准备自定义 Simple Icon、上传图标、应用图标、自动匹配图标和 favicon，再决定最终显示哪一个。滚动时可能产生多余的包管理器查询、图片解码与缓存/网络任务。

修复：

- 统一为单个 `PasswordLeadingIcon` 状态机；
- 先判断自定义来源，再决定 app/web 分支；
- 上级来源明确失败后才启动低优先级 fallback；
- 位图按目标尺寸预解码并缓存，禁止滚动期间重复解码。

### P1：数据库失效通知触发的工作过重

Room 已到 schema 78，并因 IME 独立进程启用了 multi-instance invalidation。该能力可能是必须的，不应为了跑分快速关闭；正确做法是降低每次失效后的列表工作量。

修复：

- DAO 返回列表专用元数据 projection；
- 来源、分类、收藏等筛选尽量下推 SQL；
- 用真实大库执行 `EXPLAIN QUERY PLAN` 检查索引；
- 大库搜索考虑 FTS，而不是反复整表内存过滤；
- Paging 需测量后再引入，它不能替代“取消全量解密”。

## Rust 边界

### 留在 Kotlin

- Compose 与动画；
- 导航、生命周期和 Android 进程集成；
- Room 所有权；
- Android Keystore、生物识别、权限、Intent、Service、WorkManager。

当前安全模型依赖 Keystore alias、受认证密钥访问、EncryptedSharedPreferences 和会话语义。性能重构不能偷偷替换成另一套 Rust 密钥库。

### 批量迁入 Rust

- 无密文列表投影、规范化搜索、排序与来源感知去重；
- 导入导出解析器，并配 fuzz 测试；
- 确定性合并/冲突算法；
- 与 Android 无关的 KDBX/MDBX 数据变换；
- 只有在 profiling 证明 CPU 瓶颈后，才迁移大批量哈希/校验。

### FFI 约束

- 每个快照或命令一次调用，禁止逐条调用；
- Compose State 和回调不能跨 FFI；
- DTO 有明确 schema version 和输入上限；
- 列表 DTO 永远没有密码；
- 迁移全部放在 feature flag 后；
- 先 shadow mode 对比 Kotlin/Rust 的有序 ID 与错误行为；
- 若并入现有 MDBX 动态库，可避免每个 ABI 再增加一份 Rust runtime 与 `.so` 体积。

## 分阶段交付

### Phase 0：测量与隔离核心（本 PR）

- 纯 Rust 无密文列表模型；
- 批量搜索/投影；
- 不会吞掉本地重复项和多密码兄弟项的保守去重；
- 顺序、搜索、Unicode、来源身份和副本语义测试；
- rustfmt、Clippy、test CI。

同时补齐 Macrobenchmark 场景：

1. 冷启动到锁屏；
2. 解锁到密码列表首屏；
3. 密码/验证器/笔记标签连续切换；
4. 100、1,000、10,000 条合成库滚动；
5. 连续打开/关闭密码详情。

记录：time-to-first-content、帧时长 p50/p95/p99、jank 百分比、主线程阻塞、分配次数、Java/native heap、APK/AAB 体积。

### Phase 1：先删除 Kotlin 无用工作

- 元数据专用列表流；
- 单条按需解密；
- 列表 UiState 协调器；
- lifecycle-aware 收集；
- 图标解析门控；
- 首帧依赖延迟；
- 拆开首批巨型编排边界。

生产启用 Rust 前必须先完成这一阶段。

### Phase 2：Rust shadow adapter

- 建立 UniFFI 批量桥接；
- 每个 distinct 快照/查询只调用一次；
- Debug/Preview 同时跑 Kotlin 和 Rust，对比有序 ID；
- 诊断日志只记录数量、耗时和哈希，禁止记录条目内容；
- Kotlin 结果仍为权威结果。

### Phase 3：受控切换

- 本地 feature flag 启用 Rust；
- 保留即时回退 Kotlin；
- 覆盖旧数据库升级、进程死亡、自动锁定、生物认证过期、IME 多进程失效、KeePass/MDBX/Bitwarden 混合视图和大量附件；
- parity 与基准达标后再删除重复 Kotlin 实现。

## 合并门槛

- shadow corpus 中有序 ID、筛选结果零差异；
- 日志、列表 DTO、SavedState、崩溃报告和 FFI DTO 中没有新明文；
- 1,000 条库密码页首内容中位时间至少改善 40%；
- 100 条库 p95 不回退；
- 标签切换 jank 明显下降，native heap 不持续增长；
- 解锁耗时不回退；
- 每 ABI 的包体增量已测量并接受；
- 进程死亡、自动锁、IME 多进程失效和数据库迁移测试全部通过。

绝对毫秒阈值应由 Phase 0 基线确定，不能在没有设备数据时拍脑袋填写。
