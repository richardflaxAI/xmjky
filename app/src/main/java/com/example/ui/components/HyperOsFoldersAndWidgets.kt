package com.example.ui.components

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.HyperFolderLayoutMode
import com.example.domain.model.HyperWidgetType
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MtzThemeMetadata
import com.example.domain.model.SystemWidgetProviderItem
import com.example.domain.model.WorkspaceFolder
import com.example.domain.model.WorkspaceWidgetItem
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.SageSecondary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Renders a Xiaomi HyperOS Folder Card supporting 4 distinct layout modes:
 * - BIG_FOLDER_2X2: 3 direct-launch slots + 4th bottom-right 2x2 mini-cluster expansion slot
 * - XXL_FOLDER_3X3: 8 direct-launch slots + 9th bottom-right mini-cluster expansion slot
 * - CAPSULE_2X1: 2 direct-launch slots + 3rd right-side mini-cluster expansion slot
 * - REGULAR_1X1: Compact 2x2 preview that opens the expanded folder when tapped
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HyperFolderCard(
    folder: WorkspaceFolder,
    onDirectLaunchApp: (InstalledAppItem) -> Unit,
    onAppLongClick: (InstalledAppItem) -> Unit,
    onExpandFolder: (WorkspaceFolder) -> Unit,
    onCycleLayoutMode: (WorkspaceFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .border(1.dp, EmeraldPrimary.copy(alpha = 0.40f), RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = { onExpandFolder(folder) },
                onLongClick = { onExpandFolder(folder) }
            )
            .testTag("hyper_folder_${folder.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Folder Header with Title and HyperOS Layout Mode Switch Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = EmeraldContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .border(0.75.dp, EmeraldBright.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onCycleLayoutMode(folder) }
                        .testTag("folder_mode_badge_${folder.id}")
                ) {
                    Text(
                        text = folder.layoutMode.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBright,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (folder.layoutMode) {
                HyperFolderLayoutMode.BIG_FOLDER_2X2 -> {
                    // 2x2 Layout: Top-Left, Top-Right, Bottom-Left are direct-launch; Bottom-Right is 2x2 mini-cluster
                    val slot0 = folder.apps.getOrNull(0)
                    val slot1 = folder.apps.getOrNull(1)
                    val slot2 = folder.apps.getOrNull(2)
                    val clusterApps = folder.apps.drop(3)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DirectFolderSlot(
                                app = slot0,
                                iconSize = 48.dp,
                                onTap = { slot0?.let(onDirectLaunchApp) },
                                onLongTap = { slot0?.let(onAppLongClick) }
                            )
                            DirectFolderSlot(
                                app = slot1,
                                iconSize = 48.dp,
                                onTap = { slot1?.let(onDirectLaunchApp) },
                                onLongTap = { slot1?.let(onAppLongClick) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DirectFolderSlot(
                                app = slot2,
                                iconSize = 48.dp,
                                onTap = { slot2?.let(onDirectLaunchApp) },
                                onLongTap = { slot2?.let(onAppLongClick) }
                            )
                            MiniClusterExpansionSlot(
                                overflowApps = clusterApps,
                                slotSize = 54.dp,
                                onExpand = { onExpandFolder(folder) }
                            )
                        }
                    }
                }

                HyperFolderLayoutMode.CAPSULE_2X1 -> {
                    // Horizontal 2x1 Capsule: 2 direct-launch icons + 3rd mini-cluster expansion slot
                    val slot0 = folder.apps.getOrNull(0)
                    val slot1 = folder.apps.getOrNull(1)
                    val clusterApps = folder.apps.drop(2)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DirectFolderSlot(
                            app = slot0,
                            iconSize = 46.dp,
                            onTap = { slot0?.let(onDirectLaunchApp) },
                            onLongTap = { slot0?.let(onAppLongClick) }
                        )
                        DirectFolderSlot(
                            app = slot1,
                            iconSize = 46.dp,
                            onTap = { slot1?.let(onDirectLaunchApp) },
                            onLongTap = { slot1?.let(onAppLongClick) }
                        )
                        MiniClusterExpansionSlot(
                            overflowApps = clusterApps,
                            slotSize = 50.dp,
                            onExpand = { onExpandFolder(folder) }
                        )
                    }
                }

                HyperFolderLayoutMode.XXL_FOLDER_3X3 -> {
                    // 3x3 Super Folder: Up to 8 direct-launch icons + 9th mini-cluster expansion slot
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0 until 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (col in 0 until 3) {
                                    val index = row * 3 + col
                                    if (index < 8) {
                                        val app = folder.apps.getOrNull(index)
                                        DirectFolderSlot(
                                            app = app,
                                            iconSize = 42.dp,
                                            onTap = { app?.let(onDirectLaunchApp) },
                                            onLongTap = { app?.let(onAppLongClick) }
                                        )
                                    } else {
                                        MiniClusterExpansionSlot(
                                            overflowApps = folder.apps.drop(8),
                                            slotSize = 46.dp,
                                            onExpand = { onExpandFolder(folder) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HyperFolderLayoutMode.REGULAR_1X1 -> {
                    // Compact 1x1 Folder: Tapping anywhere opens the expanded modal
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MiniClusterExpansionSlot(
                            overflowApps = folder.apps,
                            slotSize = 58.dp,
                            onExpand = { onExpandFolder(folder) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DirectFolderSlot(
    app: InstalledAppItem?,
    iconSize: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    if (app == null) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(12.dp))
                .background(SlateSurfaceElevated.copy(alpha = 0.45f))
        )
    } else {
        val iconBitmap = remember(app.renderKey, app.themedIconBitmap) {
            app.themedIconBitmap.asImageBitmap()
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(iconSize + 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongTap
                )
                .padding(2.dp)
                .testTag("folder_direct_app_${app.packageName}")
        ) {
            Image(
                bitmap = iconBitmap,
                contentDescription = app.label,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniClusterExpansionSlot(
    overflowApps: List<InstalledAppItem>,
    slotSize: androidx.compose.ui.unit.Dp,
    onExpand: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(slotSize)
            .clip(RoundedCornerShape(14.dp))
            .background(SlateSurfaceElevated)
            .border(1.dp, EmeraldPrimary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onExpand)
            .padding(5.dp)
    ) {
        if (overflowApps.isEmpty()) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = "Expand Folder",
                tint = EmeraldBright,
                modifier = Modifier.size(20.dp)
            )
        } else {
            val miniSize = (slotSize - 16.dp) / 2
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MiniIconThumb(overflowApps.getOrNull(0), miniSize)
                    MiniIconThumb(overflowApps.getOrNull(1), miniSize)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MiniIconThumb(overflowApps.getOrNull(2), miniSize)
                    MiniIconThumb(overflowApps.getOrNull(3), miniSize)
                }
            }
        }
    }
}

@Composable
private fun MiniIconThumb(app: InstalledAppItem?, size: androidx.compose.ui.unit.Dp) {
    if (app != null) {
        val iconBitmap = remember(app.renderKey, app.themedIconBitmap) {
            app.themedIconBitmap.asImageBitmap()
        }
        Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            modifier = Modifier.size(size)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(SlateDeepBackground.copy(alpha = 0.5f))
        )
    }
}

/**
 * Renders an active HyperOS Widget or Native Android System AppWidget on the Home Workspace.
 */
@Composable
fun HyperWorkspaceWidgetCard(
    widget: WorkspaceWidgetItem,
    timeString: String,
    dateString: String,
    activeTheme: MtzThemeMetadata?,
    engineConfig: LauncherEngineConfig,
    telemetry: DeviceTelemetrySnapshot,
    allApps: List<InstalledAppItem>,
    appWidgetHost: AppWidgetHost?,
    onOpenMtzStudio: () -> Unit,
    onOpenApiPipeline: () -> Unit,
    onOpenWidgetsPicker: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    onRemoveWidget: (String) -> Unit,
    onLaunchApp: (InstalledAppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(20.dp))
            .testTag("workspace_widget_${widget.id}")
    ) {
        when (widget.widgetType) {
            HyperWidgetType.CLOCK_MTZ_STATUS -> {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.displayMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = dateString,
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldBright
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenMtzStudio)
                                    .testTag("active_mtz_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Style,
                                        contentDescription = "Active MTZ",
                                        tint = EmeraldBright,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = activeTheme?.title ?: "HyperEmerald OS",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = EmeraldBright
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val effectiveScale = if (engineConfig.useCustomScaleOverride) {
                                engineConfig.customScaleValue
                            } else {
                                activeTheme?.iconScale ?: 0.78f
                            }
                            Text(
                                text = "MTZ scale=${String.format(Locale.US, "%.2f", effectiveScale)}x | MAML=${if (engineConfig.enableFancyMamlIcons) "ON" else "OFF"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetQuickChip(
                            label = "MTZ Studio",
                            icon = Icons.Default.Layers,
                            onClick = onOpenMtzStudio,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_open_mtz_studio")
                        )
                        WidgetQuickChip(
                            label = "Icon Pipeline",
                            icon = Icons.Default.Tune,
                            onClick = onOpenApiPipeline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_open_api_pipeline")
                        )
                        WidgetQuickChip(
                            label = "Widgets & Folders",
                            icon = Icons.Default.Widgets,
                            onClick = onOpenWidgetsPicker,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_open_widgets_picker")
                        )
                    }
                }
            }

            HyperWidgetType.DEVICE_TELEMETRY -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "Battery",
                                tint = EmeraldBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "HyperOS Telemetry",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldBright
                            )
                        }
                        Row {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Telemetry",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(onClick = onRefreshTelemetry)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Widget",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onRemoveWidget(widget.id) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Battery Progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (telemetry.isCharging) "Battery (Charging)" else "Battery",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${telemetry.batteryPercent}% (${String.format(Locale.US, "%.1f", telemetry.batteryTempCelsius)}C)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldBright
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (telemetry.batteryPercent / 100f).coerceIn(0.05f, 1f) },
                        color = EmeraldPrimary,
                        trackColor = SlateSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // RAM Progress
                    val ramRatio = (telemetry.usedRamMb.toFloat() / telemetry.totalRamMb.coerceAtLeast(1L)).coerceIn(0.05f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "System RAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${telemetry.usedRamMb} / ${telemetry.totalRamMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MintTertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { ramRatio },
                        color = MintTertiary,
                        trackColor = SlateSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            HyperWidgetType.STORAGE_AND_PACKS -> {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Storage",
                                tint = SageSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Storage & MTZ Matrix",
                                style = MaterialTheme.typography.labelLarge,
                                color = SageSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Widget",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemoveWidget(widget.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val storageRatio = (telemetry.usedStorageGb / telemetry.totalStorageGb.coerceAtLeast(1f)).coerceIn(0.05f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Data Partition",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f / %.0f GB", telemetry.usedStorageGb, telemetry.totalStorageGb),
                            style = MaterialTheme.typography.labelSmall,
                            color = SageSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { storageRatio },
                        color = SageSecondary,
                        trackColor = SlateSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val mamlCount = allApps.count { it.resolutionSource == IconResolutionSource.MTZ_FANCY_MAML }
                    val directCount = allApps.count { it.resolutionSource == IconResolutionSource.MTZ_DIRECT_MAP }
                    val maskCount = allApps.count { it.resolutionSource == IconResolutionSource.MTZ_DYNAMIC_MASK }

                    Text(
                        text = "MTZ Engine: $directCount PNG | $mamlCount MAML | $maskCount Masked",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HyperWidgetType.MAML_LIVE_DUO -> {
                val clockApp = allApps.firstOrNull { it.packageName.contains("deskclock") }
                val calApp = allApps.firstOrNull { it.packageName.contains("calendar") }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (clockApp != null) {
                            Image(
                                bitmap = clockApp.themedIconBitmap.asImageBitmap(),
                                contentDescription = "MAML Clock",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { onLaunchApp(clockApp) }
                            )
                        }
                        if (calApp != null) {
                            Image(
                                bitmap = calApp.themedIconBitmap.asImageBitmap(),
                                contentDescription = "MAML Calendar",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { onLaunchApp(calApp) }
                            )
                        }
                        Column {
                            Text(
                                text = "Xiaomi MAML Live Engine",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldBright
                            )
                            Text(
                                text = "fancy_icons/manifest.xml (#hour12, #minute, #date)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove Widget",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onRemoveWidget(widget.id) }
                    )
                }
            }

            HyperWidgetType.NATIVE_APPWIDGET -> {
                val context = LocalContext.current
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = widget.customTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldBright
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Native Widget",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemoveWidget(widget.id) }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val providerComp = widget.providerComponent?.let { ComponentName.unflattenFromString(it) }
                    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
                    val providerInfo = remember(widget.appWidgetId, providerComp) {
                        if (widget.appWidgetId >= 0) {
                            appWidgetManager.getAppWidgetInfo(widget.appWidgetId)
                        } else {
                            appWidgetManager.installedProviders.firstOrNull { it.provider == providerComp }
                        }
                    }

                    if (appWidgetHost != null && widget.appWidgetId >= 0 && providerInfo != null) {
                        AndroidView(
                            factory = { ctx ->
                                appWidgetHost.createView(ctx, widget.appWidgetId, providerInfo).apply {
                                    setAppWidget(widget.appWidgetId, providerInfo)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        )
                    } else {
                        Text(
                            text = "Provider: ${widget.providerComponent ?: "System AppWidget"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetQuickChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurfaceElevated)
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = EmeraldBright,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Modal Bottom Sheet for viewing an expanded HyperOS Big Folder, switching its size mode
 * (`1x1`, `2x1 Capsule`, `2x2 Big Folder`, `3x3 XXL`), renaming it, or editing its apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedHyperFolderSheet(
    folder: WorkspaceFolder,
    allApps: List<InstalledAppItem>,
    onDismiss: () -> Unit,
    onLaunchApp: (InstalledAppItem) -> Unit,
    onSelectLayoutMode: (HyperFolderLayoutMode) -> Unit,
    onRenameFolder: (String) -> Unit,
    onToggleAppMembership: (String) -> Unit,
    onDeleteFolder: () -> Unit
) {
    var isEditingApps by remember { mutableStateOf(false) }
    var titleInput by remember(folder.title) { mutableStateOf(folder.title) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "HyperOS ${folder.layoutMode.displayName} (${folder.apps.size} apps)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldBright
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { isEditingApps = !isEditingApps },
                        modifier = Modifier.testTag("folder_sheet_edit_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Folder",
                            tint = EmeraldBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEditingApps) "Done" else "Edit Apps",
                            color = EmeraldBright
                        )
                    }

                    IconButton(
                        onClick = {
                            onDeleteFolder()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Folder",
                            tint = ErrorCrimson
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // HyperOS Folder Size Mode Selector Strip (1x1, 2x1 Capsule, 2x2 Big Folder, 3x3 XXL)
            Text(
                text = "HyperOS Folder Layout Mode:",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HyperFolderLayoutMode.entries.forEach { mode ->
                    val selected = folder.layoutMode == mode
                    Surface(
                        color = if (selected) EmeraldContainer else SlateSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) EmeraldBright else SlateBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLayoutMode(mode) }
                            .testTag("select_folder_mode_${mode.code}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = mode.code,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) EmeraldBright else TextPrimary
                            )
                            Text(
                                text = mode.displayName.substringAfter(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))

            if (isEditingApps) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = {
                        titleInput = it
                        onRenameFolder(it)
                    },
                    label = { Text("Folder Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tap packages to add or remove from this HyperOS folder:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.height(260.dp)
                ) {
                    items(allApps, key = { it.packageName }) { app ->
                        val inFolder = app.packageName in folder.packageNames
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (inFolder) EmeraldContainer else SlateSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (inFolder) EmeraldBright else SlateBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onToggleAppMembership(app.packageName) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Image(
                                bitmap = app.themedIconBitmap.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (inFolder) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Included",
                                    tint = EmeraldBright
                                )
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(folder.apps, key = { it.packageName }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onLaunchApp(app)
                                }
                                .padding(6.dp)
                        ) {
                            Image(
                                bitmap = app.themedIconBitmap.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal Bottom Sheet for adding HyperOS Big Folders, Built-in HyperOS Telemetry Widgets,
 * or Native Android System AppWidgetProviders to the Home Workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsAndFoldersPickerSheet(
    allApps: List<InstalledAppItem>,
    systemWidgetProviders: List<SystemWidgetProviderItem>,
    onDismiss: () -> Unit,
    onCreateFolder: (String, HyperFolderLayoutMode, List<String>) -> Unit,
    onAddBuiltInWidget: (HyperWidgetType) -> Unit,
    onBindSystemWidget: (SystemWidgetProviderItem) -> Unit
) {
    var folderTitle by remember { mutableStateOf("HyperOS Folder") }
    var selectedMode by remember { mutableStateOf(HyperFolderLayoutMode.BIG_FOLDER_2X2) }
    val selectedPackages = remember {
        mutableStateListOf<String>().apply {
            addAll(allApps.take(5).map { it.packageName })
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = "HyperOS Big Folders & Widgets Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Create interactive 2x2/3x3/2x1 HyperOS Big Folders or pin real-time system widgets",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Section 1: Create HyperOS Big Folder
            item {
                Surface(
                    color = SlateDeepBackground,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "Create Big Folder",
                                tint = EmeraldBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create HyperOS Big Folder",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldBright
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = folderTitle,
                            onValueChange = { folderTitle = it },
                            label = { Text("Folder Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = SlateBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_folder_title_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HyperFolderLayoutMode.entries.forEach { mode ->
                                val isChosen = selectedMode == mode
                                Surface(
                                    color = if (isChosen) EmeraldContainer else SlateSurfaceElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            if (isChosen) EmeraldBright else SlateBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedMode = mode }
                                ) {
                                    Text(
                                        text = mode.code,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isChosen) EmeraldBright else TextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Select Apps (${selectedPackages.size} selected):",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(allApps, key = { it.packageName }) { app ->
                                val picked = app.packageName in selectedPackages
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (picked) EmeraldContainer else SlateSurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (picked) EmeraldBright else SlateBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            if (picked) selectedPackages.remove(app.packageName)
                                            else selectedPackages.add(app.packageName)
                                        }
                                        .padding(6.dp)
                                ) {
                                    Image(
                                        bitmap = app.themedIconBitmap.asImageBitmap(),
                                        contentDescription = app.label,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onCreateFolder(folderTitle, selectedMode, selectedPackages.toList())
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_create_big_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Folder",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add ${selectedMode.displayName} to Workspace",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Section 2: Built-In HyperOS Telemetry & MAML Widgets
            item {
                Text(
                    text = "HyperOS Telemetry & MAML Widgets",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            val builtInTypes = listOf(
                HyperWidgetType.CLOCK_MTZ_STATUS,
                HyperWidgetType.DEVICE_TELEMETRY,
                HyperWidgetType.STORAGE_AND_PACKS,
                HyperWidgetType.MAML_LIVE_DUO
            )

            items(builtInTypes, key = { it.code }) { wType ->
                Surface(
                    color = SlateDeepBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            onAddBuiltInWidget(wType)
                            onDismiss()
                        }
                        .testTag("add_builtin_widget_${wType.code}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = wType.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldBright
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = SlateSurfaceElevated,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = wType.badgeSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = wType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Widget",
                            tint = EmeraldBright
                        )
                    }
                }
            }

            // Section 3: Native Android System AppWidgetProviders
            if (systemWidgetProviders.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android System AppWidgetProviders (${systemWidgetProviders.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                items(systemWidgetProviders, key = { it.providerComponent }) { provider ->
                    Surface(
                        color = SlateDeepBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                onBindSystemWidget(provider)
                                onDismiss()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Image(
                                bitmap = provider.iconBitmap.asImageBitmap(),
                                contentDescription = provider.label,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${provider.packageName} (${provider.minWidthDp}x${provider.minHeightDp}dp)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Bind System Widget",
                                tint = EmeraldBright
                            )
                        }
                    }
                }
            }
        }
    }
}
