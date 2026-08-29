#![allow(non_snake_case)]

use jni::objects::{JClass, JLongArray, JObjectArray, JString};
use jni::sys::{jboolean, jlongArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use monica_password_list_core::{project_password_list, PasswordListRecord, ProjectionOptions};

const RUST_CORE_VERSION: &str = "monica-password-list-core/0.2.0-runtime";

struct MetadataArrays<'local, 'borrow> {
    ids: &'borrow JLongArray<'local>,
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
        ProjectionOptions {
            collapse_known_replicas: false,
        },
    );
    if projected.items.len() == 1 && projected.items[0].id == 101 {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_rustcore_RustPasswordListCore_nativeFilterIds(
    mut env: JNIEnv,
    _class: JClass,
    ids: JLongArray,
    titles: JObjectArray,
    usernames: JObjectArray,
    websites: JObjectArray,
    app_names: JObjectArray,
    app_package_names: JObjectArray,
    query: JString,
) -> jlongArray {
    let arrays = MetadataArrays {
        ids: &ids,
        titles: &titles,
        usernames: &usernames,
        websites: &websites,
        app_names: &app_names,
        app_package_names: &app_package_names,
    };
    filter_ids(&mut env, arrays, &query).unwrap_or(std::ptr::null_mut())
}

fn filter_ids(
    env: &mut JNIEnv,
    arrays: MetadataArrays<'_, '_>,
    query: &JString,
) -> Option<jlongArray> {
    let len = env.get_array_length(arrays.ids).ok()? as usize;
    for array in [
        arrays.titles,
        arrays.usernames,
        arrays.websites,
        arrays.app_names,
        arrays.app_package_names,
    ] {
        if env.get_array_length(array).ok()? as usize != len {
            return None;
        }
    }

    let mut id_values = vec![0_i64; len];
    env.get_long_array_region(arrays.ids, 0, &mut id_values)
        .ok()?;
    let titles = read_string_array(env, arrays.titles, len)?;
    let usernames = read_string_array(env, arrays.usernames, len)?;
    let websites = read_string_array(env, arrays.websites, len)?;
    let app_names = read_string_array(env, arrays.app_names, len)?;
    let app_package_names = read_string_array(env, arrays.app_package_names, len)?;
    let query: String = env.get_string(query).ok()?.into();

    let records = (0..len)
        .map(|index| PasswordListRecord {
            id: id_values[index],
            title: titles[index].clone(),
            username: usernames[index].clone(),
            website: websites[index].clone(),
            app_name: app_names[index].clone(),
            app_package_name: app_package_names[index].clone(),
            notes_preview: String::new(),
            collapse_identity: None,
            storage_target_key: None,
            secret_fingerprint: None,
            is_favorite: false,
            updated_at_millis: 0,
        })
        .collect::<Vec<_>>();

    let projected = project_password_list(
        &records,
        &query,
        ProjectionOptions {
            collapse_known_replicas: false,
        },
    );
    let selected_ids = projected
        .items
        .iter()
        .map(|item| item.id)
        .collect::<Vec<_>>();
    let output = env.new_long_array(selected_ids.len() as i32).ok()?;
    env.set_long_array_region(&output, 0, &selected_ids).ok()?;
    Some(output.into_raw())
}

fn read_string_array(env: &mut JNIEnv, array: &JObjectArray, len: usize) -> Option<Vec<String>> {
    let mut values = Vec::with_capacity(len);
    for index in 0..len {
        let object = env.get_object_array_element(array, index as i32).ok()?;
        if object.is_null() {
            values.push(String::new());
            continue;
        }
        let string = JString::from(object);
        let value: String = env.get_string(&string).ok()?.into();
        values.push(value);
    }
    Some(values)
}
