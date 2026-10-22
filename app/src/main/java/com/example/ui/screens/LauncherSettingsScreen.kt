package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.LauncherEngineConfig
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LauncherSettingsScreen(
    engineConfig: LauncherEngineConfig,
    onSetDefaultHomeLauncher: () -> Unit,
    onUpdateGridColumns: (Int) -> Unit,
    onUpdateIconSizeDp: (Int) -> Unit,
    onToggleShowLabels: (Boolean) -> Unit,
    onToggleShowApiBadges: (Boolean) -> Unit,
    onToggleMultiBgHash: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
            .padding(horizontal = 16.dp)
            .testTag("launcher_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Launcher Grid & System Architecture",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Configure Android HOME role registration, workspace density, and Xiaomi MTZ engine behavior",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Default Android Home Launcher Role Card
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Android Home Launcher Integration",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "HyperMTZ declares android.intent.category.HOME and DEFAULT in AndroidManifest.xml. You can bind it as your system default home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onSetDefaultHomeLauncher,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("set_default_launcher_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Set Default Home",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set as Default Home Launcher",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Workspace Grid & Density Controls
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Workspace Grid & Icon Geometry",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Workspace Columns",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(4, 5).forEach { cols ->
                            val selected = engineConfig.gridColumns == cols
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
                                    .clickable { onUpdateGridColumns(cols) }
                                    .testTag("grid_cols_$cols")
                            ) {
                                Text(
                                    text = "$cols Columns",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) EmeraldBright else TextPrimary,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rendered Icon Size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${engineConfig.iconSizeDp} dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldBright
                        )
                    }
                    Slider(
                        value = engineConfig.iconSizeDp.toFloat(),
                        onValueChange = { onUpdateIconSizeDp(it.toInt()) },
                        valueRange = 48f..68f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldBright,
                            activeTrackColor = EmeraldPrimary,
                            inactiveTrackColor = SlateSurfaceElevated
                        ),
                        modifier = Modifier.testTag("icon_size_slider")
                    )

                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(6.dp))

                    SettingsSwitchItem(
                        title = "Display Application Labels on Workspace",
                        subtitle = "Show package title beneath each themed icon on the home grid",
                        checked = engineConfig.showAppLabels,
                        onCheckedChange = onToggleShowLabels,
                        tag = "toggle_show_labels"
                    )

                    SettingsSwitchItem(
                        title = "Display Xiaomi API Resolution Badges",
                        subtitle = "Overlay MAML / PNG / MASK / OVR stage tags on workspace icons",
                        checked = engineConfig.showApiSourceBadges,
                        onCheckedChange = onToggleShowApiBadges,
                        tag = "toggle_show_badges"
                    )

                    SettingsSwitchItem(
                        title = "Multi-Plate icon_bg_0..n Deterministic Hashing",
                        subtitle = "Assigns package-consistent background plates when an .mtz includes multiple icon_bg_N.png files",
                        checked = engineConfig.enableMultiBgHashSelection,
                        onCheckedChange = onToggleMultiBgHash,
                        tag = "toggle_multi_bg_hash"
                    )
                }
            }
        }

        // Architectural Reference Card
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Xiaomi MIUI / HyperOS .MTZ Architecture Reference",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Archive Structure: Outer .mtz/.mtx ZIP containing description.xml, wallpaper/, and an inner 'icons' ZIP payload.\n" +
                            "2. Direct Mapping: res/drawable-xxhdpi/<package_or_component>.png matched against PackageManager activities and Xiaomi package aliases.\n" +
                            "3. Dynamic Masking API: Unthemed apps are scaled by <dimen name=\"icon_scale\"> from theme_values.xml, clipped against icon_mask.png using PorterDuff.Mode.DST_IN, placed over icon_bg.png, and framed by icon_border.png.\n" +
                            "4. MAML fancy_icons: Evaluates fancy_icons/<pkg>/manifest.xml expressions (#hour12, #minute, #second, #date) for live dynamic icons.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
