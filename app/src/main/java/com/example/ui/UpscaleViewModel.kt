package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.ai.AiQualityAdvisor
import com.example.ai.SampleAiQualityAdvisor
import com.example.data.AppDatabase
import com.example.data.EnhancedVideoEntity
import com.example.data.VideoHistoryRepository
import com.example.engine.AiEnhanceGlEffect
import com.example.engine.LiveEnhancementState
import com.example.engine.TestClipGenerator
import com.example.engine.VideoMetadataHelper
import com.example.engine.VideoUpscaleExporter
import com.example.model.AiQualityReport
import com.example.model.EnhancementParams
import com.example.model.EnhancementPreset
import com.example.model.ExportState
import com.example.model.UpscaleTarget
import com.example.model.VideoCodecOption
import com.example.model.VideoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiAnalysisUiState {
    data object Idle : AiAnalysisUiState
    data object Analyzing : AiAnalysisUiState
    data class Ready(val report: AiQualityReport) : AiAnalysisUiState
    data class Error(val message: String) : AiAnalysisUiState
}

@OptIn(UnstableApi::class)
class UpscaleViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "UpscaleViewModel"
    }

    private val appContext = application.applicationContext
    private val aiAdvisor: AiQualityAdvisor = SampleAiQualityAdvisor()
    private val exporter = VideoUpscaleExporter(appContext)
    private val historyRepo = VideoHistoryRepository(AppDatabase.getInstance(appContext).enhancedVideoDao())

    val liveShaderState = LiveEnhancementState()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
        volume = 1f
        setVideoEffects(listOf(AiEnhanceGlEffect { liveShaderState.snapshot() }))
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }
        })
    }

    private val _selectedVideo = MutableStateFlow<VideoMetadata?>(null)
    val selectedVideo: StateFlow<VideoMetadata?> = _selectedVideo.asStateFlow()

    private val _enhancementParams = MutableStateFlow(EnhancementPreset.ULTRA_4K_CRISP.defaults)
    val enhancementParams: StateFlow<EnhancementParams> = _enhancementParams.asStateFlow()

    private val _splitPosition = MutableStateFlow(0.50f)
    val splitPosition: StateFlow<Float> = _splitPosition.asStateFlow()

    private val _showSplitComparison = MutableStateFlow(true)
    val showSplitComparison: StateFlow<Boolean> = _showSplitComparison.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isLoadingVideo = MutableStateFlow(false)
    val isLoadingVideo: StateFlow<Boolean> = _isLoadingVideo.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _aiAnalysisState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val aiAnalysisState: StateFlow<AiAnalysisUiState> = _aiAnalysisState.asStateFlow()

    val exportState: StateFlow<ExportState> = exporter.exportState

    val exportHistory: StateFlow<List<EnhancedVideoEntity>> = historyRepo.exportedVideos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        syncShaderUniforms(_enhancementParams.value)
    }

    fun loadVideo(uri: Uri) {
        viewModelScope.launch {
            _isLoadingVideo.value = true
            _loadError.value = null
            _aiAnalysisState.value = AiAnalysisUiState.Idle
            exporter.resetState()
            try {
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Photo Picker URIs may already carry temporary grant without persistable flag
                }
                val metadata = VideoMetadataHelper.extract(appContext, uri)
                _selectedVideo.value = metadata

                // Default to smart upscale target above source resolution
                val smartTarget = when {
                    metadata.shortEdge < 720 -> UpscaleTarget.FHD_1080P
                    metadata.shortEdge < 1440 -> UpscaleTarget.UHD_4K
                    else -> UpscaleTarget.UHD_4K
                }
                updateParams(_enhancementParams.value.copy(targetResolution = smartTarget))

                exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load video URI", e)
                _loadError.value = e.localizedMessage ?: "Unable to read selected video."
            } finally {
                _isLoadingVideo.value = false
            }
        }
    }

    fun loadDemoClip() {
        viewModelScope.launch {
            _isLoadingVideo.value = true
            _loadError.value = null
            try {
                val demoUri = TestClipGenerator.getOrCreateDemoClip(appContext)
                _isLoadingVideo.value = false
                loadVideo(demoUri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate demo clip", e)
                _loadError.value = e.localizedMessage ?: "Failed to generate sample clip."
                _isLoadingVideo.value = false
            }
        }
    }

    fun clearVideo() {
        exporter.cancel()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _selectedVideo.value = null
        _aiAnalysisState.value = AiAnalysisUiState.Idle
        exporter.resetState()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun toggleMute() {
        val nextMuted = !_isMuted.value
        _isMuted.value = nextMuted
        exoPlayer.volume = if (nextMuted) 0f else 1f
    }

    fun pausePreviewForBackground() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }

    fun updateSplitPosition(fraction: Float) {
        val clamped = fraction.coerceIn(0.05f, 0.95f)
        _splitPosition.value = clamped
        liveShaderState.splitPosition = clamped
    }

    fun toggleSplitComparison() {
        val next = !_showSplitComparison.value
        _showSplitComparison.value = next
        liveShaderState.showSplit = next
    }

    fun selectPreset(preset: EnhancementPreset) {
        updateParams(preset.defaults)
    }

    fun updateSharpen(value: Float) {
        updateParams(_enhancementParams.value.copy(sharpen = value, presetId = EnhancementPreset.CUSTOM.id))
    }

    fun updateDenoise(value: Float) {
        updateParams(_enhancementParams.value.copy(denoise = value, presetId = EnhancementPreset.CUSTOM.id))
    }

    fun updateHdrClarity(value: Float) {
        updateParams(_enhancementParams.value.copy(hdrClarity = value, presetId = EnhancementPreset.CUSTOM.id))
    }

    fun updateVibrance(value: Float) {
        updateParams(_enhancementParams.value.copy(vibrance = value, presetId = EnhancementPreset.CUSTOM.id))
    }

    fun updateTargetResolution(target: UpscaleTarget) {
        updateParams(_enhancementParams.value.copy(targetResolution = target))
    }

    fun updateCodec(codec: VideoCodecOption) {
        updateParams(_enhancementParams.value.copy(codec = codec))
    }

    fun runAiQualityAnalysis() {
        val video = _selectedVideo.value ?: return
        viewModelScope.launch {
            _aiAnalysisState.value = AiAnalysisUiState.Analyzing
            try {
                val report = aiAdvisor.analyzeVideo(appContext, video.uri, video)
                _aiAnalysisState.value = AiAnalysisUiState.Ready(report)
            } catch (e: Exception) {
                Log.e(TAG, "Video analysis failed", e)
                _aiAnalysisState.value = AiAnalysisUiState.Error(
                    e.localizedMessage ?: "Failed to analyze video frames."
                )
            }
        }
    }

    fun applyAiRecommendation(report: AiQualityReport) {
        updateParams(report.recommendedParams)
    }

    fun startExport() {
        val video = _selectedVideo.value ?: return
        val params = _enhancementParams.value
        pausePreviewForBackground()

        val presetTitle = EnhancementPreset.entries
            .firstOrNull { it.id == params.presetId }?.title ?: "Custom Pro"

        exporter.startExport(video, params) { outUri, fileName, targetW, targetH, fileSize, processingMs ->
            viewModelScope.launch {
                historyRepo.saveExport(
                    EnhancedVideoEntity(
                        title = fileName,
                        sourceUri = video.uri.toString(),
                        outputUri = outUri.toString(),
                        sourceWidth = video.width,
                        sourceHeight = video.height,
                        targetWidth = targetW,
                        targetHeight = targetH,
                        presetName = presetTitle,
                        codecLabel = params.codec.badge,
                        fileSizeBytes = fileSize,
                        durationMs = video.durationMs,
                        processingMs = processingMs
                    )
                )
            }
        }
    }

    fun cancelExport() {
        exporter.cancel()
    }

    fun dismissExportStatus() {
        exporter.resetState()
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyRepo.deleteExport(id)
        }
    }

    private fun updateParams(newParams: EnhancementParams) {
        _enhancementParams.value = newParams
        syncShaderUniforms(newParams)
    }

    private fun syncShaderUniforms(params: EnhancementParams) {
        liveShaderState.sharpen = params.sharpen
        liveShaderState.denoise = params.denoise
        liveShaderState.hdrClarity = params.hdrClarity
        liveShaderState.vibrance = params.vibrance
        liveShaderState.splitPosition = _splitPosition.value
        liveShaderState.showSplit = _showSplitComparison.value
    }

    override fun onCleared() {
        super.onCleared()
        exporter.cancel()
        exoPlayer.release()
    }
}
