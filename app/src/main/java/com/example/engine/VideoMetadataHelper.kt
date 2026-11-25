package com.example.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoMetadataHelper {

    suspend fun extract(context: Context, uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var width = 0
        var height = 0
        var rotation = 0
        var durationMs = 0L
        var bitrateBps = 0L
        var fps = 30f

        try {
            retriever.setDataSource(context, uri)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        // Apply rotation so portrait videos report visual width/height properly
        val visualWidth = if (rotation == 90 || rotation == 270) height else width
        val visualHeight = if (rotation == 90 || rotation == 270) width else height

        // Query FPS from MediaExtractor if available
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat().coerceAtLeast(1f)
                    }
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }

        var displayName = "Selected Video"
        var fileSizeBytes = 0L
        if (uri.scheme == "file" && uri.path != null) {
            val file = java.io.File(uri.path!!)
            displayName = file.name
            fileSizeBytes = file.length()
        } else {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0) fileSizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }

        VideoMetadata(
            uri = uri,
            displayName = displayName,
            width = visualWidth.coerceAtLeast(320),
            height = visualHeight.coerceAtLeast(240),
            durationMs = durationMs.coerceAtLeast(500L),
            bitrateBps = bitrateBps,
            fps = fps,
            fileSizeBytes = fileSizeBytes
        )
    }
}
