package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.IconPipelineBreakdown
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.LauncherEngineConfig
import com.example.ui.components.ResolutionBadgeChip
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CatalogIconEntry
import java.util.Locale

@Composable
fun ApiPipelineScreen(
    allApps: List<InstalledAppItem>,
    inspectedApp: InstalledAppItem?,
    breakdown: IconPipelineBreakdown?,
    engineConfig: LauncherEngineConfig,
    iconCatalog: List<CatalogIconEntry>,
    onSelectAppToInspect: (InstalledAppItem) -> Unit,
    onUpdateScaleOverride: (Boolean, Float) -> Unit,
    onToggleDynamicMasking: (Boolean) -> Unit,
    onToggleThirdPartyColorFilter: (Boolean) -> Unit,
    onToggleFancyMaml: (Boolean) -> Unit,
    onToggleIconBorder: (Boolean) -> Unit,
    onAssignOverrideAsset: (String, String?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
            .padding(horizontal = 16.dp)
            .testTag("api_pipeline_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Xiaomi Icon Theme API Pipeline",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Real-time inspection of Red->White & Green Palette 3rd-Party Shader, PorterDuff DST_IN masking, and MAML fancy_icons",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Target Package Selector Strip
        item {
            Text(
                text = "Select Target Package to Inspect:",
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldBright
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.testTag("pipeline_app_selector")
            ) {
                items(allApps, key = { it.packageName }) { app ->
                    val isSelected = inspectedApp?.packageName == app.packageName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) EmeraldContainer else SlateSurface)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) EmeraldBright else SlateBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectAppToInspect(app) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("select_inspect_${app.packageName}")
                    ) {
                        Image(
                            bitmap = app.themedIconBitmap.asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = app.resolutionSource.code,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) EmeraldBright else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (breakdown != null && inspectedApp != null) {
            // Selected Package Header Card
            item {
                Surface(
                    color = SlateSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = breakdown.finalOutputBitmap.asImageBitmap(),
                                contentDescription = breakdown.appLabel,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = breakdown.appLabel,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ResolutionBadgeChip(source = breakdown.resolutionSource)
                                }
                                Text(
                                    text = breakdown.packageName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Matched: ${breakdown.matchedEntryPath}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright
                                )
                            }
                        }
                    }
                }
            }

            // 6-Stage Xiaomi PorterDuff & Layer Compositing Breakdown
            item {
                Surface(
                    color = SlateSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .testTag("six_stage_pipeline_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "6-Stage Xiaomi Icon Compositing Pipeline (192x192 ARGB_8888)",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldBright
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StageLayerBox(
                                stepNumber = "01",
                                title = "Raw App",
                                subtitle = "PackageManager",
                                bitmap = breakdown.rawAppBitmap,
                                modifier = Modifier.weight(1f)
                            )
                            StageLayerBox(
                                stepNumber = "02",
                                title = "icon_bg.png",
                                subtitle = "SRC_OVER Plate",
                                bitmap = breakdown.backgroundPlateBitmap,
                                modifier = Modifier.weight(1f)
                            )
                            StageLayerBox(
                                stepNumber = "03",
                                title = "Shader+Scale",
                                subtitle = "Red->W / Green",
                                bitmap = breakdown.scaledInputBitmap,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StageLayerBox(
                                stepNumber = "04",
                                title = "icon_mask.png",
                                subtitle = "Alpha Stencil",
                                bitmap = breakdown.maskTemplateBitmap,
                                modifier = Modifier.weight(1f)
                            )
                            StageLayerBox(
                                stepNumber = "05",
                                title = "DST_IN Clip",
                                subtitle = "PorterDuff Masked",
                                bitmap = breakdown.maskedLayerBitmap,
                                modifier = Modifier.weight(1f)
                            )
                            StageLayerBox(
                                stepNumber = "06",
                                title = "Final Output",
                                subtitle = "+ icon_border.png",
                                bitmap = breakdown.finalOutputBitmap,
                                isHighlighted = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Live Xiaomi Theme API Parameter Controls
            item {
                Surface(
                    color = SlateSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Real-Time Xiaomi API Transform Parameters",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val sliderVal = if (engineConfig.useCustomScaleOverride) {
                            engineConfig.customScaleValue
                        } else {
                            breakdown.appliedScale
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "icon_scale (<dimen name=\"icon_scale\">)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (engineConfig.useCustomScaleOverride) {
                                        "Custom Override: ${String.format(Locale.US, "%.2f", sliderVal)}x"
                                    } else {
                                        "From .mtz theme_values.xml: ${String.format(Locale.US, "%.2f", sliderVal)}x"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright
                                )
                            }
                            if (engineConfig.useCustomScaleOverride) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onUpdateScaleOverride(false, 0.78f) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Reset to MTZ XML",
                                        tint = EmeraldBright,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Use XML Default",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldBright
                                    )
                                }
                            }
                        }

                        Slider(
                            value = sliderVal,
                            onValueChange = { onUpdateScaleOverride(true, it) },
                            valueRange = 0.50f..1.00f,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldBright,
                                activeTrackColor = EmeraldPrimary,
                                inactiveTrackColor = SlateSurfaceElevated
                            ),
                            modifier = Modifier.testTag("icon_scale_slider")
                        )

                        HorizontalDivider(color = SlateBorder)
                        Spacer(modifier = Modifier.height(6.dp))

                        PipelineToggleRow(
                            title = "3rd-Party Color Shader: Red -> White, Others -> Green",
                            subtitle = "Converts red shades to crisp white (#ECF3F8), maps all other colors to green palette, and strips white plates",
                            checked = engineConfig.enableThirdPartyColorFilter,
                            onCheckedChange = onToggleThirdPartyColorFilter,
                            tag = "toggle_third_party_color_filter"
                        )

                        PipelineToggleRow(
                            title = "PorterDuff DST_IN Dynamic Masking",
                            subtitle = "Clips unthemed 3rd-party icons using icon_bg.png & icon_mask.png",
                            checked = engineConfig.enableDynamicMasking,
                            onCheckedChange = onToggleDynamicMasking,
                            tag = "toggle_dynamic_masking"
                        )

                        PipelineToggleRow(
                            title = "Xiaomi MAML fancy_icons Engine",
                            subtitle = "Evaluates fancy_icons/<pkg>/manifest.xml live clock & calendar expressions",
                            checked = engineConfig.enableFancyMamlIcons,
                            onCheckedChange = onToggleFancyMaml,
                            tag = "toggle_fancy_maml"
                        )

                        PipelineToggleRow(
                            title = "Foreground icon_border.png Overlay",
                            subtitle = "Composites specular rim/border layer via PorterDuff SRC_OVER",
                            checked = engineConfig.enableIconBorderOverlay,
                            onCheckedChange = onToggleIconBorder,
                            tag = "toggle_icon_border"
                        )
                    }
                }
            }

            // Manual Per-App Icon Mapping Strip
            item {
                Surface(
                    color = SlateSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Map ${inspectedApp.label} to Specific .MTZ Icon",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            if (inspectedApp.customOverrideAssetKey != null) {
                                Text(
                                    text = "Clear Override",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldBright,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onAssignOverrideAsset(inspectedApp.packageName, null) }
                                        .padding(4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(iconCatalog, key = { it.key }) { item ->
                                val isMapped = inspectedApp.customOverrideAssetKey == item.key
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isMapped) EmeraldContainer else SlateSurfaceElevated)
                                        .border(
                                            width = if (isMapped) 1.5.dp else 1.dp,
                                            color = if (isMapped) EmeraldBright else SlateBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onAssignOverrideAsset(inspectedApp.packageName, item.key) }
                                        .padding(6.dp)
                                ) {
                                    Image(
                                        bitmap = item.bitmap.asImageBitmap(),
                                        contentDescription = item.key,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.key.substringAfterLast('.'),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // XML Diagnostic Trace Card
            item {
                Surface(
                    color = SlateSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Active XML Manifest / Pipeline Trace",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldBright
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = SlateDeepBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = breakdown.xmlDiagnosticSnippet,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageLayerBox(
    stepNumber: String,
    title: String,
    subtitle: String,
    bitmap: Bitmap?,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isHighlighted) EmeraldContainer else SlateDeepBackground)
            .border(
                width = if (isHighlighted) 1.5.dp else 1.dp,
                color = if (isHighlighted) EmeraldBright else SlateBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = "STAGE $stepNumber",
            style = MaterialTheme.typography.labelSmall,
            color = EmeraldBright,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurfaceElevated)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Text(
                    text = "N/A",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PipelineToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SlateDeepBackground,
                checkedTrackColor = EmeraldBright,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SlateSurfaceElevated
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
