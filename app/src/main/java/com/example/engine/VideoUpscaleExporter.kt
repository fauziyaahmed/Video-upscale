package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.model.EnhancementParams
import com.example.model.ExportState
import com.example.model.VideoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
class VideoUpscaleExporter(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun startExport(
        metadata: VideoMetadata,
        params: EnhancementParams,
        onCompletedSaved: (
            outputUri: Uri,
            fileName: String,
            targetW: Int,
            targetH: Int,
            fileSizeBytes: Long,
            processingMs: Long
        ) -> Unit
    ) {
        cancel()
        val startMs = System.currentTimeMillis()
        val (targetW, targetH) = metadata.computeTargetDimensions(params.targetResolution)
        _exportState.value = ExportState.Preparing

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFileName = "UpscaleAI_${targetH}p_${timestamp}.mp4"
        val tempFile = File(context.cacheDir, "export_$timestamp.mp4")
        if (tempFile.exists()) tempFile.delete()

        val exportUniforms = ShaderUniforms(
            sharpen = params.sharpen,
            denoise = params.denoise,
            hdrClarity = params.hdrClarity,
            vibrance = params.vibrance,
            splitPosition = 0f,
            showSplit = false
        )

        val videoEffects = buildList {
            if (targetW != metadata.width || targetH != metadata.height) {
                add(Presentation.createForWidthAndHeight(targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT))
            }
            add(AiEnhanceGlEffect { exportUniforms })
        }

        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(metadata.uri))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val targetBitrate = estimateBitrateForResolution(targetW, targetH)
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrate)
                    .build()
            )
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(params.codec.mimeType)
            .setEncoderFactory(encoderFactory)
            .setMaxDelayBetweenMuxerSamplesMs(C.TIME_UNSET)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgressPolling()
                    _exportState.value = ExportState.SavingToGallery
                    val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    try {
                        val (savedUri, savedSize) = persistToMediaStoreOrAppMovies(tempFile, outFileName)
                        _exportState.value = ExportState.Success(
                            outputUri = savedUri,
                            fileName = outFileName,
                            targetWidth = targetW,
                            targetHeight = targetH,
                            fileSizeBytes = savedSize,
                            processingMs = elapsedMs
                        )
                        onCompletedSaved(savedUri, outFileName, targetW, targetH, savedSize, elapsedMs)
                    } catch (e: Exception) {
                        _exportState.value = ExportState.Error(
                            e.localizedMessage ?: "Failed to save upscaled video."
                        )
                    } finally {
                        activeTransformer = null
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopProgressPolling()
                    activeTransformer = null
                    if (tempFile.exists()) tempFile.delete()
                    _exportState.value = ExportState.Error(
                        exportException.localizedMessage ?: "Video upscaling failed during encoding."
                    )
                }
            })
            .build()

        activeTransformer = transformer
        _exportState.value = ExportState.Processing(
            progressPercent = 0,
            elapsedMs = 0L,
            targetWidth = targetW,
            targetHeight = targetH
        )

        mainHandler.post {
            try {
                transformer.start(editedMediaItem, tempFile.absolutePath)
                startProgressPolling(transformer, startMs, targetW, targetH)
            } catch (e: Exception) {
                stopProgressPolling()
                activeTransformer = null
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Unable to start export.")
            }
        }
    }

    fun cancel() {
        stopProgressPolling()
        mainHandler.post {
            try {
                activeTransformer?.cancel()
            } catch (_: Exception) {
            } finally {
                activeTransformer = null
            }
        }
        if (_exportState.value is ExportState.Processing || _exportState.value is ExportState.Preparing) {
            _exportState.value = ExportState.Idle
        }
    }

    fun resetState() {
        _exportState.value = ExportState.Idle
    }

    private fun startProgressPolling(
        transformer: Transformer,
        startMs: Long,
        targetW: Int,
        targetH: Int
    ) {
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                if (activeTransformer !== transformer) return
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exportState.value = ExportState.Processing(
                        progressPercent = holder.progress.coerceIn(0, 99),
                        elapsedMs = System.currentTimeMillis() - startMs,
                        targetWidth = targetW,
                        targetHeight = targetH
                    )
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 200L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.postDelayed(runnable, 200L)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun estimateBitrateForResolution(width: Int, height: Int): Int {
        val pixels = width.toLong() * height.toLong()
        return when {
            pixels >= 3840L * 2160L -> 24_000_000
            pixels >= 2560L * 1440L -> 16_000_000
            pixels >= 1920L * 1080L -> 10_000_000
            else -> 5_500_000
        }
    }

    private fun persistToMediaStoreOrAppMovies(tempFile: File, fileName: String): Pair<Uri, Long> {
        val size = tempFile.length()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/UpscaleAI")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
            if (itemUri != null) {
                resolver.openOutputStream(itemUri)?.use { out ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                tempFile.delete()
                return itemUri to size
            }
        }

        val moviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "UpscaleAI")
        if (!moviesDir.exists()) moviesDir.mkdirs()
        val destFile = File(moviesDir, fileName)
        tempFile.copyTo(destFile, overwrite = true)
        tempFile.delete()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        return uri to destFile.length()
    }
}
