package com.example.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates a short 360p compressed H.264 MP4 test clip locally via MediaCodec + MediaMuxer
 * so users on fresh devices or emulators without gallery videos can immediately preview and
 * export an upscaled video.
 */
object TestClipGenerator {

    suspend fun getOrCreateDemoClip(context: Context): Uri = withContext(Dispatchers.IO) {
        val outFile = File(context.cacheDir, "demo_skyline_360p.mp4")
        if (outFile.exists() && outFile.length() > 16_000L) {
            return@withContext Uri.fromFile(outFile)
        }

        val width = 640
        val height = 360
        val fps = 24
        val frameCount = 72 // 3 seconds
        val bitrate = 450_000 // Intentionally low bitrate so upscaling & enhancement are clearly visible

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            if (outFile.exists()) outFile.delete()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rng = Random(42)

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            trackIndex = muxer!!.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (outIndex >= 0) {
                        val encodedData = encoder.getOutputBuffer(outIndex)
                        if (encodedData != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer!!.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            }

            for (i in 0 until frameCount) {
                val canvas: Canvas = inputSurface.lockCanvas(null)
                drawTestFrame(canvas, width, height, i, frameCount, paint, rng)
                inputSurface.unlockCanvasAndPost(canvas)
                drainEncoder(false)
            }
            drainEncoder(true)
            inputSurface.release()
        } finally {
            try {
                encoder.stop()
            } catch (_: Exception) {
            }
            try {
                encoder.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {
            }
        }

        Uri.fromFile(outFile)
    }

    private fun drawTestFrame(
        canvas: Canvas,
        w: Int,
        h: Int,
        frame: Int,
        total: Int,
        paint: Paint,
        rng: Random
    ) {
        val t = frame.toFloat() / total.toFloat()

        // Dim, slightly hazy twilight gradient sky
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(18, 24, 38), Color.rgb(34, 42, 62), Color.rgb(22, 26, 36)),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Moving luminous orb
        val orbX = w * (0.2f + 0.6f * t)
        val orbY = h * (0.34f + 0.08f * sin(t * 6.28f))
        paint.color = Color.argb(190, 90, 175, 220)
        canvas.drawCircle(orbX, orbY, 38f, paint)
        paint.color = Color.argb(120, 230, 170, 95)
        canvas.drawCircle(orbX - 10f, orbY - 8f, 18f, paint)

        // Cityscape silhouettes + fine window grids (great test for super-res sharpening & HDR lift)
        val buildings = listOf(
            40f to 165f, 110f to 130f, 175f to 195f, 250f to 145f,
            320f to 210f, 405f to 150f, 470f to 180f, 545f to 140f
        )
        buildings.forEachIndexed { idx, (bx, topY) ->
            val bw = 62f
            paint.color = Color.rgb(26 + (idx % 3) * 4, 30, 42)
            canvas.drawRect(bx, topY, bx + bw, h.toFloat(), paint)

            // Fine window details
            var wy = topY + 14f
            while (wy < h - 24f) {
                var wx = bx + 10f
                while (wx < bx + bw - 12f) {
                    val lit = ((idx * 7 + (wx.toInt() / 12) + (wy.toInt() / 16) + frame / 10) % 4) == 0
                    paint.color = if (lit) Color.rgb(185, 165, 110) else Color.rgb(42, 48, 64)
                    canvas.drawRect(wx, wy, wx + 8f, wy + 10f, paint)
                    wx += 14f
                }
                wy += 18f
            }
        }

        // Foreground reflective road + fine horizon grid lines
        paint.color = Color.rgb(20, 23, 30)
        canvas.drawRect(0f, h - 55f, w.toFloat(), h.toFloat(), paint)
        paint.color = Color.argb(135, 120, 180, 200)
        paint.strokeWidth = 1.5f
        for (lineIdx in 0..8) {
            val y = h - 50f + lineIdx * 6f
            canvas.drawLine(0f, y, w.toFloat(), y, paint)
        }

        // Subtle sensor noise grain so denoising & sharpening can show visible improvement
        paint.strokeWidth = 1f
        for (g in 0 until 90) {
            val gx = rng.nextFloat() * w
            val gy = rng.nextFloat() * h
            val v = rng.nextInt(55, 130)
            paint.color = Color.argb(38, v, v, v)
            canvas.drawPoint(gx, gy, paint)
        }
    }
}
