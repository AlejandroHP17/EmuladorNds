#include <jni.h>

#include <cstdio>
#include <memory>

#include "Args.h"
#include "NDS.h"
#include "NDSCart.h"
#include "types.h"

namespace {

constexpr int kPixels = 256 * 192;

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

extern "C" JNIEXPORT void JNICALL
Java_pelkidev_com_mx_emuladornds_data_MelonDsCore_nativeRunFrame(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle,
        jintArray top,
        jintArray bottom) {
    auto* machine = reinterpret_cast<Machine*>(handle);
    if (machine == nullptr || machine->nds == nullptr) {
        return;
    }

    melonDS::NDS& nds = *machine->nds;
    nds.RunFrame();
    nds.SPU.DrainOutput();

    const int front = nds.GPU.FrontBuffer;
    const melonDS::u32* topPixels = nds.GPU.Framebuffer[front][0].get();
    const melonDS::u32* bottomPixels = nds.GPU.Framebuffer[front][1].get();
    CopyScreen(env, top, topPixels);
    CopyScreen(env, bottom, bottomPixels);
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
