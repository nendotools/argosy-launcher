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

#ifndef LIBRETRODROID_ACHIEVEMENTS_H
#define LIBRETRODROID_ACHIEVEMENTS_H

#include <string>
#include <vector>
#include <queue>
#include <functional>
#include <mutex>
#include <cstdint>

#include <rc_libretro.h>

namespace libretrodroid {

class Core;

struct AchievementDef {
    uint32_t id;
    std::string memAddr;
};

class Achievements {
public:
    void init(const std::vector<AchievementDef>& achievements);
    void initMemory(uint32_t consoleId, const struct retro_memory_map* mmap);
    void evaluateFrame();
    void clear();
    void handleUnlocks(const std::function<void(uint32_t)>& handler);
    void queueUnlock(uint32_t id);
    void markTriggered(uint32_t id);
    bool isActive() const { return active; }

    static void setCore(Core* core);

private:
    static uint32_t peekMemory(uint32_t address, uint32_t numBytes, void* userData);

    void* runtime = nullptr;
    bool active = false;
    std::queue<uint32_t> pendingUnlocks;
    std::mutex unlockMutex;
    std::vector<uint32_t> triggeredIds;

    rc_libretro_memory_regions_t memoryRegions = {};
    bool memoryInitialized = false;
    uint32_t consoleId = 0;
};

}

#endif //LIBRETRODROID_ACHIEVEMENTS_H
