#![allow(non_snake_case)]

use jni::objects::JClass;
use jni::sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use monica_password_list_core::{project_password_list, PasswordListRecord, ProjectionOptions};

const RUST_CORE_VERSION: &str = "monica-password-list-core/0.2.0-runtime";

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_rustcore_RustPasswordListCore_nativeVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    match env.new_string(RUST_CORE_VERSION) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_rustcore_RustPasswordListCore_nativeSelfTest(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let rows = vec![PasswordListRecord {
        id: 101,
        title: "GitHub".to_owned(),
        username: "octocat".to_owned(),
        website: "https://github.com".to_owned(),
        app_name: String::new(),
        app_package_name: String::new(),
        notes_preview: String::new(),
        collapse_identity: None,
        storage_target_key: None,
        secret_fingerprint: None,
        is_favorite: true,
        updated_at_millis: 1,
    }];
    let projected = project_password_list(
        &rows,
        "github",
        ProjectionOptions { collapse_known_replicas: false },
    );
    if projected.items.len() == 1 && projected.items[0].id == 101 { JNI_TRUE } else { JNI_FALSE }
}
