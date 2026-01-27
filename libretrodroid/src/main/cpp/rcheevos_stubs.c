/*
 * Stub implementations for rcheevos hash functions.
 * rc_libretro.c references these but we only use memory mapping, not hashing.
 */

#include <stddef.h>
#include <stdint.h>

const char* rc_hash_iterator_error;

int rc_path_compare_extension(const char* path, const char* ext) {
    return 0;
}

void rc_hash_init_custom_cdreader(void* reader) {
}

void rc_hash_init_custom_filereader(void* reader) {
}

int rc_hash_generate_from_buffer(char* hash, uint32_t console_id, const uint8_t* buffer, size_t size) {
    return 0;
}

int rc_hash_generate_from_file(char* hash, uint32_t console_id, const char* path) {
    return 0;
}

void rc_hash_initialize_iterator(void* iterator, const char* path, void* filereader, uint32_t console_id) {
}

int rc_hash_iterate(char* hash, void* iterator) {
    return 0;
}

void rc_hash_destroy_iterator(void* iterator) {
}

void rc_hash_reset_iterator(void* iterator) {
}
