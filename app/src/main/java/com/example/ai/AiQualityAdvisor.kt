package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.AiQualityReport
import com.example.model.EnhancementParams
import com.example.model.EnhancementPreset
import com.example.model.UpscaleTarget
import com.example.model.VideoCodecOption
import com.example.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

interface AiQualityAdvisor {
    suspend fun analyzeVideo(
        context: Context,
        uri: Uri,
        metadata: VideoMetadata
    ): AiQualityReport
}

/**
 * On-device sample/heuristic analyzer used when cloud Gemini AI Logic (Firebase)
 * is not enabled. Extracts real video keyframes via MediaMetadataRetriever to
 * measure luma, contrast, and edge gradient sharpness and suggests shader parameters.
 */
class SampleAiQualityAdvisor : AiQualityAdvisor {

    override suspend fun analyzeVideo(
        context: Context,
        uri: Uri,
        metadata: VideoMetadata
    ): AiQualityReport = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var avgLuma = 0.48f
        var avgSharpness = 0.45f
        var contrastSpread = 0.50f

        try {
            retriever.setDataSource(context, uri)
            val durationUs = (metadata.durationMs.coerceAtLeast(1000L)) * 1000L
            val timestamps = listOf(
                (durationUs * 0.15).toLong(),
                (durationUs * 0.50).toLong(),
                (durationUs * 0.85).toLong()
            )
            val lumaList = mutableListOf<Float>()
            val sharpList = mutableListOf<Float>()
            val contrastList = mutableListOf<Float>()

            for (ts in timestamps) {
                val frame = retriever.getFrameAtTime(ts, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, 128, 72, true)
                    val metrics = inspectBitmapMetrics(scaled)
                    lumaList.add(metrics.first)
                    sharpList.add(metrics.second)
                    contrastList.add(metrics.third)
                    if (scaled != frame) scaled.recycle()
                    frame.recycle()
                }
            }

            if (lumaList.isNotEmpty()) {
                avgLuma = lumaList.average().toFloat()
                avgSharpness = sharpList.average().toFloat()
                contrastSpread = contrastList.average().toFloat()
            }
        } catch (_: Exception) {
            // Fall back to metadata-derived metrics if retriever fails on exotic streams
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val pixelsPerFrame = (metadata.width * metadata.height).coerceAtLeast(1)
        val bpp = if (metadata.fps > 0f && metadata.bitrateBps > 0L) {
            metadata.bitrateBps.toFloat() / (pixelsPerFrame * metadata.fps)
        } else {
            0.08f
        }

        val isLowRes = metadata.shortEdge < 720
        val isDark = avgLuma < 0.32f
        val isSoft = avgSharpness < 0.36f
        val isFlat = contrastSpread < 0.42f
        val isHeavyCompressed = bpp < 0.06f

        val issues = buildList {
            if (isLowRes) add("Sub-HD source (${metadata.resolutionLabel}) benefits from super-resolution upscaling")
            if (isDark) add("Low-light exposure (${(avgLuma * 100).roundToInt()}% luma) shows shadow noise risk")
            if (isSoft) add("Soft edge micro-contrast detected across keyframes")
            if (isFlat) add("Compressed dynamic range — HDR tone lift recommended")
            if (isHeavyCompressed) add("Low bitrate density — macroblock deblocking advised")
            if (isEmpty()) add("Clean source — ideal for crisp 4K detail mastering")
        }

        val recommendedPreset = when {
            isDark -> EnhancementPreset.LOW_LIGHT_CINEMA
            isHeavyCompressed && isLowRes -> EnhancementPreset.FOOTAGE_RESTORE
            isSoft -> EnhancementPreset.ULTRA_4K_CRISP
            else -> EnhancementPreset.ULTRA_4K_CRISP
        }

        val targetRes = when {
            metadata.shortEdge <= 480 -> UpscaleTarget.FHD_1080P
            metadata.shortEdge <= 1080 -> UpscaleTarget.UHD_4K
            else -> UpscaleTarget.UHD_4K
        }

        val sharpen = (0.45f + (1f - avgSharpness.coerceIn(0f, 1f)) * 0.45f).coerceIn(0.35f, 0.90f)
        val denoise = when {
            isDark && isHeavyCompressed -> 0.78f
            isDark || isHeavyCompressed -> 0.60f
            else -> 0.28f
        }
        val hdrClarity = when {
            isDark -> 0.72f
            isFlat -> 0.58f
            else -> 0.36f
        }
        val vibrance = if (isFlat) 0.38f else 0.25f

        val rawScore = (
            (if (metadata.shortEdge >= 1080) 35 else if (metadata.shortEdge >= 720) 24 else 14) +
                (avgSharpness * 35f).roundToInt() +
                (contrastSpread * 20f).roundToInt() +
                (if (isHeavyCompressed) 2 else 10)
            ).coerceIn(28, 96)

        val compressionRisk = when {
            bpp < 0.05f -> "High"
            bpp < 0.10f -> "Moderate"
            else -> "Low"
        }

        val summary = buildString {
            append("Sample local frame inspection scored source quality at $rawScore/100. ")
            append(
                when {
                    isDark -> "Detected underexposed shadows; boosting bilateral denoise and HDR shadow lift."
                    isLowRes -> "Low-resolution stream detected; tuning edge-preserving unsharp boost and ${targetRes.shortBadge} scaling."
                    else -> "Good baseline detail; applying crisp super-resolution sharpening and clean HDR balance."
                }
            )
        }

        AiQualityReport(
            overallScore = rawScore,
            avgLuminance = avgLuma,
            sharpnessIndex = avgSharpness,
            compressionRisk = compressionRisk,
            diagnosisSummary = summary,
            detectedIssues = issues,
            recommendedPreset = recommendedPreset,
            recommendedParams = EnhancementParams(
                sharpen = sharpen,
                denoise = denoise,
                hdrClarity = hdrClarity,
                vibrance = vibrance,
                targetResolution = targetRes,
                codec = if (targetRes == UpscaleTarget.UHD_4K) VideoCodecOption.H265_HEVC else VideoCodecOption.H264_AVC,
                presetId = recommendedPreset.id
            ),
            isSampleFallback = true
        )
    }

    private fun inspectBitmapMetrics(bitmap: Bitmap): Triple<Float, Float, Float> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var lumaSum = 0.0
        var minLuma = 1f
        var maxLuma = 0f
        var gradSum = 0.0
        var gradCount = 0

        val lumaGrid = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c) / 255f
            val g = Color.green(c) / 255f
            val b = Color.blue(c) / 255f
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            lumaGrid[i] = l
            lumaSum += l
            if (l < minLuma) minLuma = l
            if (l > maxLuma) maxLuma = l
        }

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val gx = abs(lumaGrid[idx + 1] - lumaGrid[idx - 1])
                val gy = abs(lumaGrid[idx + w] - lumaGrid[idx - w])
                gradSum += (gx + gy)
                gradCount++
            }
        }

        val avgLuma = (lumaSum / pixels.size).toFloat().coerceIn(0f, 1f)
        val sharpness = ((gradSum / gradCount.coerceAtLeast(1)) * 6.5).toFloat().coerceIn(0f, 1f)
        val contrast = (maxLuma - minLuma).coerceIn(0f, 1f)
        return Triple(avgLuma, sharpness, contrast)
    }
}
