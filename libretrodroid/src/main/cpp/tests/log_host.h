#ifndef LIBRETRODROID_LOG_HOST_H
#define LIBRETRODROID_LOG_HOST_H

#include <cstdio>

#define MODULE_NAME "libretrodroid"

#define LOGV(...)
#define LOGD(...)
#define LOGI(...) do { printf("[INFO] "); printf(__VA_ARGS__); printf("\n"); } while(0)
#define LOGW(...) do { printf("[WARN] "); printf(__VA_ARGS__); printf("\n"); } while(0)
#define LOGE(...) do { printf("[ERROR] "); printf(__VA_ARGS__); printf("\n"); } while(0)
#define LOGF(...) do { printf("[FATAL] "); printf(__VA_ARGS__); printf("\n"); } while(0)

#endif
