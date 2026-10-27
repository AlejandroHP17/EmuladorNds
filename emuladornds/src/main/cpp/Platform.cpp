#include "Platform.h"

#include <android/log.h>

#include <chrono>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <unistd.h>

namespace melonDS::Platform {

namespace {

constexpr const char* kTag = "EmuladorNds";

struct ThreadState {
    std::thread thread;
};

struct SemaphoreState {
    std::mutex mutex;
    std::condition_variable cv;
    int count = 0;
};

struct MutexState {
    std::mutex mutex;
};

std::string ModeString(FileMode mode, bool exists) {
    std::string out;
    if (mode & FileMode::Append) {
        out = "a";
    } else if (!(mode & FileMode::Write)) {
        out = "r";
    } else if ((mode & FileMode::NoCreate) || ((mode & FileMode::Preserve) && exists)) {
        out = "r";
    } else {
        out = "w";
    }
    if ((mode & FileMode::ReadWrite) == FileMode::ReadWrite) {
        out += '+';
    }
    if (!(mode & FileMode::Text)) {
        out += 'b';
    }
    return out;
}

bool PathExists(const std::string& path) {
    FILE* file = fopen(path.c_str(), "rb");
    if (!file) {
        return false;
    }
    fclose(file);
    return true;
}

}  // namespace

void SignalStop(StopReason, void*) {}

std::string GetLocalFilePath(const std::string& filename) {
    return filename;
}

FileHandle* OpenFile(const std::string& path, FileMode mode) {
    if ((mode & (FileMode::ReadWrite | FileMode::Append)) == FileMode::None) {
        return nullptr;
    }
    const bool exists = PathExists(path);
    if ((mode & FileMode::NoCreate) && !exists && (mode & FileMode::Write)) {
        return nullptr;
    }
    FILE* file = fopen(path.c_str(), ModeString(mode, exists).c_str());
    return reinterpret_cast<FileHandle*>(file);
}

FileHandle* OpenLocalFile(const std::string& path, FileMode mode) {
    return OpenFile(GetLocalFilePath(path), mode);
}

bool FileExists(const std::string& name) {
    return PathExists(name);
}

bool LocalFileExists(const std::string& name) {
    return PathExists(GetLocalFilePath(name));
}

bool CheckFileWritable(const std::string& filepath) {
    FileHandle* existing = OpenFile(filepath, FileMode::Read);
    if (existing) {
        CloseFile(existing);
        FileHandle* writable = OpenFile(filepath, FileMode::Append);
        if (!writable) {
            return false;
        }
        CloseFile(writable);
        return true;
    }
    FileHandle* created = OpenFile(filepath, FileMode::Write);
    if (!created) {
        return false;
    }
    CloseFile(created);
    remove(filepath.c_str());
    return true;
}

bool CheckLocalFileWritable(const std::string& filepath) {
    return CheckFileWritable(GetLocalFilePath(filepath));
}

bool CloseFile(FileHandle* file) {
    return fclose(reinterpret_cast<FILE*>(file)) == 0;
}

bool IsEndOfFile(FileHandle* file) {
    return feof(reinterpret_cast<FILE*>(file)) != 0;
}

bool FileReadLine(char* str, int count, FileHandle* file) {
    return fgets(str, count, reinterpret_cast<FILE*>(file)) != nullptr;
}

bool FileSeek(FileHandle* file, s64 offset, FileSeekOrigin origin) {
    int stdorigin = SEEK_SET;
    switch (origin) {
        case FileSeekOrigin::Start:
            stdorigin = SEEK_SET;
            break;
        case FileSeekOrigin::Current:
            stdorigin = SEEK_CUR;
            break;
        case FileSeekOrigin::End:
            stdorigin = SEEK_END;
            break;
    }
    return fseeko(reinterpret_cast<FILE*>(file), offset, stdorigin) == 0;
}

void FileRewind(FileHandle* file) {
    rewind(reinterpret_cast<FILE*>(file));
}

u64 FileRead(void* data, u64 size, u64 count, FileHandle* file) {
    return fread(data, static_cast<size_t>(size), static_cast<size_t>(count),
                 reinterpret_cast<FILE*>(file));
}

bool FileFlush(FileHandle* file) {
    return fflush(reinterpret_cast<FILE*>(file)) == 0;
}

u64 FileWrite(const void* data, u64 size, u64 count, FileHandle* file) {
    return fwrite(data, static_cast<size_t>(size), static_cast<size_t>(count),
                  reinterpret_cast<FILE*>(file));
}

u64 FileWriteFormatted(FileHandle* file, const char* fmt, ...) {
    if (fmt == nullptr) {
        return 0;
    }
    va_list args;
    va_start(args, fmt);
    const int written = vfprintf(reinterpret_cast<FILE*>(file), fmt, args);
    va_end(args);
    return written < 0 ? 0 : static_cast<u64>(written);
}

u64 FileLength(FileHandle* file) {
    FILE* stdfile = reinterpret_cast<FILE*>(file);
    const off_t pos = ftello(stdfile);
    if (pos < 0) {
        return 0;
    }
    if (fseeko(stdfile, 0, SEEK_END) != 0) {
        return 0;
    }
    const off_t len = ftello(stdfile);
    fseeko(stdfile, pos, SEEK_SET);
    return len < 0 ? 0 : static_cast<u64>(len);
}

void Log(LogLevel level, const char* fmt, ...) {
    if (fmt == nullptr) {
        return;
    }
    android_LogPriority priority = ANDROID_LOG_INFO;
    switch (level) {
        case LogLevel::Debug:
            priority = ANDROID_LOG_DEBUG;
            break;
        case LogLevel::Info:
            priority = ANDROID_LOG_INFO;
            break;
        case LogLevel::Warn:
            priority = ANDROID_LOG_WARN;
            break;
        case LogLevel::Error:
            priority = ANDROID_LOG_ERROR;
            break;
    }
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(priority, kTag, fmt, args);
    va_end(args);
}

Thread* Thread_Create(std::function<void()> func) {
    auto* state = new ThreadState();
    state->thread = std::thread(std::move(func));
    return reinterpret_cast<Thread*>(state);
}

void Thread_Free(Thread* thread) {
    auto* state = reinterpret_cast<ThreadState*>(thread);
    if (state->thread.joinable()) {
        state->thread.detach();
    }
    delete state;
}

void Thread_Wait(Thread* thread) {
    auto* state = reinterpret_cast<ThreadState*>(thread);
    if (state->thread.joinable()) {
        state->thread.join();
    }
}

Semaphore* Semaphore_Create() {
    return reinterpret_cast<Semaphore*>(new SemaphoreState());
}

void Semaphore_Free(Semaphore* sema) {
    delete reinterpret_cast<SemaphoreState*>(sema);
}

void Semaphore_Reset(Semaphore* sema) {
    auto* state = reinterpret_cast<SemaphoreState*>(sema);
    std::lock_guard<std::mutex> lock(state->mutex);
    state->count = 0;
}

void Semaphore_Wait(Semaphore* sema) {
    auto* state = reinterpret_cast<SemaphoreState*>(sema);
    std::unique_lock<std::mutex> lock(state->mutex);
    state->cv.wait(lock, [&] { return state->count > 0; });
    --state->count;
}

bool Semaphore_TryWait(Semaphore* sema, int timeout_ms) {
    auto* state = reinterpret_cast<SemaphoreState*>(sema);
    std::unique_lock<std::mutex> lock(state->mutex);
    if (timeout_ms <= 0) {
        if (state->count <= 0) {
            return false;
        }
        --state->count;
        return true;
    }
    const bool ready = state->cv.wait_for(
        lock,
        std::chrono::milliseconds(timeout_ms),
        [&] { return state->count > 0; });
    if (!ready) {
        return false;
    }
    --state->count;
    return true;
}

void Semaphore_Post(Semaphore* sema, int count) {
    auto* state = reinterpret_cast<SemaphoreState*>(sema);
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        state->count += count;
    }
    state->cv.notify_all();
}

Mutex* Mutex_Create() {
    return reinterpret_cast<Mutex*>(new MutexState());
}

void Mutex_Free(Mutex* mutex) {
    delete reinterpret_cast<MutexState*>(mutex);
}

void Mutex_Lock(Mutex* mutex) {
    reinterpret_cast<MutexState*>(mutex)->mutex.lock();
}

void Mutex_Unlock(Mutex* mutex) {
    reinterpret_cast<MutexState*>(mutex)->mutex.unlock();
}

bool Mutex_TryLock(Mutex* mutex) {
    return reinterpret_cast<MutexState*>(mutex)->mutex.try_lock();
}

void Sleep(u64 usecs) {
    std::this_thread::sleep_for(std::chrono::microseconds(usecs));
}

u64 GetMSCount() {
    using clock = std::chrono::steady_clock;
    return static_cast<u64>(
        std::chrono::duration_cast<std::chrono::milliseconds>(clock::now().time_since_epoch())
            .count());
}

u64 GetUSCount() {
    using clock = std::chrono::steady_clock;
    return static_cast<u64>(
        std::chrono::duration_cast<std::chrono::microseconds>(clock::now().time_since_epoch())
            .count());
}

void WriteNDSSave(const u8*, u32, u32, u32, void*) {}

void WriteGBASave(const u8*, u32, u32, u32, void*) {}

void WriteFirmware(const Firmware&, u32, u32, void*) {}

void WriteDateTime(int, int, int, int, int, int, void*) {}

void MP_Begin(void*) {}

void MP_End(void*) {}

int MP_SendPacket(u8*, int, u64, void*) {
    return 0;
}

int MP_RecvPacket(u8*, u64*, void*) {
    return 0;
}

int MP_SendCmd(u8*, int, u64, void*) {
    return 0;
}

int MP_SendReply(u8*, int, u64, u16, void*) {
    return 0;
}

int MP_SendAck(u8*, int, u64, void*) {
    return 0;
}

int MP_RecvHostPacket(u8*, u64*, void*) {
    return 0;
}

u16 MP_RecvReplies(u8*, u64, u16, void*) {
    return 0;
}

int Net_SendPacket(u8*, int, void*) {
    return 0;
}

int Net_RecvPacket(u8*, void*) {
    return 0;
}

void Camera_Start(int, void*) {}

void Camera_Stop(int, void*) {}

void Camera_CaptureFrame(int, u32* frame, int width, int height, bool, void*) {
    if (frame == nullptr || width <= 0 || height <= 0) {
        return;
    }
    std::memset(frame, 0, static_cast<size_t>(width) * static_cast<size_t>(height) * sizeof(u32));
}

bool Addon_KeyDown(KeyType, void*) {
    return false;
}

void Addon_RumbleStart(u32, void*) {}

void Addon_RumbleStop(void*) {}

float Addon_MotionQuery(MotionQueryType, void*) {
    return 0.0f;
}

DynamicLibrary* DynamicLibrary_Load(const char* lib) {
    return reinterpret_cast<DynamicLibrary*>(dlopen(lib, RTLD_NOW));
}

void DynamicLibrary_Unload(DynamicLibrary* lib) {
    if (lib != nullptr) {
        dlclose(lib);
    }
}

void* DynamicLibrary_LoadFunction(DynamicLibrary* lib, const char* name) {
    return dlsym(lib, name);
}

}  // namespace melonDS::Platform
