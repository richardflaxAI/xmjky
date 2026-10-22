package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MtzThemeEntity
import com.example.domain.model.MtzThemeMetadata
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CatalogIconEntry
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MtzStudioScreen(
    themes: List<MtzThemeEntity>,
    activeThemeId: String,
    activeThemeMetadata: MtzThemeMetadata?,
    activeThemeCatalog: List<CatalogIconEntry>,
    onActivateTheme: (String) -> Unit,
    onRequestImportMtz: () -> Unit,
    onRequestExportMtz: (MtzThemeEntity) -> Unit,
    onDeleteCustomTheme: (String) -> Unit
) {
    var showXmlSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("mtz_studio_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Xiaomi .MTZ / .MTX Theme Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Unpacks MIUI & HyperOS .mtz archives, nested icons ZIPs, theme_values.xml, and MAML fancy_icons",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Import & Active XML Spec Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRequestImportMtz,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_mtz_archive_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Import .MTZ",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import .MTZ / .MTX", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showXmlSheet = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("view_active_mtz_xml_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Inspect XML",
                    tint = EmeraldBright,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Active XML Specs", color = EmeraldBright)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("mtz_theme_list")
        ) {
            // Active Theme Extracted Icon Catalog Preview
            if (activeThemeMetadata != null && activeThemeCatalog.isNotEmpty()) {
                item {
                    Surface(
                        color = SlateSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Extracted Icon Pack: ${activeThemeMetadata.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldBright
                                )
                                Text(
                                    text = "${activeThemeCatalog.size} PNGs + ${activeThemeMetadata.fancyIconSpecs.size} MAML",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(activeThemeCatalog, key = { it.key }) { item ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SlateSurfaceElevated)
                                            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                            .padding(6.dp)
                                    ) {
                                        Image(
                                            bitmap = item.bitmap.asImageBitmap(),
                                            contentDescription = item.key,
                                            modifier = Modifier.size(42.dp)
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
            }

            // Installed / Built-In .MTZ Packages
            items(themes, key = { it.id }) { theme ->
                val isActive = theme.id == activeThemeId
                Surface(
                    color = if (isActive) SlateSurfaceElevated else SlateSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) EmeraldBright else SlateBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag("theme_card_${theme.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = theme.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = EmeraldContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Active",
                                                    tint = EmeraldBright,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "ACTIVE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldBright
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Archive: ${theme.archiveFileName} | Author: ${theme.author} | ${theme.uiVersion}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }

                            if (!theme.isBuiltIn) {
                                IconButton(
                                    onClick = { onDeleteCustomTheme(theme.id) },
                                    modifier = Modifier.testTag("delete_theme_${theme.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Custom MTZ",
                                        tint = ErrorCrimson
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = theme.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary.copy(alpha = 0.9f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Technical Xiaomi API Capabilities Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetricPill("icon_scale", String.format(Locale.US, "%.2fx", theme.iconScale))
                            TechMetricPill("Direct PNG", "${theme.directIconCount}")
                            TechMetricPill("MAML Fancy", "${theme.fancyIconCount}")
                            TechMetricPill(
                                "Mask API",
                                if (theme.hasMask && theme.hasBg) "BG+MASK+BDR" else "PARTIAL"
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onActivateTheme(theme.id) },
                                enabled = !isActive,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary,
                                    disabledContainerColor = EmeraldContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("apply_theme_${theme.id}")
                            ) {
                                Text(
                                    text = if (isActive) "Currently Applied" else "Apply .MTZ Theme",
                                    color = if (isActive) EmeraldBright else SlateDeepBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (theme.isBuiltIn) {
                                OutlinedButton(
                                    onClick = { onRequestExportMtz(theme) },
                                    modifier = Modifier.testTag("export_theme_${theme.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Export .MTZ",
                                        tint = EmeraldBright,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export .MTZ", color = EmeraldBright)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showXmlSheet && activeThemeMetadata != null) {
        ModalBottomSheet(
            onDismissRequest = { showXmlSheet = false },
            containerColor = SlateSurface,
            contentColor = TextPrimary
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = "${activeThemeMetadata.archiveFileName} — Xiaomi XML Specifications",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldBright
                    )
                }
                item {
                    XmlBlockCard(title = "description.xml", xml = activeThemeMetadata.rawDescriptionXml)
                }
                item {
                    XmlBlockCard(title = "icons/theme_values.xml", xml = activeThemeMetadata.rawThemeValuesXml)
                }
                item {
                    XmlBlockCard(title = "icons/transform_config.xml", xml = activeThemeMetadata.rawTransformConfigXml)
                }
            }
        }
    }
}

@Composable
private fun TechMetricPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateDeepBackground)
            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = EmeraldBright,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun XmlBlockCard(title: String, xml: String) {
    Surface(
        color = SlateDeepBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldBright
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = xml,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
        }
    }
}
