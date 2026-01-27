/*
 *     Copyright (C) 2024  Argosy
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "achievements.h"
#include "libretrodroid.h"
#include "log.h"

#include <rc_runtime.h>
#include <rc_runtime_types.h>

namespace libretrodroid {

static Core* g_core = nullptr;

static void getCoreMemoryInfo(uint32_t id, rc_libretro_core_memory_info_t* info) {
    if (!g_core || !info) return;
    info->data = static_cast<uint8_t*>(g_core->retro_get_memory_data(id));
    info->size = g_core->retro_get_memory_size(id);
}

void Achievements::init(const std::vector<AchievementDef>& achievements) {
    clear();

    if (achievements.empty()) {
        LOGD("No achievements to initialize");
        return;
    }

    runtime = new rc_runtime_t;
    rc_runtime_init(static_cast<rc_runtime_t*>(runtime));

    int activated = 0;
    for (const auto& ach : achievements) {
        int result = rc_runtime_activate_achievement(
            static_cast<rc_runtime_t*>(runtime),
            ach.id,
            ach.memAddr.c_str(),
            nullptr,
            0
        );

        if (result == RC_OK) {
            activated++;
        } else {
            LOGW("Failed to activate achievement %u: error %d", ach.id, result);
        }
    }

    active = activated > 0;
    LOGI("Achievements initialized: %d/%zu activated", activated, achievements.size());

    // Debug: dump trigger states after init
    auto* rt = static_cast<rc_runtime_t*>(runtime);
    LOGI("Runtime has %u triggers registered", rt->trigger_count);
    for (uint32_t i = 0; i < std::min(rt->trigger_count, 5u); i++) {
        auto& trigger = rt->triggers[i];
        if (trigger.trigger) {
            LOGI("  Trigger %u (id=%u): state=%d", i, trigger.id, trigger.trigger->state);
        }
    }
}

void Achievements::initMemory(uint32_t consoleId, const struct retro_memory_map* mmap) {
    this->consoleId = consoleId;

    if (memoryInitialized) {
        rc_libretro_memory_destroy(&memoryRegions);
        memoryInitialized = false;
    }

    int result = rc_libretro_memory_init(
        &memoryRegions,
        mmap,
        getCoreMemoryInfo,
        consoleId
    );

    memoryInitialized = (result == 1);

    if (memoryInitialized) {
        LOGI("Achievement memory initialized for console %u with %u regions",
             consoleId, memoryRegions.count);
    } else {
        LOGW("Failed to initialize achievement memory mapping, falling back to direct RAM");
    }
}

static int frameCounter = 0;
static bool firstEvalLogged = false;

void Achievements::evaluateFrame() {
    if (!active || !runtime || !g_core) return;

    auto* rt = static_cast<rc_runtime_t*>(runtime);

    if (!firstEvalLogged) {
        LOGI("Achievement evaluation started - runtime active");
        // Log first trigger state before evaluation
        if (rt->trigger_count > 0 && rt->triggers[0].trigger) {
            LOGI("  First trigger state before eval: %d", rt->triggers[0].trigger->state);
        }
        firstEvalLogged = true;
    }

    frameCounter++;
    if (frameCounter % 3600 == 0) {  // Log every ~60 seconds at 60fps
        LOGI("Achievement evaluation: %d frames, memInitialized=%d", frameCounter, memoryInitialized ? 1 : 0);
    }

    triggeredIds.clear();

    rc_runtime_do_frame(
        static_cast<rc_runtime_t*>(runtime),
        [](const rc_runtime_event_t* event) {
            // Log all event types for debugging
            if (event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_TRIGGERED) {
                LOGI("Achievement TRIGGERED: %u", event->id);
                auto& ach = LibretroDroid::getInstance().getAchievements();
                ach.queueUnlock(event->id);
                ach.markTriggered(event->id);
            } else if (event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_ACTIVATED) {
                LOGI("Achievement activated: %u", event->id);
            } else if (event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_PAUSED) {
                LOGI("Achievement paused: %u", event->id);
            } else if (event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_PRIMED) {
                LOGI("Achievement PRIMED: %u", event->id);
            } else if (event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_PROGRESS_UPDATED) {
                LOGI("Achievement progress updated: %u", event->id);
            } else {
                LOGI("Achievement event type %d for id %u", event->type, event->id);
            }
        },
        &Achievements::peekMemory,
        nullptr,
        nullptr
    );

    for (uint32_t id : triggeredIds) {
        rc_runtime_deactivate_achievement(rt, id);
        LOGD("Deactivated achievement %u to prevent re-triggering", id);
    }
}

static int peekLogCounter = 0;

uint32_t Achievements::peekMemory(uint32_t address, uint32_t numBytes, void* userData) {
    auto& ach = LibretroDroid::getInstance().getAchievements();

    if (ach.memoryInitialized) {
        uint8_t buffer[4] = {0};
        uint32_t bytesRead = rc_libretro_memory_read(&ach.memoryRegions, address, buffer, numBytes);

        // Log first few peeks to verify memory reading works
        if (peekLogCounter < 5) {
            peekLogCounter++;
            uint32_t value = 0;
            for (uint32_t i = 0; i < bytesRead; i++) {
                value |= static_cast<uint32_t>(buffer[i]) << (i * 8);
            }
            LOGI("Memory peek: addr=0x%08X, bytes=%u, read=%u, value=0x%X", address, numBytes, bytesRead, value);
        }

        if (bytesRead == 0) return 0;

        uint32_t value = 0;
        for (uint32_t i = 0; i < numBytes; i++) {
            value |= static_cast<uint32_t>(buffer[i]) << (i * 8);
        }
        return value;
    }

    if (!g_core) return 0;

    void* memPtr = g_core->retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM);
    size_t memSize = g_core->retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM);

    if (!memPtr || address + numBytes > memSize) {
        return 0;
    }

    auto* data = static_cast<uint8_t*>(memPtr);
    uint32_t value = 0;

    for (uint32_t i = 0; i < numBytes; i++) {
        value |= static_cast<uint32_t>(data[address + i]) << (i * 8);
    }

    return value;
}

void Achievements::queueUnlock(uint32_t id) {
    std::lock_guard<std::mutex> lock(unlockMutex);
    pendingUnlocks.push(id);
}

void Achievements::markTriggered(uint32_t id) {
    triggeredIds.push_back(id);
}

void Achievements::handleUnlocks(const std::function<void(uint32_t)>& handler) {
    std::lock_guard<std::mutex> lock(unlockMutex);

    while (!pendingUnlocks.empty()) {
        uint32_t id = pendingUnlocks.front();
        pendingUnlocks.pop();
        handler(id);
    }
}

void Achievements::clear() {
    if (runtime) {
        rc_runtime_destroy(static_cast<rc_runtime_t*>(runtime));
        delete static_cast<rc_runtime_t*>(runtime);
        runtime = nullptr;
    }
    active = false;
    triggeredIds.clear();

    if (memoryInitialized) {
        rc_libretro_memory_destroy(&memoryRegions);
        memoryInitialized = false;
    }
    consoleId = 0;

    std::lock_guard<std::mutex> lock(unlockMutex);
    while (!pendingUnlocks.empty()) {
        pendingUnlocks.pop();
    }

    LOGD("Achievements cleared");
}

void Achievements::setCore(Core* core) {
    g_core = core;
}

}
