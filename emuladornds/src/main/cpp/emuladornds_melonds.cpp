#include <jni.h>

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

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_pelkidev_com_mx_emuladornds_data_MelonDsCore_nativeCreate(
        JNIEnv* env,
        jobject /* thiz */,
        jbyteArray rom) {
    if (rom == nullptr) {
        return 0;
    }
    const jsize size = env->GetArrayLength(rom);
    if (size <= 0) {
        return 0;
    }

    auto romCopy = std::make_unique<melonDS::u8[]>(static_cast<size_t>(size));
    env->GetByteArrayRegion(
            rom,
            0,
            size,
            reinterpret_cast<jbyte*>(romCopy.get()));

    auto cart = melonDS::NDSCart::ParseROM(
            std::move(romCopy),
            static_cast<melonDS::u32>(size));
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
