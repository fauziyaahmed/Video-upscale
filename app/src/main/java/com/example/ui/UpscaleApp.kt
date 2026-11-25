package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private const val ROUTE_STUDIO = "studio"
private const val ROUTE_EXPORTS = "exports"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpscaleApp(
    viewModel: UpscaleViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STUDIO

    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val exportHistory by viewModel.exportHistory.collectAsStateWithLifecycle()
    var playbackDialogUri by remember { mutableStateOf<Uri?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pausePreviewForBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle back from secondary tab cleanly
    BackHandler(enabled = currentRoute == ROUTE_EXPORTS) {
        navController.navigate(ROUTE_STUDIO) {
            popUpTo(ROUTE_STUDIO) { inclusive = true }
            launchSingleTop = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    },
                    actions = {
                        if (currentRoute == ROUTE_STUDIO && selectedVideo != null) {
                            IconButton(
                                onClick = { viewModel.clearVideo() },
                                modifier = Modifier.testTag("clear_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close active video",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = StudioSurface,
                        titleContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = StudioSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_STUDIO,
                            onClick = {
                                if (currentRoute != ROUTE_STUDIO) {
                                    navController.navigate(ROUTE_STUDIO) {
                                        popUpTo(ROUTE_STUDIO) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_STUDIO) Icons.Rounded.AutoFixHigh else Icons.Outlined.AutoFixHigh,
                                    contentDescription = stringResource(R.string.nav_studio)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            modifier = Modifier.testTag("nav_studio_tab"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = DeepCyanContainer,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )

                        NavigationBarItem(
                            selected = currentRoute == ROUTE_EXPORTS,
                            onClick = {
                                if (currentRoute != ROUTE_EXPORTS) {
                                    viewModel.pausePreviewForBackground()
                                    navController.navigate(ROUTE_EXPORTS) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (exportHistory.isNotEmpty()) {
                                            Badge(containerColor = ElectricCyan, contentColor = ObsidianBg) {
                                                Text("${exportHistory.size}")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_EXPORTS) Icons.Rounded.VideoLibrary else Icons.Outlined.VideoLibrary,
                                        contentDescription = stringResource(R.string.nav_exports)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_exports)) },
                            modifier = Modifier.testTag("nav_exports_tab"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = DeepCyanContainer,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(containerColor = StudioSurface) {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_STUDIO,
                            onClick = {
                                if (currentRoute != ROUTE_STUDIO) {
                                    navController.navigate(ROUTE_STUDIO) {
                                        popUpTo(ROUTE_STUDIO) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_STUDIO) Icons.Rounded.AutoFixHigh else Icons.Outlined.AutoFixHigh,
                                    contentDescription = stringResource(R.string.nav_studio)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            modifier = Modifier.testTag("rail_studio_tab"),
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = DeepCyanContainer,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )

                        NavigationRailItem(
                            selected = currentRoute == ROUTE_EXPORTS,
                            onClick = {
                                if (currentRoute != ROUTE_EXPORTS) {
                                    viewModel.pausePreviewForBackground()
                                    navController.navigate(ROUTE_EXPORTS) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (exportHistory.isNotEmpty()) {
                                            Badge(containerColor = ElectricCyan, contentColor = ObsidianBg) {
                                                Text("${exportHistory.size}")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_EXPORTS) Icons.Rounded.VideoLibrary else Icons.Outlined.VideoLibrary,
                                        contentDescription = stringResource(R.string.nav_exports)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_exports)) },
                            modifier = Modifier.testTag("rail_exports_tab"),
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = DeepCyanContainer,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_STUDIO,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_STUDIO) {
                        StudioScreen(
                            viewModel = viewModel,
                            onPlayExportedVideo = { uri ->
                                viewModel.pausePreviewForBackground()
                                playbackDialogUri = uri
                            }
                        )
                    }
                    composable(ROUTE_EXPORTS) {
                        ExportsScreen(
                            viewModel = viewModel,
                            onPlayVideo = { uri ->
                                playbackDialogUri = uri
                            }
                        )
                    }
                }
            }
        }
    }

    playbackDialogUri?.let { uri ->
        ExportedVideoPlayerDialog(
            videoUri = uri,
            onDismiss = { playbackDialogUri = null }
        )
    }
}
