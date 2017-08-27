#include <jni.h>
#include <string>

#include "FLAC/metadata.h"
#include "FLAC/stream_encoder.h"
#include <android/log.h>

jclass encoder_class;
jfieldID encoder_field;
jclass decoder_class;
jfieldID decoder_field;
jmethodID decoder_init_result_method;

#define TAG "FlacJNI" // 这个是自定义的LOG的标识
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // 定义LOGF类型

FLAC__StreamEncoder *getEncoderFromInstance(JNIEnv *env, jobject instance) {
    return reinterpret_cast<FLAC__StreamEncoder *>(env->GetLongField(instance, encoder_field));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_wenbo_zhu_flac_FlacEncoder_init(
        JNIEnv *env,
        jobject instance,
        jint sample_rate,
        jint channels,
        jint bps,
        jint compressionLevel,
        jstring outFile) {
    FLAC__bool ok = true;
    FLAC__StreamEncoder *encoder = 0;
    FLAC__StreamEncoderInitStatus init_status;

    /* allocate the encoder */
    if ((encoder = FLAC__stream_encoder_new()) == NULL) {
        LOGE("ERROR: allocating encoder");
        return (jboolean) false;
    }

    ok &= FLAC__stream_encoder_set_verify(encoder, true);
    ok &= FLAC__stream_encoder_set_compression_level(encoder, (unsigned int) compressionLevel);
    ok &= FLAC__stream_encoder_set_channels(encoder, (unsigned int) channels);
    ok &= FLAC__stream_encoder_set_bits_per_sample(encoder, (unsigned int) bps);
    ok &= FLAC__stream_encoder_set_sample_rate(encoder, (unsigned int) sample_rate);

    const char *file = env->GetStringUTFChars(outFile, 0);
    if (ok) {
        init_status = FLAC__stream_encoder_init_file(encoder, file,
                                                     NULL, /*client_data=*/NULL);
        if (init_status != FLAC__STREAM_ENCODER_INIT_STATUS_OK) {
            LOGE("ERROR: initializing encoder: %s",
                 FLAC__StreamEncoderInitStatusString[init_status]);
            ok = false;
        }
    }

    if (ok) {
        env->SetLongField(instance, encoder_field, (jlong) encoder);
    }
    return (jboolean) ok;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_wenbo_zhu_flac_FlacEncoder_process(
        JNIEnv *env,
        jobject instance,
        jbyteArray data,
        jint len) {
    FLAC__StreamEncoder *encoder = getEncoderFromInstance(env, instance);
    signed char *bytes = new signed char[len];
    env->GetByteArrayRegion(data, 0, len, bytes);
    u_int bps = FLAC__stream_encoder_get_bits_per_sample(encoder);
    u_int samples = len * 8 / bps;
    u_int channels = FLAC__stream_encoder_get_channels(encoder);
    FLAC__int32 *buffer = new FLAC__int32[samples];
    for (int i = 0; i < samples; i++) {
        if (bps == 8) {
            buffer[i] = bytes[i];
        } else if (bps == 16) {
            buffer[i] = (bytes[2 * i] & 0xFF) | (bytes[2 * i + 1]) << 8;
        }
    }
    return (jboolean) FLAC__stream_encoder_process_interleaved(encoder, buffer,
                                                               samples / channels);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_wenbo_zhu_flac_FlacEncoder_finish(JNIEnv *env, jobject instance) {
    FLAC__StreamEncoder *encoder = getEncoderFromInstance(env, instance);
    FLAC__bool ok = FLAC__stream_encoder_finish(encoder);
    FLAC__stream_encoder_delete(encoder);
    return (jboolean) ok;
}

extern "C"
JNIEXPORT jint JNICALL
Java_wenbo_zhu_flac_FlacEncoder_getState(JNIEnv *env, jobject instance) {
    FLAC__StreamEncoder *encoder = getEncoderFromInstance(env, instance);
    return FLAC__stream_encoder_get_state(encoder);
}

class DecoderClientData {
public:
    JNIEnv *env;
    jobject instance;
    jobject result;

    DecoderClientData(JNIEnv *env, jobject instance) {
        this->env = env;
        this->instance = instance;
    }

    void accept_stream_meta_data(const FLAC__StreamMetadata *metadata) {
        jlong total_samples = metadata->data.stream_info.total_samples;
        jint sample_rate = metadata->data.stream_info.sample_rate;
        jint channels = metadata->data.stream_info.channels;
        jint bps = metadata->data.stream_info.bits_per_sample;
        result = env->CallObjectMethod(instance, decoder_init_result_method,
                                       total_samples, sample_rate, channels, bps);
    }
};

void metadata_callback(const FLAC__StreamDecoder *decoder, const FLAC__StreamMetadata *metadata,
                       void *client_data) {
    if (metadata->type == FLAC__METADATA_TYPE_STREAMINFO) {
        DecoderClientData *data = reinterpret_cast<DecoderClientData *>(client_data);
        data->accept_stream_meta_data(metadata);
    }
}

FLAC__StreamDecoderWriteStatus decoder_write_callback(const FLAC__StreamDecoder *decoder, const FLAC__Frame *frame, const FLAC__int32 * const buffer[], void *client_data) {

}

void decoder_error_callback(const FLAC__StreamDecoder *decoder, FLAC__StreamDecoderErrorStatus status, void *client_data) {

}

extern "C"
JNIEXPORT jobject JNICALL
Java_wenbo_zhu_flac_FlacDecoder_init(JNIEnv *env, jobject instance, jstring input_file) {
    FLAC__bool ok = true;
    FLAC__StreamDecoder *decoder = 0;
    FLAC__StreamDecoderInitStatus init_status;

    if ((decoder = FLAC__stream_decoder_new()) == NULL) {
        LOGE("ERROR: allocating decoder\n");
        return NULL;
    }

    (void) FLAC__stream_decoder_set_md5_checking(decoder, true);

    const char *input_file_chars = env->GetStringUTFChars(input_file, 0);
    DecoderClientData *client_data = new DecoderClientData(env, instance);
    init_status = FLAC__stream_decoder_init_file(decoder, input_file_chars, decoder_write_callback,
                                                 metadata_callback, decoder_error_callback, client_data);
    if (init_status != FLAC__STREAM_DECODER_INIT_STATUS_OK) {
        LOGE("ERROR: initializing decoder: %s\n",
                FLAC__StreamDecoderInitStatusString[init_status]);
        ok = false;
    }

    if (ok) {
        ok = FLAC__stream_decoder_process_until_end_of_metadata(decoder);
    }

    if (ok) {
        return client_data->result;
    } else {
        return NULL;
    }
}

JNIEXPORT
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jint result = -1;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return result;
    }

    encoder_class = env->FindClass("wenbo/zhu/flac/FlacEncoder");
    encoder_field = env->GetFieldID(encoder_class, "encoderPointer", "J");

    decoder_class = env->FindClass("wenbo/zhu/flac/FlacDecoder");
    decoder_field = env->GetFieldID(decoder_class, "decoderPointer", "J");
    decoder_init_result_method = env->GetMethodID(decoder_class, "newStreamData", "(JIII)Ljava/lang/Object;");

    return JNI_VERSION_1_6;
}
