#include <jni.h>
#include "whisper.h"
#include <string>
#include <memory>
#include <algorithm>

struct Decoder {whisper_context* model;whisper_vad_context* vad;};
struct Abort {JavaVM* vm;jobject supplier;jmethodID method;};
static bool cancelled(void* opaque){
    auto* abort=static_cast<Abort*>(opaque);JNIEnv* env=nullptr;bool attached=false;
    if(abort->vm->GetEnv(reinterpret_cast<void**>(&env),JNI_VERSION_1_6)!=JNI_OK){if(abort->vm->AttachCurrentThread(&env,nullptr)!=JNI_OK)return true;attached=true;}
    bool result=env->CallBooleanMethod(abort->supplier,abort->method);if(env->ExceptionCheck()){env->ExceptionClear();result=true;}
    if(attached)abort->vm->DetachCurrentThread();return result;
}
static void silent_log(ggml_log_level,const char*,void*){}
static jobjectArray result(JNIEnv* env,const std::string& text,const char* language){
    auto strings=env->NewObjectArray(2,env->FindClass("java/lang/String"),nullptr);
    // Construct from UTF-8 bytes, preserving supplementary Unicode characters too.
    auto stringClass=env->FindClass("java/lang/String");auto ctor=env->GetMethodID(stringClass,"<init>","([BLjava/lang/String;)V");
    auto bytes=env->NewByteArray(static_cast<jsize>(text.size()));env->SetByteArrayRegion(bytes,0,text.size(),reinterpret_cast<const jbyte*>(text.data()));
    auto encoding=env->NewStringUTF("UTF-8");auto decoded=env->NewObject(stringClass,ctor,bytes,encoding);
    env->SetObjectArrayElement(strings,0,decoded);env->SetObjectArrayElement(strings,1,env->NewStringUTF(language));return strings;
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_orbitvoice_mobile_WhisperAsr_open(JNIEnv* env,jclass,jstring model,jstring vad){
    whisper_log_set(silent_log,nullptr);auto params=whisper_context_default_params();params.use_gpu=false;
    const char* modelPath=env->GetStringUTFChars(model,nullptr);auto* context=whisper_init_from_file_with_params(modelPath,params);env->ReleaseStringUTFChars(model,modelPath);if(!context)return 0;
    auto vp=whisper_vad_default_context_params();vp.use_gpu=false;vp.n_threads=1;
    const char* vadPath=env->GetStringUTFChars(vad,nullptr);auto* detector=whisper_vad_init_from_file_with_params(vadPath,vp);env->ReleaseStringUTFChars(vad,vadPath);
    if(!detector){whisper_free(context);return 0;}return reinterpret_cast<jlong>(new Decoder{context,detector});
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_dev_orbitvoice_mobile_WhisperAsr_decode(JNIEnv* env,jclass,jlong handle,jfloatArray audio,jint threads,jobject supplier){
    auto* decoder=reinterpret_cast<Decoder*>(handle);if(!decoder)return nullptr;
    Abort abort{};env->GetJavaVM(&abort.vm);abort.supplier=env->NewGlobalRef(supplier);abort.method=env->GetMethodID(env->GetObjectClass(supplier),"getAsBoolean","()Z");
    float* samples=env->GetFloatArrayElements(audio,nullptr);int count=env->GetArrayLength(audio);jobjectArray output=nullptr;
    if(!cancelled(&abort)&&whisper_vad_detect_speech(decoder->vad,samples,count)){
        auto* probs=whisper_vad_probs(decoder->vad);int voiced=0;for(int i=0;i<whisper_vad_n_probs(decoder->vad);i++)if(probs[i]>=0.55f)voiced++;
        if(voiced<3)output=result(env,"","");
        else{
            auto params=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
            params.n_threads=std::max(1,static_cast<int>(threads));params.language="auto";params.detect_language=false;params.translate=false;params.no_context=true;
            params.no_timestamps=true;params.single_segment=true;params.print_realtime=false;params.print_progress=false;params.print_timestamps=false;params.print_special=false;
            params.suppress_nst=true;params.temperature=0;params.temperature_inc=0;params.abort_callback=cancelled;params.abort_callback_user_data=&abort;
            if(whisper_full(decoder->model,params,samples,count)==0&&!cancelled(&abort)){
                std::string text;for(int i=0;i<whisper_full_n_segments(decoder->model);i++)text+=whisper_full_get_segment_text(decoder->model,i);
                const char* language=whisper_lang_str(whisper_full_lang_id(decoder->model));output=result(env,text,language?language:"");
            }
        }
    }else if(!cancelled(&abort))output=result(env,"","");
    env->ReleaseFloatArrayElements(audio,samples,JNI_ABORT);env->DeleteGlobalRef(abort.supplier);return output;
}
extern "C" JNIEXPORT void JNICALL Java_dev_orbitvoice_mobile_WhisperAsr_release(JNIEnv*,jclass,jlong handle){
    auto* decoder=reinterpret_cast<Decoder*>(handle);if(decoder){whisper_vad_free(decoder->vad);whisper_free(decoder->model);delete decoder;}
}
