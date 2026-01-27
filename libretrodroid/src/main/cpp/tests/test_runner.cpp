#include "achievements_test.h"
#include <cstdlib>

int main() {
    libretrodroid::test::AchievementTester tester;
    auto results = tester.runAllTests();

    int passed = 0;
    int failed = 0;
    for (const auto& r : results) {
        if (r.passed) passed++;
        else failed++;
    }

    return (failed == 0) ? EXIT_SUCCESS : EXIT_FAILURE;
}
