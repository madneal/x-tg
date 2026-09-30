package com.example.tgclient.data

internal data class PhotoSizeCandidate(val fileId: Int, val width: Int, val height: Int)

internal data class PhotoFileSelection(val previewFileId: Int?, val fullFileId: Int?)

/** Picks a modest inline preview and keeps the largest photo for explicit viewing/export. */
internal fun selectPhotoFiles(
    sizes: Collection<PhotoSizeCandidate>,
    previewMaxDimension: Int = 720,
): PhotoFileSelection {
    val validSizes = sizes.filter { it.fileId > 0 && it.width > 0 && it.height > 0 }
    val full = validSizes.maxByOrNull { it.width.toLong() * it.height }
    val preview = validSizes
        .filter { maxOf(it.width, it.height) <= previewMaxDimension }
        .maxByOrNull { it.width.toLong() * it.height }
        ?: validSizes.minByOrNull { it.width.toLong() * it.height }
    return PhotoFileSelection(
        previewFileId = preview?.fileId,
        fullFileId = full?.fileId?.takeIf { it != preview?.fileId },
    )
}
