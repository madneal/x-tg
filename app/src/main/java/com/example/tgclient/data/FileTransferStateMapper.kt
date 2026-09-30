package com.example.tgclient.data

import com.example.tgclient.model.TransferState

internal data class FileTransferSnapshot(
    val fileId: Int,
    val size: Long,
    val expectedSize: Long,
    val downloadedBytes: Long,
    val isDownloadingActive: Boolean,
    val isDownloadingCompleted: Boolean,
    val hasReadyLocalPath: Boolean,
    val uploadedBytes: Long,
    val isUploadingActive: Boolean,
)

internal fun FileTransferSnapshot.toTransferState(): TransferState {
    val uploading = isUploadingActive
    return TransferState(
        fileId = fileId,
        downloadedBytes = (if (uploading) uploadedBytes else downloadedBytes).coerceAtLeast(0L),
        totalBytes = (size.takeIf { it > 0L } ?: expectedSize).coerceAtLeast(0L),
        isActive = isDownloadingActive || uploading,
        isUploading = uploading,
        isCompleted = isDownloadingCompleted && hasReadyLocalPath,
    )
}
