#![allow(non_snake_case)]

mod search;

use jni::objects::{JByteArray, JClass, JIntArray, JString};
use jni::sys::{jboolean, jintArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use search::{filter_metadata_batch, SearchQuery};

const RUST_CORE_VERSION: &str = "monica-rust-jni/0.4.0-batched-search";

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
    let query = SearchQuery::new("github");
    if query.matches_value("GitHub") && !query.matches_value("example.cn") {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_rustcore_RustPasswordListCore_nativeFilterIndices(
    mut env: JNIEnv,
    _class: JClass,
    metadata: JByteArray,
    query: JString,
) -> jintArray {
    filter_indices(&mut env, &metadata, &query).unwrap_or(std::ptr::null_mut())
}

fn filter_indices(
    env: &mut JNIEnv,
    metadata: &JByteArray,
    query: &JString,
) -> Option<jintArray> {
    // One JNI copy replaces five object arrays plus up to 5*N element/string
    // lookups. The parser then borrows UTF-8 slices directly from this buffer.
    let metadata = env.convert_byte_array(metadata).ok()?;
    let query: String = env.get_string(query).ok()?.into();
    let query = SearchQuery::new(&query);
    let selected = filter_metadata_batch(&metadata, &query)?;

    let output: JIntArray<'_> = env.new_int_array(selected.len() as i32).ok()?;
    env.set_int_array_region(&output, 0, &selected).ok()?;
    Some(output.into_raw())
}
