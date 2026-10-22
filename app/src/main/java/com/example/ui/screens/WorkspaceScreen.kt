package com.example.ui.screens

import android.appwidget.AppWidgetHost
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.HyperFolderLayoutMode
import com.example.domain.model.HyperWidgetType
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MtzThemeMetadata
import com.example.domain.model.WorkspaceFolder
import com.example.domain.model.WorkspaceWidgetItem
import com.example.ui.components.AppIconTile
import com.example.ui.components.HyperFolderCard
import com.example.ui.components.HyperWorkspaceWidgetCard
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceScreen(
    activeTheme: MtzThemeMetadata?,
    wallpaperBitmap: Bitmap?,
    engineConfig: LauncherEngineConfig,
    allApps: List<InstalledAppItem>,
    workspaceApps: List<InstalledAppItem>,
    dockApps: List<InstalledAppItem>,
    workspaceFolders: List<WorkspaceFolder>,
    workspaceWidgets: List<WorkspaceWidgetItem>,
    deviceTelemetry: DeviceTelemetrySnapshot,
    appWidgetHost: AppWidgetHost?,
    onAppClick: (InstalledAppItem) -> Unit,
    onAppLongClick: (InstalledAppItem) -> Unit,
    onExpandFolder: (WorkspaceFolder) -> Unit,
    onCycleFolderMode: (WorkspaceFolder) -> Unit,
    onOpenWidgetsAndFoldersPicker: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    onRemoveWidget: (String) -> Unit,
    onOpenAppDrawer: () -> Unit,
    onOpenMtzStudio: () -> Unit,
    onOpenApiPipeline: () -> Unit
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val timeString = remember(currentTimeMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTimeMillis))
    }
    val dateString = remember(currentTimeMillis) {
        SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(currentTimeMillis)).uppercase(Locale.US)
    }

    val fullWidthWidgets = remember(workspaceWidgets) {
        workspaceWidgets.filter {
            it.widgetType == HyperWidgetType.CLOCK_MTZ_STATUS ||
                it.widgetType == HyperWidgetType.MAML_LIVE_DUO ||
                it.widgetType == HyperWidgetType.NATIVE_APPWIDGET
        }
    }
    val halfWidthWidgets = remember(workspaceWidgets) {
        workspaceWidgets.filter {
            it.widgetType == HyperWidgetType.DEVICE_TELEMETRY ||
                it.widgetType == HyperWidgetType.STORAGE_AND_PACKS
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
            .testTag("workspace_screen")
    ) {
        // Wallpaper Layer from Active Xiaomi .MTZ Package
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap.asImageBitmap(),
                contentDescription = "Active MTZ Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SlateDeepBackground.copy(alpha = 0.65f),
                                SlateDeepBackground.copy(alpha = 0.40f),
                                SlateDeepBackground.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Scrollable HyperOS Workspace Canvas (Widgets + Big Folders + App Grid)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("workspace_scroll_canvas")
            ) {
                // 1. Full-Width HyperOS Widgets (Clock/MTZ Status, MAML Live Duo, Native AppWidgets)
                items(
                    count = fullWidthWidgets.size,
                    key = { idx -> "fw_${fullWidthWidgets[idx].id}" }
                ) { idx ->
                    val widget = fullWidthWidgets[idx]
                    HyperWorkspaceWidgetCard(
                        widget = widget,
                        timeString = timeString,
                        dateString = dateString,
                        activeTheme = activeTheme,
                        engineConfig = engineConfig,
                        telemetry = deviceTelemetry,
                        allApps = allApps,
                        appWidgetHost = appWidgetHost,
                        onOpenMtzStudio = onOpenMtzStudio,
                        onOpenApiPipeline = onOpenApiPipeline,
                        onOpenWidgetsPicker = onOpenWidgetsAndFoldersPicker,
                        onRefreshTelemetry = onRefreshTelemetry,
                        onRemoveWidget = onRemoveWidget,
                        onLaunchApp = onAppClick
                    )
                }

                // 2. Paired 2x2 HyperOS Telemetry & Storage Widgets
                val halfWidgetRows = halfWidthWidgets.chunked(2)
                items(
                    count = halfWidgetRows.size,
                    key = { rowIdx -> "hw_row_$rowIdx" }
                ) { rowIdx ->
                    val rowPair = halfWidgetRows[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowPair.forEach { widget ->
                            HyperWorkspaceWidgetCard(
                                widget = widget,
                                timeString = timeString,
                                dateString = dateString,
                                activeTheme = activeTheme,
                                engineConfig = engineConfig,
                                telemetry = deviceTelemetry,
                                allApps = allApps,
                                appWidgetHost = appWidgetHost,
                                onOpenMtzStudio = onOpenMtzStudio,
                                onOpenApiPipeline = onOpenApiPipeline,
                                onOpenWidgetsPicker = onOpenWidgetsAndFoldersPicker,
                                onRefreshTelemetry = onRefreshTelemetry,
                                onRemoveWidget = onRemoveWidget,
                                onLaunchApp = onAppClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowPair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // 3. HyperOS Big Folders Section Header + Add Button
                item(key = "hyper_folders_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "HyperOS Big Folders",
                                tint = EmeraldBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HyperOS Big Folders",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldContainer)
                                .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenWidgetsAndFoldersPicker)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("add_folder_or_widget_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Big Folder or Widget",
                                tint = EmeraldBright,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Folder / Widget",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Render XXL 3x3 Folders full-width, and pair 2x2 / 2x1 / 1x1 Folders in 2-column rows
                val xxlFolders = workspaceFolders.filter { it.layoutMode == HyperFolderLayoutMode.XXL_FOLDER_3X3 }
                val standardFolders = workspaceFolders.filter { it.layoutMode != HyperFolderLayoutMode.XXL_FOLDER_3X3 }

                items(
                    count = xxlFolders.size,
                    key = { idx -> "xxl_${xxlFolders[idx].id}_${xxlFolders[idx].renderKey}" }
                ) { idx ->
                    val folder = xxlFolders[idx]
                    HyperFolderCard(
                        folder = folder,
                        onDirectLaunchApp = onAppClick,
                        onAppLongClick = onAppLongClick,
                        onExpandFolder = onExpandFolder,
                        onCycleLayoutMode = onCycleFolderMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val folderPairs = standardFolders.chunked(2)
                items(
                    count = folderPairs.size,
                    key = { rowIdx -> "folder_pair_${rowIdx}_${folderPairs[rowIdx].joinToString { it.renderKey }}" }
                ) { rowIdx ->
                    val pair = folderPairs[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        pair.forEach { folder ->
                            HyperFolderCard(
                                folder = folder,
                                onDirectLaunchApp = onAppClick,
                                onAppLongClick = onAppLongClick,
                                onExpandFolder = onExpandFolder,
                                onCycleLayoutMode = onCycleFolderMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // 4. Pinned Home Workspace App Shortcuts Grid (chunked by gridColumns)
                val appRows = workspaceApps.chunked(engineConfig.gridColumns)
                items(
                    count = appRows.size,
                    key = { rowIdx -> "app_row_${rowIdx}_${appRows[rowIdx].firstOrNull()?.renderKey.orEmpty()}" }
                ) { rowIdx ->
                    val rowApps = appRows[rowIdx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(if (rowIdx == 0) "workspace_icon_grid" else "workspace_icon_row_$rowIdx"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until engineConfig.gridColumns) {
                            val app = rowApps.getOrNull(col)
                            if (app != null) {
                                AppIconTile(
                                    app = app,
                                    iconSize = engineConfig.iconSizeDp.dp,
                                    showLabel = engineConfig.showAppLabels,
                                    showApiBadge = engineConfig.showApiSourceBadges,
                                    onClick = { onAppClick(app) },
                                    onLongClick = { onAppLongClick(app) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Frosted Dark Grayish-Blue Launcher Dock
            Surface(
                color = SlateSurfaceElevated.copy(alpha = 0.92f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    .testTag("workspace_dock")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dockApps.forEach { app ->
                        AppIconTile(
                            app = app,
                            iconSize = (engineConfig.iconSizeDp - 4).coerceAtLeast(48).dp,
                            showLabel = false,
                            showApiBadge = engineConfig.showApiSourceBadges,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }

                    // Dedicated Widgets & Folders Trigger in Dock
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(SlateSurface)
                            .border(1.dp, SlateBorder, RoundedCornerShape(15.dp))
                            .clickable(onClick = onOpenWidgetsAndFoldersPicker)
                            .testTag("dock_widgets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Open HyperOS Widgets & Folders",
                            tint = EmeraldBright,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Dedicated App Drawer Trigger in Dock
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(EmeraldContainer)
                            .border(1.dp, EmeraldBright.copy(alpha = 0.6f), RoundedCornerShape(15.dp))
                            .clickable(onClick = onOpenAppDrawer)
                            .testTag("dock_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Open App Drawer",
                            tint = EmeraldBright,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
