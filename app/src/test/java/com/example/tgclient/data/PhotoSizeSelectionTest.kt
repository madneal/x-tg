package com.example.tgclient.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSizeSelectionTest {
    @Test
    fun usesLargestSmallPreviewAndDefersFullResolutionFile() {
        val selection = selectPhotoFiles(
            listOf(
                PhotoSizeCandidate(fileId = 11, width = 90, height = 90),
                PhotoSizeCandidate(fileId = 12, width = 320, height = 240),
                PhotoSizeCandidate(fileId = 13, width = 640, height = 480),
                PhotoSizeCandidate(fileId = 14, width = 2560, height = 1920),
            ),
        )

        assertEquals(PhotoFileSelection(previewFileId = 13, fullFileId = 14), selection)
    }

    @Test
    fun reusesOnlyFileWhenNoSeparateFullSizeExists() {
        assertEquals(
            PhotoFileSelection(previewFileId = 20, fullFileId = null),
            selectPhotoFiles(listOf(PhotoSizeCandidate(fileId = 20, width = 320, height = 240))),
        )
    }
}
