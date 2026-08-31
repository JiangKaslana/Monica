#![allow(non_snake_case)]

mod search;

use jni::objects::{JClass, JIntArray, JObjectArray, JString};
use jni::sys::{jboolean, jintArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use search::SearchQuery;

const RUST_CORE_VERSION: &str = "monica-rust-jni/0.3.0-index-search";

struct MetadataArrays<'local, 'borrow> {
    titles: &'borrow JObjectArray<'local>,
    usernames: &'borrow JObjectArray<'local>,
    websites: &'borrow JObjectArray<'local>,
    app_names: &'borrow JObjectArray<'local>,
    app_package_names: &'borrow JObjectArray<'local>,
}

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
    titles: JObjectArray,
    usernames: JObjectArray,
    websites: JObjectArray,
    app_names: JObjectArray,
    app_package_names: JObjectArray,
    query: JString,
) -> jintArray {
    let arrays = MetadataArrays {
        titles: &titles,
        usernames: &usernames,
        websites: &websites,
        app_names: &app_names,
        app_package_names: &app_package_names,
    };
    filter_indices(&mut env, arrays, &query).unwrap_or(std::ptr::null_mut())
}

fn filter_indices(
    env: &mut JNIEnv,
    arrays: MetadataArrays<'_, '_>,
    query: &JString,
) -> Option<jintArray> {
    let len = env.get_array_length(arrays.titles).ok()? as usize;
    for array in [
        arrays.usernames,
        arrays.websites,
        arrays.app_names,
        arrays.app_package_names,
    ] {
        if env.get_array_length(array).ok()? as usize != len {
            return None;
        }
    }

    let query: String = env.get_string(query).ok()?.into();
    let query = SearchQuery::new(&query);

    let mut selected = Vec::with_capacity(len);
    if query.is_empty() {
        selected.extend((0..len).map(|index| index as i32));
    } else {
        for index in 0..len {
            if row_matches(env, &arrays, index, &query)? {
                selected.push(index as i32);
            }
        }
    }

    let output: JIntArray<'_> = env.new_int_array(selected.len() as i32).ok()?;
    env.set_int_array_region(&output, 0, &selected).ok()?;
    Some(output.into_raw())
}

fn row_matches(
    env: &mut JNIEnv,
    arrays: &MetadataArrays<'_, '_>,
    index: usize,
    query: &SearchQuery,
) -> Option<bool> {
    for array in [
        arrays.titles,
        arrays.usernames,
        arrays.websites,
        arrays.app_names,
        arrays.app_package_names,
    ] {
        let object = env.get_object_array_element(array, index as i32).ok()?;
        if object.is_null() {
            continue;
        }
        let value: String = env.get_string(&JString::from(object)).ok()?.into();
        if query.matches_value(&value) {
            return Some(true);
        }
    }
    Some(false)
}
