package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.model.AiQualityReport
import com.example.model.EnhancementParams
import com.example.model.ExportState
import com.example.model.VideoMetadata
import com.example.ui.AiAnalysisUiState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DeepAmberContainer
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepMintContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardHighlight
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StudioMonitorCard(
    player: ExoPlayer,
    video: VideoMetadata,
    params: EnhancementParams,
    splitPosition: Float,
    showSplitComparison: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    onSplitChange: (Float) -> Unit,
    onToggleSplit: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onSwapVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (targetW, targetH) = video.computeTargetDimensions(params.targetResolution)
    val aspect = (video.width.toFloat() / video.height.toFloat()).coerceIn(0.65f, 2.0f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top telemetry bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${video.width}×${video.height} → ${targetW}×${targetH}  •  ${params.codec.badge}  •  ${formatDuration(video.durationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onSwapVideo,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("change_video_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StudioSurfaceElevated,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "Change source video",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Video preview surface with interactive Before/After split overlay
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(ObsidianBg)
                    .pointerInput(showSplitComparison) {
                        detectTapGestures { offset ->
                            if (showSplitComparison && size.width > 0) {
                                onSplitChange(offset.x / size.width.toFloat())
                            } else {
                                onTogglePlay()
                            }
                        }
                    }
                    .pointerInput(showSplitComparison) {
                        if (showSplitComparison) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                if (size.width > 0) {
                                    onSplitChange(change.position.x / size.width.toFloat())
                                }
                            }
                        }
                    }
            ) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val handleX = (splitPosition * widthPx).roundToInt()

                AndroidView(
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.studio_player_view, null) as PlayerView).apply {
                            this.player = player
                        }
                    },
                    update = { view ->
                        if (view.player !== player) {
                            view.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Before / After corner labels
                if (showSplitComparison) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StudioBorder)
                        ) {
                            Text(
                                text = stringResource(R.string.label_before),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = DeepCyanContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = stringResource(R.string.label_after),
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Draggable Split Handle Pill
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(handleX - 24, 0) }
                            .fillMaxHeight()
                            .width(48.dp)
                            .testTag("split_compare_handle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = ObsidianBg.copy(alpha = 0.9f),
                            border = BorderStroke(1.5.dp, ElectricCyan),
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                                    contentDescription = "Drag to compare original vs enhanced",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        color = DeepCyanContainer.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "FULL AI PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Playback & Split controls bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier.testTag("play_pause_button"),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = ElectricCyan)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause preview" else "Play preview"
                        )
                    }
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.testTag("mute_button"),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (isMuted) "Unmute audio" else "Mute audio"
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onToggleSplit,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("toggle_split_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (showSplitComparison) DeepCyanContainer else StudioCardHighlight,
                        contentColor = if (showSplitComparison) ElectricCyan else TextSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Compare,
                        contentDescription = "Toggle split screen comparison",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showSplitComparison) "Split Compare: ON" else "Split Compare: OFF",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancementSliderRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color = ElectricCyan,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Surface(
                color = StudioCardHighlight,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Text(
                    text = "${(value * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = StudioCardHighlight
            )
        )
    }
}

@Composable
fun AiAdvisorCard(
    state: AiAnalysisUiState,
    onAnalyzeClick: () -> Unit,
    onApplyRecommendation: (AiQualityReport) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(DeepCyanContainer, DeepAmberContainer))
                            )
                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Frame Analyzer",
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Frame Quality Analyzer",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Inspects brightness, edges & compression",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onAnalyzeClick,
                    enabled = state !is AiAnalysisUiState.Analyzing,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("ai_analyze_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DeepAmberContainer,
                        contentColor = AmberGold
                    )
                ) {
                    if (state is AiAnalysisUiState.Analyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AmberGold
                        )
                    } else {
                        Text(
                            text = if (state is AiAnalysisUiState.Ready) "Re-Scan" else "Analyze",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Required notice explaining cloud Gemini AI requires Firebase
            Surface(
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ai_notice_firebase_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = state is AiAnalysisUiState.Ready) {
                if (state is AiAnalysisUiState.Ready) {
                    val report = state.report
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioSurfaceElevated)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SAMPLE ANALYSIS SCORE: ${report.overallScore}/100",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberGold
                            )
                            Text(
                                text = "Compression: ${report.compressionRisk}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }

                        Text(
                            text = report.diagnosisSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )

                        report.detectedIssues.forEach { issue ->
                            Text(
                                text = "• $issue",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = { onApplyRecommendation(report) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("apply_ai_recommendation_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberGold,
                                contentColor = ObsidianBg
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apply ${report.recommendedPreset.title} (${report.recommendedParams.targetResolution.shortBadge})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (state is AiAnalysisUiState.Error) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ExportStatusCard(
    video: VideoMetadata,
    params: EnhancementParams,
    exportState: ExportState,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onPlayExported: (Uri) -> Unit,
    onShareExported: (Uri) -> Unit,
    onDismissStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (targetW, targetH) = video.computeTargetDimensions(params.targetResolution)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (exportState) {
                is ExportState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Master Output Target",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${targetW}×${targetH} MP4 • ${params.codec.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Button(
                        onClick = onStartExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("export_video_button"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBg
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upscale & Export ${params.targetResolution.shortBadge}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is ExportState.Preparing, is ExportState.SavingToGallery -> {
                    val label = if (exportState is ExportState.Preparing) {
                        "Initializing GPU pipeline & hardware encoder..."
                    } else {
                        "Saving mastered MP4 to Movies/UpscaleAI..."
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = ElectricCyan
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }

                is ExportState.Processing -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Rendering ${exportState.targetWidth}×${exportState.targetHeight}...",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Elapsed: ${exportState.elapsedMs / 1000}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "${exportState.progressPercent}%",
                            style = MaterialTheme.typography.headlineSmall,
                            color = ElectricCyan
                        )
                    }

                    LinearProgressIndicator(
                        progress = { exportState.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ElectricCyan,
                        trackColor = StudioCardHighlight
                    )

                    OutlinedButton(
                        onClick = onCancelExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cancel_export_button"),
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_cancel_export))
                    }
                }

                is ExportState.Success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = DeepMintContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MintSuccess,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Upscaled Video Saved",
                                style = MaterialTheme.typography.titleMedium,
                                color = MintSuccess
                            )
                            Text(
                                text = "${exportState.targetWidth}×${exportState.targetHeight} • ${formatFileSize(exportState.fileSizeBytes)} • ${exportState.processingMs / 1000.0}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = onDismissStatus) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = TextMuted
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayExported(exportState.outputUri) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("play_exported_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MintSuccess,
                                contentColor = ObsidianBg
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_play_result), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onShareExported(exportState.outputUri) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_exported_button"),
                            border = BorderStroke(1.dp, StudioBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_share))
                        }
                    }
                }

                is ExportState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = onStartExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Retry Export")
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", min, sec)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "--"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%d KB", (bytes / 1024L).coerceAtLeast(1L))
    }
}

fun shareVideoUri(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Upscaled Video"))
}
