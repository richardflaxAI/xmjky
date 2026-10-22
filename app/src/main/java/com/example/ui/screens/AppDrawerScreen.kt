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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.InstalledAppItem
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

@Composable
fun AppDrawerScreen(
    allApps: List<InstalledAppItem>,
    searchQuery: String,
    activeSourceFilter: IconResolutionSource?,
    onSearchQueryChange: (String) -> Unit,
    onSourceFilterSelect: (IconResolutionSource?) -> Unit,
    onLaunchApp: (InstalledAppItem) -> Unit,
    onInspectApp: (InstalledAppItem) -> Unit,
    onOpenAppOptions: (InstalledAppItem) -> Unit
) {
    val filteredApps = remember(allApps, searchQuery, activeSourceFilter) {
        allApps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesSource = activeSourceFilter == null || app.resolutionSource == activeSourceFilter
            matchesQuery && matchesSource
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("app_drawer_screen")
    ) {
        Text(
            text = "Installed Applications & MTZ Resolution",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = "${filteredApps.size} of ${allApps.size} packages resolved via Xiaomi Icon Theme API",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "Filter by app name or package ID...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = EmeraldBright
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = TextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SlateSurface,
                unfocusedContainerColor = SlateSurface,
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = SlateBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Xiaomi API Resolution Source Filter Strip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = activeSourceFilter == null,
                    onClick = { onSourceFilterSelect(null) },
                    label = { Text("All (${allApps.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldBright,
                        containerColor = SlateSurface,
                        labelColor = TextSecondary
                    )
                )
            }
            items(IconResolutionSource.entries) { source ->
                val count = allApps.count { it.resolutionSource == source }
                FilterChip(
                    selected = activeSourceFilter == source,
                    onClick = { onSourceFilterSelect(source) },
                    label = { Text("${source.code} ($count)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldBright,
                        containerColor = SlateSurface,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredApps.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = "No matching packages found for current filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("drawer_app_list")
            ) {
                items(filteredApps, key = { "${it.packageName}_${it.renderKey}" }) { app ->
                    val themedImg = remember(app.renderKey, app.themedIconBitmap) {
                        app.themedIconBitmap.asImageBitmap()
                    }
                    Surface(
                        color = SlateSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onLaunchApp(app) }
                            .testTag("drawer_item_${app.packageName}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            // Raw vs Themed side-by-side comparison thumbnail
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Image(
                                    bitmap = themedImg,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(50.dp)
                                )
                                Image(
                                    bitmap = app.rawIconBitmap.asImageBitmap(),
                                    contentDescription = "Raw Icon",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(SlateSurfaceElevated)
                                        .border(1.dp, SlateBorder, RoundedCornerShape(5.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ResolutionBadgeChip(source = app.resolutionSource)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "MTZ Asset: ${app.matchedAssetName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onInspectApp(app) },
                                modifier = Modifier.testTag("inspect_btn_${app.packageName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "Inspect in API Pipeline",
                                    tint = EmeraldBright
                                )
                            }

                            IconButton(
                                onClick = { onOpenAppOptions(app) },
                                modifier = Modifier.testTag("options_btn_${app.packageName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
