package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfigureEmulatorUseCaseTest {

    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var useCase: ConfigureEmulatorUseCase

    @Before
    fun setup() {
        emulatorConfigDao = mockk(relaxed = true)
        useCase = ConfigureEmulatorUseCase(emulatorConfigDao)
    }

    @Test
    fun `setForGame deletes existing override`() = runTest {
        useCase.setForGame(123L, 1L, "nes", null)

        coVerify { emulatorConfigDao.deleteGameOverride(123L) }
    }

    @Test
    fun `setForGame saves game-specific config when emulator provided`() = runTest {
        val emulator = createEmulator("com.retroarch", "RetroArch")
        val configSlot = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(configSlot)) } returns 1L

        useCase.setForGame(123L, 1L, "nes", emulator)

        assertTrue(configSlot.isCaptured)
        val config = configSlot.captured
        assertEquals(1L, config.platformId)
        assertEquals(123L, config.gameId)
        assertEquals("com.retroarch", config.packageName)
        assertEquals("RetroArch", config.displayName)
        assertFalse(config.isDefault)
    }

    @Test
    fun `setForGame with null emulator only deletes override`() = runTest {
        useCase.setForGame(123L, 1L, "nes", null)

        coVerify { emulatorConfigDao.deleteGameOverride(123L) }
        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `setForPlatform clears platform defaults`() = runTest {
        useCase.setForPlatform(1L, "nes", null)

        coVerify { emulatorConfigDao.clearPlatformDefaults(1L) }
    }

    @Test
    fun `setForPlatform saves platform default when emulator provided`() = runTest {
        val emulator = createEmulator("com.snes9x", "Snes9x")
        val configSlot = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(configSlot)) } returns 1L

        useCase.setForPlatform(2L, "snes", emulator)

        assertTrue(configSlot.isCaptured)
        val config = configSlot.captured
        assertEquals(2L, config.platformId)
        assertNull(config.gameId)
        assertEquals("com.snes9x", config.packageName)
        assertEquals("Snes9x", config.displayName)
        assertTrue(config.isDefault)
    }

    @Test
    fun `setForPlatform with null emulator only clears defaults`() = runTest {
        useCase.setForPlatform(1L, "nes", null)

        coVerify { emulatorConfigDao.clearPlatformDefaults(1L) }
        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `clearForGame deletes game override`() = runTest {
        useCase.clearForGame(456L)

        coVerify { emulatorConfigDao.deleteGameOverride(456L) }
    }

    @Test
    fun `clearForPlatform clears platform defaults`() = runTest {
        useCase.clearForPlatform(3L)

        coVerify { emulatorConfigDao.clearPlatformDefaults(3L) }
    }

    private fun createEmulator(packageName: String, displayName: String): InstalledEmulator {
        val def = EmulatorDef(
            id = packageName,
            packageName = packageName,
            displayName = displayName,
            supportedPlatforms = setOf("nes", "snes", "gba")
        )
        return InstalledEmulator(def = def, versionName = "1.0", versionCode = 1L)
    }
}
