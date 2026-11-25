package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.EnhancementParams
import com.example.model.EnhancementPreset
import com.example.model.UpscaleTarget
import com.example.model.VideoCodecOption
import com.example.ui.components.AiAdvisorCard
import com.example.ui.components.EnhancementSliderRow
import com.example.ui.components.ExportStatusCard
import com.example.ui.components.StudioMonitorCard
import com.example.ui.components.shareVideoUri
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardHighlight
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StudioScreen(
    viewModel: UpscaleViewModel,
    onPlayExportedVideo: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val params by viewModel.enhancementParams.collectAsStateWithLifecycle()
    val splitPosition by viewModel.splitPosition.collectAsStateWithLifecycle()
    val showSplit by viewModel.showSplitComparison.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingVideo.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val aiAnalysisState by viewModel.aiAnalysisState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    // Zero-permission Android Photo Picker for videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadVideo(uri)
        }
    }

    val openPicker = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = ElectricCyan)
                Text(
                    text = "Preparing video stream...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        } else if (selectedVideo == null) {
            EmptyStudioLanding(
                errorMessage = loadError,
                onPickVideo = openPicker,
                onLoadDemoClip = { viewModel.loadDemoClip() }
            )
        } else {
            val video = selectedVideo!!
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 840.dp
                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            StudioMonitorCard(
                                player = viewModel.exoPlayer,
                                video = video,
                                params = params,
                                splitPosition = splitPosition,
                                showSplitComparison = showSplit,
                                isPlaying = isPlaying,
                                isMuted = isMuted,
                                onSplitChange = viewModel::updateSplitPosition,
                                onToggleSplit = viewModel::toggleSplitComparison,
                                onTogglePlay = viewModel::togglePlayPause,
                                onToggleMute = viewModel::toggleMute,
                                onSwapVideo = openPicker
                            )
                            AiAdvisorCard(
                                state = aiAnalysisState,
                                onAnalyzeClick = viewModel::runAiQualityAnalysis,
                                onApplyRecommendation = viewModel::applyAiRecommendation
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.9f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ControlsSection(
                                params = params,
                                onSelectPreset = viewModel::selectPreset,
                                onTargetResolutionChange = viewModel::updateTargetResolution,
                                onCodecChange = viewModel::updateCodec,
                                onSharpenChange = viewModel::updateSharpen,
                                onDenoiseChange = viewModel::updateDenoise,
                                onHdrChange = viewModel::updateHdrClarity,
                                onVibranceChange = viewModel::updateVibrance
                            )
                            ExportStatusCard(
                                video = video,
                                params = params,
                                exportState = exportState,
                                onStartExport = viewModel::startExport,
                                onCancelExport = viewModel::cancelExport,
                                onPlayExported = onPlayExportedVideo,
                                onShareExported = { shareVideoUri(context, it) },
                                onDismissStatus = viewModel::dismissExportStatus
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            StudioMonitorCard(
                                player = viewModel.exoPlayer,
                                video = video,
                                params = params,
                                splitPosition = splitPosition,
                                showSplitComparison = showSplit,
                                isPlaying = isPlaying,
                                isMuted = isMuted,
                                onSplitChange = viewModel::updateSplitPosition,
                                onToggleSplit = viewModel::toggleSplitComparison,
                                onTogglePlay = viewModel::togglePlayPause,
                                onToggleMute = viewModel::toggleMute,
                                onSwapVideo = openPicker
                            )

                            AiAdvisorCard(
                                state = aiAnalysisState,
                                onAnalyzeClick = viewModel::runAiQualityAnalysis,
                                onApplyRecommendation = viewModel::applyAiRecommendation
                            )

                            ControlsSection(
                                params = params,
                                onSelectPreset = viewModel::selectPreset,
                                onTargetResolutionChange = viewModel::updateTargetResolution,
                                onCodecChange = viewModel::updateCodec,
                                onSharpenChange = viewModel::updateSharpen,
                                onDenoiseChange = viewModel::updateDenoise,
                                onHdrChange = viewModel::updateHdrClarity,
                                onVibranceChange = viewModel::updateVibrance
                            )

                            ExportStatusCard(
                                video = video,
                                params = params,
                                exportState = exportState,
                                onStartExport = viewModel::startExport,
                                onCancelExport = viewModel::cancelExport,
                                onPlayExported = onPlayExportedVideo,
                                onShareExported = { shareVideoUri(context, it) },
                                onDismissStatus = viewModel::dismissExportStatus
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStudioLanding(
    errorMessage: String?,
    onPickVideo: () -> Unit,
    onLoadDemoClip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Studio Mastering Card
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero),
                        contentDescription = "Before and after 4K video restoration preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ObsidianBg.copy(alpha = 0.55f),
                                        ObsidianBg.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                        color = DeepCyanContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "GPU SUPER-RESOLUTION ENGINE",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hero_title),
                            style = MaterialTheme.typography.displaySmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.hero_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Primary CTAs
            Button(
                onClick = onPickVideo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("select_video_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBg
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.action_select_video),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onLoadDemoClip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("load_demo_clip_button"),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, StudioBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.action_load_demo),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Algorithm capability tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CapabilityBadgeTile(
                    icon = Icons.Rounded.AutoFixHigh,
                    title = "Edge Super-Res",
                    detail = "Anti-halo unsharp",
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                CapabilityBadgeTile(
                    icon = Icons.Rounded.BlurOff,
                    title = "GPU Denoise",
                    detail = "Bilateral deblock",
                    accent = MintSuccess,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CapabilityBadgeTile(
                    icon = Icons.Rounded.Contrast,
                    title = "HDR Tone Map",
                    detail = "Shadow detail lift",
                    accent = AmberGold,
                    modifier = Modifier.weight(1f)
                )
                CapabilityBadgeTile(
                    icon = Icons.Rounded.HighQuality,
                    title = "Up to 4K HEVC",
                    detail = "Hardware Media3",
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CapabilityBadgeTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ControlsSection(
    params: EnhancementParams,
    onSelectPreset: (EnhancementPreset) -> Unit,
    onTargetResolutionChange: (UpscaleTarget) -> Unit,
    onCodecChange: (VideoCodecOption) -> Unit,
    onSharpenChange: (Float) -> Unit,
    onDenoiseChange: (Float) -> Unit,
    onHdrChange: (Float) -> Unit,
    onVibranceChange: (Float) -> Unit
) {
    // 1. Enhancement Presets
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.MovieCreation,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Mastering Presets",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(EnhancementPreset.entries) { preset ->
                    val selected = params.presetId == preset.id
                    Surface(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelectPreset(preset) }
                            .testTag("preset_${preset.id}"),
                        color = if (selected) DeepCyanContainer else StudioSurfaceElevated,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) ElectricCyan else StudioBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) ElectricCyan else TextPrimary
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // 2. Output Resolution & Codec
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Target Upscale Resolution",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(UpscaleTarget.entries) { target ->
                    val selected = params.targetResolution == target
                    FilterChip(
                        selected = selected,
                        onClick = { onTargetResolutionChange(target) },
                        label = { Text(target.label) },
                        modifier = Modifier.testTag("res_${target.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepCyanContainer,
                            selectedLabelColor = ElectricCyan,
                            containerColor = StudioSurfaceElevated,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = ElectricCyan,
                            borderColor = StudioBorder
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Codec:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                VideoCodecOption.entries.forEach { codec ->
                    val selected = params.codec == codec
                    FilterChip(
                        selected = selected,
                        onClick = { onCodecChange(codec) },
                        label = { Text(codec.label) },
                        modifier = Modifier.testTag("codec_${codec.badge.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioCardHighlight,
                            selectedLabelColor = ElectricCyan,
                            containerColor = StudioSurfaceElevated,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }
    }

    // 3. Real-Time GPU Enhancement Sliders
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Real-Time GPU Enhancement",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            EnhancementSliderRow(
                title = "Super-Res Sharpen",
                subtitle = "Edge-aware unsharp detail boost",
                icon = Icons.Rounded.AutoFixHigh,
                value = params.sharpen,
                onValueChange = onSharpenChange,
                accentColor = ElectricCyan,
                testTag = "slider_sharpen"
            )
            EnhancementSliderRow(
                title = "Denoise & Deblock",
                subtitle = "Edge-preserving bilateral smoothing",
                icon = Icons.Rounded.BlurOff,
                value = params.denoise,
                onValueChange = onDenoiseChange,
                accentColor = MintSuccess,
                testTag = "slider_denoise"
            )
            EnhancementSliderRow(
                title = "HDR Clarity",
                subtitle = "Shadow recovery & S-curve contrast",
                icon = Icons.Rounded.Contrast,
                value = params.hdrClarity,
                onValueChange = onHdrChange,
                accentColor = AmberGold,
                testTag = "slider_hdr"
            )
            EnhancementSliderRow(
                title = "Color Vibrance",
                subtitle = "Perceptual tone mastering",
                icon = Icons.Rounded.ColorLens,
                value = params.vibrance,
                onValueChange = onVibranceChange,
                accentColor = ElectricCyan,
                testTag = "slider_vibrance"
            )
        }
    }
}
