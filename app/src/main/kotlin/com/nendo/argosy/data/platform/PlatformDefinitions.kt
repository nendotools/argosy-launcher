package com.nendo.argosy.data.platform

import com.nendo.argosy.data.local.entity.PlatformEntity

data class PlatformDef(
    val id: String,
    val name: String,
    val shortName: String,
    val extensions: Set<String>,
    val sortOrder: Int
)

object PlatformDefinitions {

    private val platforms = listOf(
        PlatformDef("nes", "Nintendo Entertainment System", "NES", setOf("nes", "unf", "unif", "zip", "7z", "chd"), 1),
        PlatformDef("snes", "Super Nintendo", "SNES", setOf("sfc", "smc", "fig", "swc", "zip", "7z", "chd"), 2),
        PlatformDef("n64", "Nintendo 64", "N64", setOf("n64", "z64", "v64", "zip", "7z", "chd"), 3),
        PlatformDef("gc", "GameCube", "GameCube", setOf("iso", "gcm", "gcz", "rvz", "ciso", "zip", "7z", "chd"), 4),
        PlatformDef("wii", "Nintendo Wii", "Wii", setOf("wbfs", "iso", "rvz", "gcz", "zip", "7z", "chd"), 5),
        PlatformDef("gb", "Game Boy", "Game Boy", setOf("gb", "zip", "7z", "chd"), 6),
        PlatformDef("gbc", "Game Boy Color", "Game Boy Color", setOf("gbc", "zip", "7z", "chd"), 7),
        PlatformDef("gba", "Game Boy Advance", "Game Boy Advance", setOf("gba", "zip", "7z", "chd"), 8),
        PlatformDef("nds", "Nintendo DS", "NDS", setOf("nds", "dsi", "zip", "7z", "chd"), 9),
        PlatformDef("3ds", "Nintendo 3DS", "3DS", setOf("3ds", "cia", "cxi", "app", "zip", "7z", "chd"), 10),
        PlatformDef("switch", "Nintendo Switch", "Switch", setOf("nsp", "xci", "nsz", "xcz", "zip", "7z", "chd"), 11),
        PlatformDef("wiiu", "Nintendo Wii U", "Wii U", setOf("wud", "wux", "rpx", "wua", "zip", "7z", "chd"), 12),

        PlatformDef("sms", "Sega Master System", "SMS", setOf("sms", "sg", "zip", "7z", "chd"), 20),
        PlatformDef("genesis", "Sega Genesis", "Genesis", setOf("md", "gen", "smd", "bin", "zip", "7z", "chd"), 21),
        PlatformDef("scd", "Sega CD", "Sega CD", setOf("iso", "bin", "chd", "zip", "7z"), 22),
        PlatformDef("32x", "Sega 32X", "32X", setOf("32x", "zip", "7z", "chd"), 23),
        PlatformDef("saturn", "Sega Saturn", "Saturn", setOf("iso", "bin", "cue", "chd", "zip", "7z"), 24),
        PlatformDef("dreamcast", "Sega Dreamcast", "Dreamcast", setOf("gdi", "cdi", "chd", "zip", "7z"), 25),
        PlatformDef("gg", "Sega Game Gear", "GG", setOf("gg", "zip", "7z", "chd"), 26),

        PlatformDef("psx", "Sony PlayStation", "PS1", setOf("bin", "iso", "img", "chd", "pbp", "cue", "zip", "7z"), 30),
        PlatformDef("ps2", "Sony PlayStation 2", "PS2", setOf("iso", "bin", "chd", "gz", "cso", "zip", "7z"), 31),
        PlatformDef("psp", "Sony PlayStation Portable", "PSP", setOf("iso", "cso", "pbp", "zip", "7z", "chd"), 32),
        PlatformDef("vita", "Sony PlayStation Vita", "Vita", setOf("vpk", "mai", "zip", "7z", "chd"), 33),

        PlatformDef("tg16", "TurboGrafx-16", "TG16", setOf("pce", "zip", "7z", "chd"), 40),
        PlatformDef("tgcd", "TurboGrafx-CD", "TG-CD", setOf("chd", "cue", "ccd", "zip", "7z"), 41),
        PlatformDef("pcfx", "PC-FX", "PC-FX", setOf("chd", "cue", "ccd", "zip", "7z"), 42),

        PlatformDef("ngp", "Neo Geo Pocket", "NGP", setOf("ngp", "ngc", "zip", "7z", "chd"), 50),
        PlatformDef("ngpc", "Neo Geo Pocket Color", "NGPC", setOf("ngpc", "ngc", "zip", "7z", "chd"), 51),
        PlatformDef("neogeo", "Neo Geo", "Neo Geo", setOf("zip", "7z", "chd"), 52),

        PlatformDef("atari2600", "Atari 2600", "2600", setOf("a26", "bin", "zip", "7z", "chd"), 60),
        PlatformDef("atari5200", "Atari 5200", "5200", setOf("a52", "bin", "zip", "7z", "chd"), 61),
        PlatformDef("atari7800", "Atari 7800", "7800", setOf("a78", "bin", "zip", "7z", "chd"), 62),
        PlatformDef("lynx", "Atari Lynx", "Lynx", setOf("lnx", "zip", "7z", "chd"), 63),
        PlatformDef("jaguar", "Atari Jaguar", "Jaguar", setOf("j64", "jag", "zip", "7z", "chd"), 64),

        PlatformDef("msx", "MSX", "MSX", setOf("rom", "mx1", "mx2", "zip", "7z", "chd"), 70),
        PlatformDef("msx2", "MSX2", "MSX2", setOf("rom", "mx2", "zip", "7z", "chd"), 71),

        PlatformDef("arcade", "Arcade", "Arcade", setOf("zip", "7z", "chd"), 80),

        PlatformDef("dos", "DOS", "DOS", setOf("exe", "com", "bat", "zip", "7z", "chd"), 90),
        PlatformDef("scummvm", "ScummVM", "ScummVM", setOf("scummvm", "zip", "7z", "chd"), 91),

        PlatformDef("wonderswan", "WonderSwan", "WS", setOf("ws", "zip", "7z", "chd"), 100),
        PlatformDef("wonderswancolor", "WonderSwan Color", "WSC", setOf("wsc", "zip", "7z", "chd"), 101),

        PlatformDef("vectrex", "Vectrex", "Vectrex", setOf("vec", "zip", "7z", "chd"), 110),
        PlatformDef("coleco", "ColecoVision", "Coleco", setOf("col", "zip", "7z", "chd"), 111),
        PlatformDef("intellivision", "Intellivision", "Intv", setOf("int", "bin", "zip", "7z", "chd"), 112),

        PlatformDef("steam", "Steam", "Steam", emptySet(), 130),
    )

    private val platformMap = platforms.associateBy { it.id }
    private val extensionMap: Map<String, List<PlatformDef>>

    init {
        val extMap = mutableMapOf<String, MutableList<PlatformDef>>()
        platforms.forEach { platform ->
            platform.extensions.forEach { ext ->
                extMap.getOrPut(ext.lowercase()) { mutableListOf() }.add(platform)
            }
        }
        extensionMap = extMap
    }

    fun getAll(): List<PlatformDef> = platforms

    fun getById(id: String): PlatformDef? = platformMap[id]

    fun getPlatformsForExtension(extension: String): List<PlatformDef> =
        extensionMap[extension.lowercase()] ?: emptyList()

    fun toEntity(def: PlatformDef) = PlatformEntity(
        id = def.id,
        name = def.name,
        shortName = def.shortName,
        sortOrder = def.sortOrder,
        romExtensions = def.extensions.joinToString(","),
        isVisible = true
    )

    fun toEntities(): List<PlatformEntity> = platforms.map { toEntity(it) }
}
