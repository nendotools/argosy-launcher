package com.nendo.argosy.data.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantDownloadThresholdTest {

    private fun isInstantDownload(expectedSizeBytes: Long, thresholdMb: Int): Boolean {
        if (expectedSizeBytes <= 0) return false
        val thresholdBytes = thresholdMb * 1024L * 1024L
        return expectedSizeBytes <= thresholdBytes
    }

    @Test
    fun `file under threshold returns true`() {
        val threshold = 50
        val fileSizeBytes = 40 * 1024 * 1024L // 40 MB

        assertTrue(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `file over threshold returns false`() {
        val threshold = 50
        val fileSizeBytes = 60 * 1024 * 1024L // 60 MB

        assertFalse(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `file at exact threshold returns true`() {
        val threshold = 50
        val fileSizeBytes = 50 * 1024 * 1024L // Exactly 50 MB

        assertTrue(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `file with zero size returns false`() {
        val threshold = 50
        val fileSizeBytes = 0L

        assertFalse(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `file with negative size returns false`() {
        val threshold = 50
        val fileSizeBytes = -1L

        assertFalse(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `threshold of 100mb accepts 99mb file`() {
        val threshold = 100
        val fileSizeBytes = 99 * 1024 * 1024L

        assertTrue(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `threshold of 100mb rejects 101mb file`() {
        val threshold = 100
        val fileSizeBytes = 101 * 1024 * 1024L

        assertFalse(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `threshold of 250mb accepts 200mb file`() {
        val threshold = 250
        val fileSizeBytes = 200 * 1024 * 1024L

        assertTrue(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `threshold of 500mb accepts 499mb file`() {
        val threshold = 500
        val fileSizeBytes = 499 * 1024 * 1024L

        assertTrue(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `threshold of 500mb rejects 501mb file`() {
        val threshold = 500
        val fileSizeBytes = 501 * 1024 * 1024L

        assertFalse(isInstantDownload(fileSizeBytes, threshold))
    }

    @Test
    fun `small file of 1mb is always instant at any threshold`() {
        val fileSizeBytes = 1 * 1024 * 1024L
        val thresholds = listOf(50, 100, 250, 500)

        for (threshold in thresholds) {
            assertTrue(
                "1MB file should be instant at ${threshold}MB threshold",
                isInstantDownload(fileSizeBytes, threshold)
            )
        }
    }

    @Test
    fun `large file of 600mb is never instant at any threshold`() {
        val fileSizeBytes = 600 * 1024 * 1024L
        val thresholds = listOf(50, 100, 250, 500)

        for (threshold in thresholds) {
            assertFalse(
                "600MB file should not be instant at ${threshold}MB threshold",
                isInstantDownload(fileSizeBytes, threshold)
            )
        }
    }
}
