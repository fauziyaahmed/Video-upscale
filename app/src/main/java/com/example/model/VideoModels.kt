package com.example.model

import android.net.Uri
import androidx.media3.common.MimeTypes
import kotlin.math.roundToInt

enum class UpscaleTarget(
    val label: String,
    val shortBadge: String,
    val targetShortEdge: Int // 0 = native, -2 = 2x scale, >0 = target height (landscape) / short edge
) {
    NATIVE("Original", "1×", 0),
    FHD_1080P("1080p FHD", "1080p", 1080),
    QHD_2K("2K QHD", "2K", 1440),
    UHD_4K("4K UHD", "4K", 2160),
    SCALE_2X("2× AI Scale", "2×", -2)
}

enum class VideoCodecOption(
    val label: String,
    val mimeType: String,
    val badge: String
) {
    H264_AVC("H.264 / AVC", MimeTypes.VIDEO_H264, "H.264"),
    H265_HEVC("H.265 / HEVC", MimeTypes.VIDEO_H265, "HEVC")
}

data class EnhancementParams(
    val sharpen: Float = 0.62f,
    val denoise: Float = 0.35f,
    val hdrClarity: Float = 0.40f,
    val vibrance: Float = 0.25f,
    val targetResolution: UpscaleTarget = UpscaleTarget.FHD_1080P,
    val codec: VideoCodecOption = VideoCodecOption.H264_AVC,
    val presetId: String = EnhancementPreset.ULTRA_4K_CRISP.id
)

enum class EnhancementPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaults: EnhancementParams
) {
    ULTRA_4K_CRISP(
        id = "ultra_4k",
        title = "4K Crisp",
        subtitle = "High edge detail + clean upscale",
        defaults = EnhancementParams(
            sharpen = 0.72f,
            denoise = 0.30f,
            hdrClarity = 0.42f,
            vibrance = 0.24f,
            targetResolution = UpscaleTarget.UHD_4K,
            codec = VideoCodecOption.H265_HEVC,
            presetId = "ultra_4k"
        )
    ),
    ANIME_REMASTER(
        id = "anime_2d",
        title = "Anime 2D",
        subtitle = "Linework sharpen + deblock + pop",
        defaults = EnhancementParams(
            sharpen = 0.85f,
            denoise = 0.62f,
            hdrClarity = 0.35f,
            vibrance = 0.55f,
            targetResolution = UpscaleTarget.QHD_2K,
            codec = VideoCodecOption.H264_AVC,
            presetId = "anime_2d"
        )
    ),
    LOW_LIGHT_CINEMA(
        id = "low_light",
        title = "Low-Light",
        subtitle = "Heavy denoise + shadow HDR lift",
        defaults = EnhancementParams(
            sharpen = 0.42f,
            denoise = 0.78f,
            hdrClarity = 0.74f,
            vibrance = 0.22f,
            targetResolution = UpscaleTarget.FHD_1080P,
            codec = VideoCodecOption.H264_AVC,
            presetId = "low_light"
        )
    ),
    FOOTAGE_RESTORE(
        id = "restore",
        title = "Restore",
        subtitle = "Deblock old compressed clips",
        defaults = EnhancementParams(
            sharpen = 0.58f,
            denoise = 0.66f,
            hdrClarity = 0.50f,
            vibrance = 0.30f,
            targetResolution = UpscaleTarget.SCALE_2X,
            codec = VideoCodecOption.H264_AVC,
            presetId = "restore"
        )
    ),
    CUSTOM(
        id = "custom",
        title = "Custom Pro",
        subtitle = "Manual GPU shader tuning",
        defaults = EnhancementParams(
            sharpen = 0.50f,
            denoise = 0.35f,
            hdrClarity = 0.40f,
            vibrance = 0.25f,
            targetResolution = UpscaleTarget.FHD_1080P,
            codec = VideoCodecOption.H264_AVC,
            presetId = "custom"
        )
    )
}

data class VideoMetadata(
    val uri: Uri,
    val displayName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bitrateBps: Long,
    val fps: Float,
    val fileSizeBytes: Long
) {
    val isPortrait: Boolean get() = height > width

    val shortEdge: Int get() = minOf(width, height)

    val resolutionLabel: String
        get() = when {
            shortEdge >= 2160 -> "4K"
            shortEdge >= 1440 -> "2K"
            shortEdge >= 1080 -> "1080p"
            shortEdge >= 720 -> "720p"
            shortEdge >= 480 -> "480p"
            shortEdge > 0 -> "${shortEdge}p"
            else -> "Unknown"
        }

    fun computeTargetDimensions(target: UpscaleTarget): Pair<Int, Int> {
        val srcW = width.coerceAtLeast(320)
        val srcH = height.coerceAtLeast(240)
        val aspect = srcW.toDouble() / srcH.toDouble()

        val (rawW, rawH) = when (target) {
            UpscaleTarget.NATIVE -> srcW to srcH
            UpscaleTarget.SCALE_2X -> {
                val maxShort = 2160
                val scaledShort = (minOf(srcW, srcH) * 2).coerceAtMost(maxShort)
                if (srcW >= srcH) {
                    (scaledShort * aspect).roundToInt() to scaledShort
                } else {
                    scaledShort to (scaledShort / aspect).roundToInt()
                }
            }
            else -> {
                val desiredShort = target.targetShortEdge
                if (srcW >= srcH) {
                    (desiredShort * aspect).roundToInt() to desiredShort
                } else {
                    desiredShort to (desiredShort / aspect).roundToInt()
                }
            }
        }

        // Round to multiples of 2 (even dimensions required by H.264/H.265 hardware encoders)
        val evenW = (rawW.coerceIn(320, 3840) / 2) * 2
        val evenH = (rawH.coerceIn(240, 3840) / 2) * 2
        return evenW to evenH
    }
}

data class AiQualityReport(
    val overallScore: Int,
    val avgLuminance: Float,
    val sharpnessIndex: Float,
    val compressionRisk: String,
    val diagnosisSummary: String,
    val detectedIssues: List<String>,
    val recommendedPreset: EnhancementPreset,
    val recommendedParams: EnhancementParams,
    val isSampleFallback: Boolean = true
)

sealed interface ExportState {
    data object Idle : ExportState
    data object Preparing : ExportState
    data class Processing(
        val progressPercent: Int,
        val elapsedMs: Long,
        val targetWidth: Int,
        val targetHeight: Int
    ) : ExportState
    data object SavingToGallery : ExportState
    data class Success(
        val outputUri: Uri,
        val fileName: String,
        val targetWidth: Int,
        val targetHeight: Int,
        val fileSizeBytes: Long,
        val processingMs: Long
    ) : ExportState
    data class Error(val message: String) : ExportState
}
