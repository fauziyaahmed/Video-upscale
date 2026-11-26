package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates a short 360p compressed H.264 MP4 test clip locally via MediaCodec (YUV420Flexible)
 * and MediaMuxer so users on fresh devices or emulators without gallery videos can immediately
 * preview and export an upscaled video without Surface/EGL input dependencies.
 */
object TestClipGenerator {

    private const val DEMO_FILE_NAME = "demo_skyline_v2_360p.mp4"

    suspend fun getOrCreateDemoClip(context: Context): Uri = withContext(Dispatchers.IO) {
        val outFile = File(context.cacheDir, DEMO_FILE_NAME)
        if (outFile.exists() && outFile.length() > 24_000L) {
            return@withContext Uri.fromFile(outFile)
        }
        if (outFile.exists()) {
            outFile.delete()
        }

        val width = 640
        val height = 360
        val fps = 24
        val frameCount = 60 // 2.5 seconds
        val bitrate = 480_000 // Intentionally modest bitrate so upscaling & enhancement are clearly visible

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false
        var samplesWritten = 0

        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)
        val argbPixels = IntArray(width * height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(42)

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            var inputDone = false
            var outputDone = false
            var frameIndex = 0
            val frameDurationUs = 1_000_000L / fps

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        if (frameIndex >= frameCount) {
                            encoder.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                frameIndex * frameDurationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            drawTestFrame(canvas, width, height, frameIndex, frameCount, paint, rng)
                            frameBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)

                            val image = try {
                                encoder.getInputImage(inIndex)
                            } catch (_: Exception) {
                                null
                            }

                            val byteSize: Int
                            if (image != null) {
                                fillYuvImageFromArgb(image, argbPixels, width, height)
                                byteSize = width * height * 3 / 2
                            } else {
                                val inBuffer = encoder.getInputBuffer(inIndex)
                                    ?: throw IllegalStateException("Encoder input buffer unavailable")
                                byteSize = fillI420BufferFromArgb(inBuffer, argbPixels, width, height)
                            }

                            encoder.queueInputBuffer(
                                inIndex,
                                0,
                                byteSize,
                                frameIndex * frameDurationUs,
                                0
                            )
                            frameIndex++
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(outIndex)
                        if (encodedData != null &&
                            bufferInfo.size > 0 &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            samplesWritten++
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (!muxerStarted || samplesWritten == 0) {
                throw IllegalStateException("Demo video encoder produced no frames.")
            }
        } catch (e: Exception) {
            if (outFile.exists()) outFile.delete()
            throw e
        } finally {
            frameBitmap.recycle()
            try {
                encoder.stop()
            } catch (_: Exception) {
            }
            try {
                encoder.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted && samplesWritten > 0) {
                    muxer?.stop()
                }
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }

        Uri.fromFile(outFile)
    }

    private fun fillYuvImageFromArgb(image: Image, argb: IntArray, width: Int, height: Int) {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (y in 0 until height) {
            val yRowOffset = y * yRowStride
            val uvRow = y shr 1
            val uRowOffset = uvRow * uRowStride
            val vRowOffset = uvRow * vRowStride
            val rowStart = y * width

            for (x in 0 until width) {
                val c = argb[rowStart + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yBuf.put(yRowOffset + x * yPixelStride, yVal.toByte())

                if ((y and 1) == 0 && (x and 1) == 0) {
                    val uvCol = x shr 1
                    val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    uBuf.put(uRowOffset + uvCol * uPixelStride, uVal.toByte())
                    vBuf.put(vRowOffset + uvCol * vPixelStride, vVal.toByte())
                }
            }
        }
    }

    private fun fillI420BufferFromArgb(buffer: ByteBuffer, argb: IntArray, width: Int, height: Int): Int {
        buffer.clear()
        val frameSize = width * height
        val ySize = frameSize
        val uvSize = frameSize / 4

        var yIdx = 0
        var uIdx = ySize
        var vIdx = ySize + uvSize

        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                val c = argb[rowStart + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                buffer.put(yIdx++, yVal.toByte())

                if ((y and 1) == 0 && (x and 1) == 0) {
                    val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    buffer.put(uIdx++, uVal.toByte())
                    buffer.put(vIdx++, vVal.toByte())
                }
            }
        }
        return ySize + uvSize * 2
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
