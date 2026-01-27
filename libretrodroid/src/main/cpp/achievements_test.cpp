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

#include "achievements_test.h"

#ifdef HOST_BUILD
#include "tests/log_host.h"
#else
#include "log.h"
#endif

#include <rc_runtime.h>

namespace libretrodroid {
namespace test {

TestMemory* AchievementTester::activeMemory = nullptr;
static bool g_testTriggered = false;

uint32_t TestMemory::peek(uint32_t addr, uint32_t numBytes) const {
    if (addr + numBytes > ram.size()) return 0;
    uint32_t value = 0;
    for (uint32_t i = 0; i < numBytes; i++) {
        value |= static_cast<uint32_t>(ram[addr + i]) << (i * 8);
    }
    return value;
}

uint32_t AchievementTester::testPeekCallback(uint32_t addr, uint32_t numBytes, void* ud) {
    if (!activeMemory) return 0;
    return activeMemory->peek(addr, numBytes);
}

static void testEventCallback(const rc_runtime_event_t* event) {
    if (event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_TRIGGERED) {
        g_testTriggered = true;
    }
}

TestResult AchievementTester::runTest(const AchievementTestCase& test) {
    TestResult result;
    result.name = test.name;
    result.passed = false;

    LOGI("Running test: %s", test.name.c_str());

    TestMemory mem(0x10000);
    activeMemory = &mem;
    g_testTriggered = false;

    rc_runtime_t runtime;
    rc_runtime_init(&runtime);

    int parseResult = rc_runtime_activate_achievement(&runtime, 1, test.memAddr.c_str(), nullptr, 0);
    if (parseResult != RC_OK) {
        result.details = "Failed to parse condition: error " + std::to_string(parseResult);
        LOGE("  FAIL: %s", result.details.c_str());
        rc_runtime_destroy(&runtime);
        activeMemory = nullptr;
        return result;
    }

    if (test.setup) test.setup(mem);

    for (int i = 0; i < 5; i++) {
        rc_runtime_do_frame(&runtime, testEventCallback, testPeekCallback, nullptr, nullptr);
    }

    if (test.trigger) test.trigger(mem);

    rc_runtime_do_frame(&runtime, testEventCallback, testPeekCallback, nullptr, nullptr);

    bool triggered = g_testTriggered;

    rc_runtime_destroy(&runtime);
    activeMemory = nullptr;

    result.passed = (triggered == test.expectTrigger);
    result.details = "expected=" + std::string(test.expectTrigger ? "trigger" : "no-trigger") +
                     ", got=" + std::string(triggered ? "trigger" : "no-trigger");

    if (result.passed) {
        LOGI("  PASS: %s", result.details.c_str());
    } else {
        LOGE("  FAIL: %s", result.details.c_str());
    }

    return result;
}

std::vector<AchievementTestCase> AchievementTester::getStandardTestCases() {
    return {
        // =====================================================================
        // BASIC MEMORY READ TESTS - Verify correct byte sizes and endianness
        // =====================================================================
        {
            "8-bit read - basic",
            "0xH0001=5",
            [](TestMemory& m) { m.write8(0x0001, 0); },
            [](TestMemory& m) { m.write8(0x0001, 5); },
            true
        },
        {
            "8-bit read - max value 255",
            "0xH0002=255",
            [](TestMemory& m) { m.write8(0x0002, 0); },
            [](TestMemory& m) { m.write8(0x0002, 255); },
            true
        },
        {
            "8-bit read - zero value",
            "0xH0003=0",
            [](TestMemory& m) { m.write8(0x0003, 99); },
            [](TestMemory& m) { m.write8(0x0003, 0); },
            true
        },
        {
            "16-bit read - basic",
            "0x 0010=1000",
            [](TestMemory& m) { m.write16(0x0010, 0); },
            [](TestMemory& m) { m.write16(0x0010, 1000); },
            true
        },
        {
            "16-bit read - max value 65535",
            "0x 0012=65535",
            [](TestMemory& m) { m.write16(0x0012, 0); },
            [](TestMemory& m) { m.write16(0x0012, 65535); },
            true
        },
        {
            "16-bit read - little-endian verify",
            "0x 0014=513",  // 0x0201 = 513 decimal, stored as [0x01, 0x02]
            [](TestMemory& m) { m.write8(0x0014, 0); m.write8(0x0015, 0); },
            [](TestMemory& m) { m.write8(0x0014, 0x01); m.write8(0x0015, 0x02); },
            true
        },
        {
            "32-bit read - basic",
            "0xX0020=305419896",  // 0x12345678
            [](TestMemory& m) { m.write32(0x0020, 0); },
            [](TestMemory& m) { m.write32(0x0020, 0x12345678); },
            true
        },
        {
            "32-bit read - max value",
            "0xX0024=4294967295",  // 0xFFFFFFFF
            [](TestMemory& m) { m.write32(0x0024, 0); },
            [](TestMemory& m) { m.write32(0x0024, 0xFFFFFFFF); },
            true
        },
        {
            "32-bit read - little-endian verify",
            "0xX0028=67305985",  // 0x04030201, stored as [0x01, 0x02, 0x03, 0x04]
            [](TestMemory& m) { m.write32(0x0028, 0); },
            [](TestMemory& m) {
                m.write8(0x0028, 0x01);
                m.write8(0x0029, 0x02);
                m.write8(0x002A, 0x03);
                m.write8(0x002B, 0x04);
            },
            true
        },
        {
            "Address 0x0000 - read from start of memory",
            "0xH0000=42",
            [](TestMemory& m) { m.write8(0x0000, 0); },
            [](TestMemory& m) { m.write8(0x0000, 42); },
            true
        },

        // =====================================================================
        // COMPARISON OPERATORS - All operators with success cases
        // =====================================================================
        {
            "Equals (=) - match",
            "0xH0100=50",
            [](TestMemory& m) { m.write8(0x0100, 0); },
            [](TestMemory& m) { m.write8(0x0100, 50); },
            true
        },
        {
            "Not equals (!=) - different value",
            "0xH0101!=0",
            [](TestMemory& m) { m.write8(0x0101, 0); },
            [](TestMemory& m) { m.write8(0x0101, 1); },
            true
        },
        {
            "Less than (<) - below threshold",
            "0xH0102<100",
            [](TestMemory& m) { m.write8(0x0102, 200); },
            [](TestMemory& m) { m.write8(0x0102, 50); },
            true
        },
        {
            "Less than or equal (<=) - equal to threshold",
            "0xH0103<=100",
            [](TestMemory& m) { m.write8(0x0103, 200); },
            [](TestMemory& m) { m.write8(0x0103, 100); },
            true
        },
        {
            "Less than or equal (<=) - below threshold",
            "0xH0104<=100",
            [](TestMemory& m) { m.write8(0x0104, 200); },
            [](TestMemory& m) { m.write8(0x0104, 50); },
            true
        },
        {
            "Greater than (>) - above threshold",
            "0xH0105>100",
            [](TestMemory& m) { m.write8(0x0105, 50); },
            [](TestMemory& m) { m.write8(0x0105, 150); },
            true
        },
        {
            "Greater than or equal (>=) - equal to threshold",
            "0xH0106>=100",
            [](TestMemory& m) { m.write8(0x0106, 50); },
            [](TestMemory& m) { m.write8(0x0106, 100); },
            true
        },
        {
            "Greater than or equal (>=) - above threshold",
            "0xH0107>=100",
            [](TestMemory& m) { m.write8(0x0107, 50); },
            [](TestMemory& m) { m.write8(0x0107, 150); },
            true
        },

        // =====================================================================
        // FAILURE CASES - Conditions that should NOT trigger
        // =====================================================================
        {
            "FAIL: Equals - off by one (too low)",
            "0xH0200=100",
            [](TestMemory& m) { m.write8(0x0200, 0); },
            [](TestMemory& m) { m.write8(0x0200, 99); },
            false
        },
        {
            "FAIL: Equals - off by one (too high)",
            "0xH0201=100",
            [](TestMemory& m) { m.write8(0x0201, 0); },
            [](TestMemory& m) { m.write8(0x0201, 101); },
            false
        },
        {
            "FAIL: Not equals - same value",
            "0xH0202!=50",
            [](TestMemory& m) { m.write8(0x0202, 0); },
            [](TestMemory& m) { m.write8(0x0202, 50); },
            false
        },
        {
            "FAIL: Less than - equal (boundary)",
            "0xH0203<100",
            [](TestMemory& m) { m.write8(0x0203, 50); },
            [](TestMemory& m) { m.write8(0x0203, 100); },
            false
        },
        {
            "FAIL: Less than - above threshold",
            "0xH0204<100",
            [](TestMemory& m) { m.write8(0x0204, 50); },
            [](TestMemory& m) { m.write8(0x0204, 150); },
            false
        },
        {
            "FAIL: Greater than - equal (boundary)",
            "0xH0205>100",
            [](TestMemory& m) { m.write8(0x0205, 150); },
            [](TestMemory& m) { m.write8(0x0205, 100); },
            false
        },
        {
            "FAIL: Greater than - below threshold",
            "0xH0206>100",
            [](TestMemory& m) { m.write8(0x0206, 150); },
            [](TestMemory& m) { m.write8(0x0206, 50); },
            false
        },
        {
            "FAIL: 16-bit partial match - only low byte correct",
            "0x 0210=1000",  // 1000 = 0x03E8
            [](TestMemory& m) { m.write16(0x0210, 0); },
            [](TestMemory& m) { m.write8(0x0210, 0xE8); m.write8(0x0211, 0x00); },  // Low byte only
            false
        },
        {
            "FAIL: 32-bit partial match - only 3 bytes correct",
            "0xX0220=305419896",  // 0x12345678
            [](TestMemory& m) { m.write32(0x0220, 0); },
            [](TestMemory& m) {
                m.write8(0x0220, 0x78);
                m.write8(0x0221, 0x56);
                m.write8(0x0222, 0x34);
                m.write8(0x0223, 0x00);  // Wrong high byte
            },
            false
        },

        // =====================================================================
        // DELTA/PRIOR VALUE TESTS - Track changes between frames
        // =====================================================================
        {
            "Delta - value increased",
            "0xH0300>d0xH0300",
            [](TestMemory& m) { m.write8(0x0300, 10); },
            [](TestMemory& m) { m.write8(0x0300, 11); },
            true
        },
        {
            "Delta - value decreased",
            "0xH0301<d0xH0301",
            [](TestMemory& m) { m.write8(0x0301, 100); },
            [](TestMemory& m) { m.write8(0x0301, 50); },
            true
        },
        {
            "FAIL: Delta - value unchanged",
            "0xH0302>d0xH0302",
            [](TestMemory& m) { m.write8(0x0302, 50); },
            [](TestMemory& m) { m.write8(0x0302, 50); },  // Same value
            false
        },
        {
            "FAIL: Delta - value decreased when expecting increase",
            "0xH0303>d0xH0303",
            [](TestMemory& m) { m.write8(0x0303, 100); },
            [](TestMemory& m) { m.write8(0x0303, 50); },
            false
        },
        {
            "Delta equals - value changed to specific",
            "d0xH0304=10_0xH0304=20",  // Was 10, now 20
            [](TestMemory& m) { m.write8(0x0304, 10); },
            [](TestMemory& m) { m.write8(0x0304, 20); },
            true
        },

        // =====================================================================
        // COMPOUND CONDITIONS - AND/OR logic
        // =====================================================================
        {
            "AND - both conditions true",
            "0xH0400=1_0xH0401=2",
            [](TestMemory& m) { m.write8(0x0400, 0); m.write8(0x0401, 0); },
            [](TestMemory& m) { m.write8(0x0400, 1); m.write8(0x0401, 2); },
            true
        },
        {
            "FAIL: AND - first condition false",
            "0xH0402=1_0xH0403=2",
            [](TestMemory& m) { m.write8(0x0402, 0); m.write8(0x0403, 0); },
            [](TestMemory& m) { m.write8(0x0402, 99); m.write8(0x0403, 2); },
            false
        },
        {
            "FAIL: AND - second condition false",
            "0xH0404=1_0xH0405=2",
            [](TestMemory& m) { m.write8(0x0404, 0); m.write8(0x0405, 0); },
            [](TestMemory& m) { m.write8(0x0404, 1); m.write8(0x0405, 99); },
            false
        },
        {
            "FAIL: AND - both conditions false",
            "0xH0406=1_0xH0407=2",
            [](TestMemory& m) { m.write8(0x0406, 0); m.write8(0x0407, 0); },
            [](TestMemory& m) { m.write8(0x0406, 99); m.write8(0x0407, 99); },
            false
        },
        {
            "AND - three conditions all true",
            "0xH0408=1_0xH0409=2_0xH040A=3",
            [](TestMemory& m) { m.write8(0x0408, 0); m.write8(0x0409, 0); m.write8(0x040A, 0); },
            [](TestMemory& m) { m.write8(0x0408, 1); m.write8(0x0409, 2); m.write8(0x040A, 3); },
            true
        },
        {
            "OR - first alt group true",
            "S0xH0410=1S0xH0411=2",
            [](TestMemory& m) { m.write8(0x0410, 0); m.write8(0x0411, 0); },
            [](TestMemory& m) { m.write8(0x0410, 1); },
            true
        },
        {
            "OR - second alt group true",
            "S0xH0412=1S0xH0413=2",
            [](TestMemory& m) { m.write8(0x0412, 0); m.write8(0x0413, 0); },
            [](TestMemory& m) { m.write8(0x0413, 2); },
            true
        },
        {
            "OR - both alt groups true",
            "S0xH0414=1S0xH0415=2",
            [](TestMemory& m) { m.write8(0x0414, 0); m.write8(0x0415, 0); },
            [](TestMemory& m) { m.write8(0x0414, 1); m.write8(0x0415, 2); },
            true
        },
        {
            "FAIL: OR - neither alt group true",
            "S0xH0416=1S0xH0417=2",
            [](TestMemory& m) { m.write8(0x0416, 0); m.write8(0x0417, 0); },
            [](TestMemory& m) { m.write8(0x0416, 99); m.write8(0x0417, 99); },
            false
        },

        // =====================================================================
        // BIT OPERATIONS - Individual bit checks
        // =====================================================================
        {
            "Bit0 set (value & 0x01)",
            "0xM0500=1",  // Bit0
            [](TestMemory& m) { m.write8(0x0500, 0); },
            [](TestMemory& m) { m.write8(0x0500, 0x01); },
            true
        },
        {
            "Bit7 set (value & 0x80)",
            "0xT0501=1",  // Bit7 (M=0, N=1, O=2, P=3, Q=4, R=5, S=6, T=7)
            [](TestMemory& m) { m.write8(0x0501, 0); },
            [](TestMemory& m) { m.write8(0x0501, 0x80); },
            true
        },
        {
            "FAIL: Bit0 not set",
            "0xM0502=1",
            [](TestMemory& m) { m.write8(0x0502, 0); },
            [](TestMemory& m) { m.write8(0x0502, 0xFE); },  // All bits except bit0
            false
        },
        {
            "Lower nibble check",
            "0xL0503=15",  // Lower 4 bits = 0x0F
            [](TestMemory& m) { m.write8(0x0503, 0); },
            [](TestMemory& m) { m.write8(0x0503, 0xFF); },
            true
        },
        {
            "Upper nibble check",
            "0xU0504=15",  // Upper 4 bits = 0xF0 >> 4 = 15
            [](TestMemory& m) { m.write8(0x0504, 0); },
            [](TestMemory& m) { m.write8(0x0504, 0xF0); },
            true
        },

        // =====================================================================
        // MEMORY TO MEMORY COMPARISON
        // =====================================================================
        {
            "Mem-to-mem: two addresses equal",
            "0xH0600=0xH0601",
            [](TestMemory& m) { m.write8(0x0600, 0); m.write8(0x0601, 99); },
            [](TestMemory& m) { m.write8(0x0600, 42); m.write8(0x0601, 42); },
            true
        },
        {
            "FAIL: Mem-to-mem: addresses not equal",
            "0xH0602=0xH0603",
            [](TestMemory& m) { m.write8(0x0602, 0); m.write8(0x0603, 0); },
            [](TestMemory& m) { m.write8(0x0602, 10); m.write8(0x0603, 20); },
            false
        },
        {
            "Mem-to-mem: first greater than second",
            "0xH0604>0xH0605",
            [](TestMemory& m) { m.write8(0x0604, 0); m.write8(0x0605, 100); },
            [](TestMemory& m) { m.write8(0x0604, 50); m.write8(0x0605, 25); },
            true
        },

        // =====================================================================
        // EDGE CASES
        // =====================================================================
        {
            "High address read (0xFF00)",
            "0xH FF00=123",
            [](TestMemory& m) { m.write8(0xFF00, 0); },
            [](TestMemory& m) { m.write8(0xFF00, 123); },
            true
        },
        {
            "16-bit spanning two different values",
            "0x 0700=4660",  // 0x1234
            [](TestMemory& m) { m.write8(0x0700, 0); m.write8(0x0701, 0); },
            [](TestMemory& m) { m.write8(0x0700, 0x34); m.write8(0x0701, 0x12); },
            true
        },
        {
            "Value transition from max to min",
            "0xH0710=0_d0xH0710=255",  // Now 0, was 255
            [](TestMemory& m) { m.write8(0x0710, 255); },
            [](TestMemory& m) { m.write8(0x0710, 0); },
            true
        }
    };
}

std::vector<TestResult> AchievementTester::runAllTests() {
    auto tests = getStandardTestCases();
    std::vector<TestResult> results;
    int passed = 0;
    int failed = 0;

    LOGI("=== Running %zu achievement condition tests ===", tests.size());

    for (const auto& test : tests) {
        TestResult result = runTest(test);
        results.push_back(result);
        if (result.passed) {
            passed++;
        } else {
            failed++;
        }
    }

    LOGI("=== Results: %d passed, %d failed ===", passed, failed);
    return results;
}

}
}
