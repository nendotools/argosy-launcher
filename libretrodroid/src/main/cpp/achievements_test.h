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

#ifndef LIBRETRODROID_ACHIEVEMENTS_TEST_H
#define LIBRETRODROID_ACHIEVEMENTS_TEST_H

#include <vector>
#include <cstdint>
#include <string>
#include <functional>

namespace libretrodroid {
namespace test {

struct TestMemory {
    std::vector<uint8_t> ram;

    explicit TestMemory(size_t size) : ram(size, 0) {}

    void write8(uint32_t addr, uint8_t val) {
        if (addr < ram.size()) ram[addr] = val;
    }

    void write16(uint32_t addr, uint16_t val) {
        write8(addr, val & 0xFF);
        write8(addr + 1, (val >> 8) & 0xFF);
    }

    void write32(uint32_t addr, uint32_t val) {
        write16(addr, val & 0xFFFF);
        write16(addr + 2, (val >> 16) & 0xFFFF);
    }

    uint32_t peek(uint32_t addr, uint32_t numBytes) const;
};

struct AchievementTestCase {
    std::string name;
    std::string memAddr;
    std::function<void(TestMemory&)> setup;
    std::function<void(TestMemory&)> trigger;
    bool expectTrigger;
};

struct TestResult {
    std::string name;
    bool passed;
    std::string details;
};

class AchievementTester {
public:
    TestResult runTest(const AchievementTestCase& test);
    std::vector<TestResult> runAllTests();

    static std::vector<AchievementTestCase> getStandardTestCases();

private:
    static TestMemory* activeMemory;
    static uint32_t testPeekCallback(uint32_t addr, uint32_t numBytes, void* ud);
};

}
}

#endif //LIBRETRODROID_ACHIEVEMENTS_TEST_H
