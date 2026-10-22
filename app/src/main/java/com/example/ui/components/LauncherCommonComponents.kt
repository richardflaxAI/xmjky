package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.InstalledAppItem
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintContainer
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.SageContainer
import com.example.ui.theme.SageSecondary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CatalogIconEntry

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconTile(
    app: InstalledAppItem,
    iconSize: Dp,
    showLabel: Boolean,
    showApiBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("app_tile_${app.packageName}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val imageBitmap = androidx.compose.runtime.remember(app.renderKey, app.themedIconBitmap) {
            app.themedIconBitmap.asImageBitmap()
        }
        Box(contentAlignment = Alignment.BottomEnd) {
            Image(
                bitmap = imageBitmap,
                contentDescription = app.label,
                modifier = Modifier.size(iconSize)
            )
            if (showApiBadge) {
                Surface(
                    color = SlateSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.border(0.75.dp, EmeraldPrimary.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = shortCodeForSource(app.resolutionSource),
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBright,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(6.dp))
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

@Composable
fun ResolutionBadgeChip(
    source: IconResolutionSource,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (source) {
        IconResolutionSource.MTZ_FANCY_MAML -> EmeraldContainer to EmeraldBright
        IconResolutionSource.MTZ_DIRECT_MAP -> SageContainer to SageSecondary
        IconResolutionSource.MTZ_DYNAMIC_MASK -> MintContainer to MintTertiary
        IconResolutionSource.MANUAL_OVERRIDE -> EmeraldContainer to EmeraldPrimary
        IconResolutionSource.SYSTEM_RAW -> SlateSurfaceElevated to TextSecondary
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = source.code,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun shortCodeForSource(source: IconResolutionSource): String = when (source) {
    IconResolutionSource.MANUAL_OVERRIDE -> "OVR"
    IconResolutionSource.MTZ_FANCY_MAML -> "MAML"
    IconResolutionSource.MTZ_DIRECT_MAP -> "PNG"
    IconResolutionSource.MTZ_DYNAMIC_MASK -> "MASK"
    IconResolutionSource.SYSTEM_RAW -> "RAW"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionBottomSheet(
    app: InstalledAppItem,
    iconCatalog: List<CatalogIconEntry>,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onInspectPipeline: () -> Unit,
    onToggleWorkspacePin: () -> Unit,
    onToggleDockPin: () -> Unit,
    onSelectOverrideAsset: (String?) -> Unit
) {
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
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = app.themedIconBitmap.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ResolutionBadgeChip(source = app.resolutionSource)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = app.matchedAssetName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onLaunch()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_launch_app_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Launch",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch App", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onInspectPipeline()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_inspect_pipeline_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Inspect API Pipeline",
                        tint = EmeraldBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inspect API", color = EmeraldBright)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onToggleWorkspacePin()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Workspace Pin",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (app.isPinnedToWorkspace) "Unpin Home" else "Pin to Home",
                        color = TextPrimary
                    )
                }

                OutlinedButton(
                    onClick = {
                        onToggleDockPin()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Dock Pin",
                        tint = EmeraldBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (app.isPinnedToDock) "Unpin Dock" else "Pin to Dock",
                        color = EmeraldBright
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manual .MTZ Icon Override (res/drawable-xxhdpi)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                if (app.customOverrideAssetKey != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onSelectOverrideAsset(null)
                                onDismiss()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Reset Override",
                            tint = EmeraldBright,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset Auto",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldBright
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(iconCatalog, key = { it.key }) { entry ->
                    val isSelected = app.customOverrideAssetKey == entry.key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) EmeraldContainer else SlateSurfaceElevated)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) EmeraldBright else SlateBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                onSelectOverrideAsset(entry.key)
                                onDismiss()
                            }
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = entry.bitmap.asImageBitmap(),
                            contentDescription = entry.key,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.key.substringAfterLast('.'),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
