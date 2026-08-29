# Monica Password List Rust Core

这是 Monica Android 性能重构的第一块 Rust 边界：**密码列表的无密文元数据筛选、投影与保守去重**。

## 为什么先做这一块

当前密码列表热路径会把筛选后的条目逐条解密，再把完整 `PasswordEntry` 送进 Compose。列表实际绘制主要依赖标题、用户名、网站、备注预览、更新时间、来源和图标，不需要密码明文。

正确顺序不是“把全量解密搬到 Rust”，而是：

1. 列表链路不再加载或解密密码；
2. 详情、复制、编辑等明确操作按 ID 加载单条密文并解密；
3. 大批量、确定性、平台无关的筛选/投影再交给 Rust；
4. Compose、生命周期、Room、Android Keystore 和生物识别继续留在 Kotlin。

## 安全边界

`PasswordListRecord` 没有密码字段。可选的 `secret_fingerprint` 只能是 Android 数据层在写入/导入时产生的、不可逆的带密钥摘要；不能传明文，也不能把随机化密文当成指纹。

去重采用保守原则：

- 没有明确来源身份的本地条目永不合并；
- 未知指纹不视为相同；
- 同一副本组、同一存储目标内出现多条记录时，视为用户有意保存的多密码兄弟条目；
- 只有“明确身份相同 + 已知指纹相同”的条目才允许折叠。

## Android 接入规则

后续通过一个批量 UniFFI/JNI 调用接入：每次列表快照或搜索请求调用一次，禁止逐行跨 FFI。接入必须放在功能开关后，先与 Kotlin 实现做 shadow parity，对比有序 ID 列表，Rust 结果暂不直接驱动生产界面。

本 crate 目前不接触 Android，也不包含 FFI；这样可以先稳定数据语义和测试，再决定是否并入现有 MDBX Rust 动态库，避免每个 ABI 再增加一套独立 `.so`。

## 校验

```bash
cargo fmt --all -- --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test --all-targets --all-features
```
