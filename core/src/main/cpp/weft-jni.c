/*
 * JNI glue between Kotlin (app.weft.core.CoreKt) and the SimpleX chat core (libsimplex.so).
 * Adapted from simplex-chat/apps/multiplatform/common/src/commonMain/cpp/android/simplex-api.c @ v7.0.2
 * (AGPL-3.0). Trimmed to the functions Weft uses; remote-host pairing and the stdout socket are dropped.
 *
 * Ownership note: the official glue never frees the C strings returned by the Haskell side. Weft must
 * verify this against the core sources before Phase 1 ships (a leak per message would be visible).
 */
#include <jni.h>
#include <string.h>
#include <stdint.h>

// from the RTS
void hs_init_with_rtsopts(int * argc, char **argv[]);

// from android-support
void setLineBuffering(void);

extern void __svfscanf(void){};
extern void __vfwscanf(void){};
extern void __memset_chk_fail(void){};
extern void __strcpy_chk_generic(void){};
extern void __strcat_chk_generic(void){};
extern void __libc_globals(void){};
extern void __rel_iplt_start(void){};

// Android 9 only, not 13
extern void reallocarray(void){};

JNIEXPORT void JNICALL
Java_app_weft_core_CoreKt_initHS(__unused JNIEnv *env, __unused jclass clazz) {
    int argc = 5;
    char *argv[] = {
        "simplex",
        "+RTS", // requires `hs_init_with_rtsopts`
        "-A64m", // chunk size for new allocations
        "-H64m", // initial heap size
        "-xn", // non-moving GC
        NULL
    };
    char **pargv = argv;
    hs_init_with_rtsopts(&argc, &pargv);
    setLineBuffering();
}

// from simplex-chat
typedef long* chat_ctrl;

/*
   When you start using any new function from Haskell libraries,
   you have to add the function name to the file libsimplex.dll.def in the root directory.
   And do the same by adding it into flake.nix file in the root directory,
   Otherwise, Windows and Android libraries cannot be built.
*/

extern char *chat_migrate_init(const char *path, const char *key, const char *confirm, chat_ctrl *ctrl);
extern char *chat_close_store(chat_ctrl ctrl);
extern char *chat_send_cmd_retry(chat_ctrl ctrl, const char *cmd, const int retryNum);
extern char *chat_send_remote_cmd_retry(chat_ctrl ctrl, const int rhId, const char *cmd, const int retryNum);
extern char *chat_recv_msg(chat_ctrl ctrl); // deprecated
extern char *chat_recv_msg_wait(chat_ctrl ctrl, const int wait);
extern char *chat_parse_markdown(const char *str);
extern char *chat_parse_server(const char *str);
extern char *chat_parse_uri(const char *str, const int safe);
extern char *chat_password_hash(const char *pwd, const char *salt);
extern char *chat_valid_name(const char *name);
extern int chat_json_length(const char *str);
extern char *chat_write_file(chat_ctrl ctrl, const char *path, char *ptr, int length);
extern char *chat_read_file(const char *path, const char *key, const char *nonce);
extern char *chat_encrypt_file(chat_ctrl ctrl, const char *from_path, const char *to_path);
extern char *chat_decrypt_file(const char *from_path, const char *key, const char *nonce, const char *to_path);

JNIEXPORT jobjectArray JNICALL
Java_app_weft_core_CoreKt_chatMigrateInit(JNIEnv *env, __unused jclass clazz, jstring dbPath, jstring dbKey, jstring confirm) {
    const char *_dbPath = (*env)->GetStringUTFChars(env, dbPath, JNI_FALSE);
    const char *_dbKey = (*env)->GetStringUTFChars(env, dbKey, JNI_FALSE);
    const char *_confirm = (*env)->GetStringUTFChars(env, confirm, JNI_FALSE);
    jlong _ctrl = (jlong) 0;
    jstring res = (*env)->NewStringUTF(env, chat_migrate_init(_dbPath, _dbKey, _confirm, &_ctrl));
    (*env)->ReleaseStringUTFChars(env, dbPath, _dbPath);
    (*env)->ReleaseStringUTFChars(env, dbKey, _dbKey);
    (*env)->ReleaseStringUTFChars(env, confirm, _confirm);

    // Creating array of Object's (boxed values can be passed, eg. Long instead of long)
    jobjectArray ret = (jobjectArray)(*env)->NewObjectArray(env, 2, (*env)->FindClass(env, "java/lang/Object"), NULL);
    // Java's String
    (*env)->SetObjectArrayElement(env, ret, 0, res);
    // Java's Long
    (*env)->SetObjectArrayElement(env, ret, 1,
        (*env)->NewObject(env, (*env)->FindClass(env, "java/lang/Long"),
        (*env)->GetMethodID(env, (*env)->FindClass(env, "java/lang/Long"), "<init>", "(J)V"),
        _ctrl));
    return ret;
}

JNIEXPORT jstring JNICALL
Java_app_weft_core_CoreKt_chatCloseStore(JNIEnv *env, __unused jclass clazz, jlong controller) {
    jstring res = (*env)->NewStringUTF(env, chat_close_store((void*)controller));
    return res;
}

JNIEXPORT jstring JNICALL
Java_app_weft_core_CoreKt_chatSendCmdRetry(JNIEnv *env, __unused jclass clazz, jlong controller, jstring msg, jint retryNum) {
    const char *_msg = (*env)->GetStringUTFChars(env, msg, JNI_FALSE);
    //jint length = (jint) (*env)->GetStringUTFLength(env, msg);
    //for (int i = 0; i < length; ++i)
    //    __android_log_print(ANDROID_LOG_ERROR, "simplex", "%d: %02x\n", i, _msg[i]);
    jstring res = (*env)->NewStringUTF(env, chat_send_cmd_retry((void*)controller, _msg, retryNum));
    (*env)->ReleaseStringUTFChars(env, msg, _msg);
    return res;
}

JNIEXPORT jstring JNICALL
Java_app_weft_core_CoreKt_chatRecvMsgWait(JNIEnv *env, __unused jclass clazz, jlong controller, jint wait) {
    return (*env)->NewStringUTF(env, chat_recv_msg_wait((void*)controller, wait));
}

JNIEXPORT jstring JNICALL
Java_app_weft_core_CoreKt_chatParseServer(JNIEnv *env, __unused jclass clazz, jstring str) {
    const char *_str = (*env)->GetStringUTFChars(env, str, JNI_FALSE);
    jstring res = (*env)->NewStringUTF(env, chat_parse_server(_str));
    (*env)->ReleaseStringUTFChars(env, str, _str);
    return res;
}

JNIEXPORT jstring JNICALL
Java_app_weft_core_CoreKt_chatParseUri(JNIEnv *env, __unused jclass clazz, jstring str, jint safe) {
    const char *_str = (*env)->GetStringUTFChars(env, str, JNI_FALSE);
    jstring res = (*env)->NewStringUTF(env, chat_parse_uri(_str, safe));
    (*env)->ReleaseStringUTFChars(env, str, _str);
    return res;
}

JNIEXPORT jstring JNICALL
Java_app_weft_core_CoreKt_chatValidName(JNIEnv *env, jclass clazz, jstring name) {
    const char *_name = (*env)->GetStringUTFChars(env, name, JNI_FALSE);
    jstring res = (*env)->NewStringUTF(env, chat_valid_name(_name));
    (*env)->ReleaseStringUTFChars(env, name, _name);
    return res;
}

