#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <memory>

#include "Args.h"
#include "NDS.h"
#include "NDSCart.h"
#include "types.h"

namespace {

constexpr int kPixels = 256 * 192;
constexpr int kMaxNativeSamples = 2048;
constexpr int kMaxOutputSamples = 4096;
constexpr float kNativeSampleRate = 32823.6328125f;
constexpr float kOutputSampleRate = 48000.0f;

struct Machine {
    std::unique_ptr<melonDS::NDS> nds;
};

void CopyScreen(JNIEnv* env, jintArray out, const melonDS::u32* src) {
    if (out == nullptr || src == nullptr) {
        return;
    }
    if (env->GetArrayLength(out) < kPixels) {
        return;
    }
    env->SetIntArrayRegion(out, 0, kPixels, reinterpret_cast<const jint*>(src));
}

std::unique_ptr<melonDS::u8[]> ReadFileBytes(const char* path, melonDS::u32* outSize) {
    FILE* file = std::fopen(path, "rb");
    if (file == nullptr) {
        return nullptr;
    }
    if (std::fseek(file, 0, SEEK_END) != 0) {
        std::fclose(file);
        return nullptr;
    }
    const long length = std::ftell(file);
    if (length <= 0) {
        std::fclose(file);
        return nullptr;
    }
    if (std::fseek(file, 0, SEEK_SET) != 0) {
        std::fclose(file);
        return nullptr;
    }
    auto bytes = std::make_unique<melonDS::u8[]>(static_cast<size_t>(length));
    const size_t read = std::fread(bytes.get(), 1, static_cast<size_t>(length), file);
    std::fclose(file);
    if (read != static_cast<size_t>(length)) {
        return nullptr;
    }
    *outSize = static_cast<melonDS::u32>(length);
    return bytes;
}

int ResampleTo48k(
        const int16_t* input,
        int inputFrames,
        int16_t* output,
        int maxOutputFrames) {
    if (inputFrames <= 0 || maxOutputFrames <= 0) {
        return 0;
    }
    const float ratio = kNativeSampleRate / kOutputSampleRate;
    int written = 0;
    float position = 0.0f;
    while (written < maxOutputFrames) {
        const int index = static_cast<int>(position);
        if (index >= inputFrames - 1) {
            break;
        }
        const float frac = position - static_cast<float>(index);
        const int16_t l0 = input[index * 2];
        const int16_t l1 = input[(index + 1) * 2];
        const int16_t r0 = input[index * 2 + 1];
        const int16_t r1 = input[(index + 1) * 2 + 1];
        output[written * 2] = static_cast<int16_t>(l0 + (l1 - l0) * frac);
        output[written * 2 + 1] = static_cast<int16_t>(r0 + (r1 - r0) * frac);
        ++written;
        position += ratio;
    }
    return written;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_pelkidev_com_mx_emuladornds_data_MelonDsCore_nativeCreate(
        JNIEnv* env,
        jobject /* thiz */,
        jstring romPath) {
    if (romPath == nullptr) {
        return 0;
    }
    const char* path = env->GetStringUTFChars(romPath, nullptr);
    if (path == nullptr) {
        return 0;
    }

    melonDS::u32 size = 0;
    auto romCopy = ReadFileBytes(path, &size);
    env->ReleaseStringUTFChars(romPath, path);
    if (!romCopy || size == 0) {
        return 0;
    }

    auto cart = melonDS::NDSCart::ParseROM(std::move(romCopy), size);
    if (!cart) {
        return 0;
    }

    auto machine = std::make_unique<Machine>();
    try {
        machine->nds = std::make_unique<melonDS::NDS>(melonDS::NDSArgs{});
        machine->nds->SetNDSCart(std::move(cart));
        machine->nds->Reset();
        if (machine->nds->NeedsDirectBoot()) {
            machine->nds->SetupDirectBoot("rom.nds");
        }
        machine->nds->Start();
    } catch (...) {
        return 0;
    }

    return reinterpret_cast<jlong>(machine.release());
}

extern "C" JNIEXPORT jint JNICALL
Java_pelkidev_com_mx_emuladornds_data_MelonDsCore_nativeRunFrame(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle,
        jintArray top,
        jintArray bottom,
        jshortArray audioOut) {
    auto* machine = reinterpret_cast<Machine*>(handle);
    if (machine == nullptr || machine->nds == nullptr) {
        return 0;
    }

    melonDS::NDS& nds = *machine->nds;
    nds.RunFrame();

    int available = nds.SPU.GetOutputSize();
    available = std::min(available, kMaxNativeSamples);
    int16_t nativePcm[kMaxNativeSamples * 2];
    const int got = available > 0 ? nds.SPU.ReadOutput(nativePcm, available) : 0;

    jint produced = 0;
    if (got > 0 && audioOut != nullptr) {
        const jsize capacity = env->GetArrayLength(audioOut) / 2;
        const int maxOut = std::min(static_cast<int>(capacity), kMaxOutputSamples);
        int16_t outputPcm[kMaxOutputSamples * 2];
        produced = ResampleTo48k(nativePcm, got, outputPcm, maxOut);
        if (produced > 0) {
            env->SetShortArrayRegion(
                    audioOut,
                    0,
                    produced * 2,
                    reinterpret_cast<const jshort*>(outputPcm));
        }
    } else if (got > 0) {
        nds.SPU.DrainOutput();
    }

    const int front = nds.GPU.FrontBuffer;
    const melonDS::u32* topPixels = nds.GPU.Framebuffer[front][0].get();
    const melonDS::u32* bottomPixels = nds.GPU.Framebuffer[front][1].get();
    CopyScreen(env, top, topPixels);
    CopyScreen(env, bottom, bottomPixels);
    return produced;
}

extern "C" JNIEXPORT void JNICALL
Java_pelkidev_com_mx_emuladornds_data_MelonDsCore_nativeDestroy(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong handle) {
    auto* machine = reinterpret_cast<Machine*>(handle);
    if (machine == nullptr) {
        return;
    }
    if (machine->nds != nullptr) {
        machine->nds->Stop();
        machine->nds.reset();
    }
    delete machine;
}
