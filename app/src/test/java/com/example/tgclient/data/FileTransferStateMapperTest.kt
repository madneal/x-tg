package com.example.tgclient.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTransferStateMapperTest {
    @Test
    fun activeUploadUsesRemoteUploadedBytesAndExpectedSizeFallback() {
        val state = FileTransferSnapshot(
            fileId = 7,
            size = 0,
            expectedSize = 1_000,
            downloadedBytes = 900,
            isDownloadingActive = false,
            isDownloadingCompleted = false,
            hasReadyLocalPath = false,
            uploadedBytes = 300,
            isUploadingActive = true,
        ).toTransferState()

        assertEquals(300L, state.downloadedBytes)
        assertEquals(1_000L, state.totalBytes)
        assertTrue(state.isActive)
        assertTrue(state.isUploading)
        assertFalse(state.isCompleted)
    }

    @Test
    fun incomingDownloadDoesNotUseRemoteUploadSizeAsLocalProgress() {
        val state = FileTransferSnapshot(
            fileId = 8,
            size = 2_000,
            expectedSize = 0,
            downloadedBytes = 450,
            isDownloadingActive = true,
            isDownloadingCompleted = false,
            hasReadyLocalPath = false,
            uploadedBytes = 2_000,
            isUploadingActive = false,
        ).toTransferState()

        assertEquals(450L, state.downloadedBytes)
        assertEquals(2_000L, state.totalBytes)
        assertTrue(state.isActive)
        assertFalse(state.isUploading)
    }

    @Test
    fun downloadedFileIsOnlyCompleteWhenItsLocalPathIsReady() {
        val incomplete = FileTransferSnapshot(9, 100, 0, 100, false, true, false, 100, false).toTransferState()
        val complete = FileTransferSnapshot(9, 100, 0, 100, false, true, true, 100, false).toTransferState()

        assertFalse(incomplete.isCompleted)
        assertTrue(complete.isCompleted)
    }
}
