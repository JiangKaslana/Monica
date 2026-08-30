# Monica Android Rust JNI

`rust-jni` 是 Android 与 `rust-core` 之间的窄桥接层。

当前运行时接入的是密码列表元数据搜索。Kotlin 一次性传入平行数组：ID、标题、用户名、网站、应用名、包名和查询词；Rust 返回保持原顺序的匹配 ID。登录密码、TOTP secret、支付信息等敏感值不进入 JNI。

## 回退

`RustPasswordListCore` 会懒加载 `libmonica_rust_jni.so` 并执行 native self-test。如果动态库缺失或调用失败，调用方返回 `null` 并使用 Kotlin 元数据筛选，应用功能不会因 Rust 不可用而失效。

## Android 构建

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk --version 4.1.2 --locked
cd rust-jni
cargo ndk \
  -t arm64-v8a \
  -t armeabi-v7a \
  -o ../app/src/main/jniLibs \
  build --release
```

生成的 `.so` 是构建产物，不应手工提交。正式 Release 和 Preview 工作流会在 Gradle 打包前执行同样的 native 构建。
