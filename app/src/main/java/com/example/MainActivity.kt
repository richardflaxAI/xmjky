package com.example

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.MtzThemeEntity
import com.example.domain.model.HyperFolderLayoutMode
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.SystemWidgetProviderItem
import com.example.domain.model.WorkspaceFolder
import com.example.ui.components.AppActionBottomSheet
import com.example.ui.components.ExpandedHyperFolderSheet
import com.example.ui.components.WidgetsAndFoldersPickerSheet
import com.example.ui.screens.ApiPipelineScreen
import com.example.ui.screens.AppDrawerScreen
import com.example.ui.screens.LauncherSettingsScreen
import com.example.ui.screens.MtzStudioScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LauncherTab
import com.example.ui.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {

    private var appWidgetHost: AppWidgetHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetHost = try {
            AppWidgetHost(applicationContext, 1024).also { it.startListening() }
        } catch (_: Exception) {
            null
        }

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val viewModel: LauncherViewModel = viewModel(
                    factory = LauncherViewModel.provideFactory(context)
                )
                HyperMtzLauncherRoot(
                    viewModel = viewModel,
                    appWidgetHost = appWidgetHost
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            appWidgetHost?.stopListening()
        } catch (_: Exception) {
        }
    }
}

private data class NavTabSpec(
    val tab: LauncherTab,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val NAV_TABS = listOf(
    NavTabSpec(LauncherTab.WORKSPACE, "Workspace", Icons.Default.Home, "nav_tab_workspace"),
    NavTabSpec(LauncherTab.APP_DRAWER, "Drawer", Icons.Default.Apps, "nav_tab_drawer"),
    NavTabSpec(LauncherTab.MTZ_STUDIO, "MTZ Studio", Icons.Default.Layers, "nav_tab_mtz_studio"),
    NavTabSpec(LauncherTab.API_PIPELINE, "API Pipeline", Icons.Default.Tune, "nav_tab_api_pipeline"),
    NavTabSpec(LauncherTab.SETTINGS, "System", Icons.Default.Settings, "nav_tab_settings")
)

@Composable
fun HyperMtzLauncherRoot(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedAppForSheet by remember { mutableStateOf<InstalledAppItem?>(null) }
    var expandedFolderId by remember { mutableStateOf<String?>(null) }
    var showWidgetsAndFoldersPicker by remember { mutableStateOf(false) }
    var pendingExportTheme by remember { mutableStateOf<MtzThemeEntity?>(null) }
    var pendingBindWidget by remember { mutableStateOf<Pair<Int, SystemWidgetProviderItem>?>(null) }

    // Return to Home Workspace when pressing hardware/gesture Back on any secondary tab
    BackHandler(enabled = uiState.currentTab != LauncherTab.WORKSPACE) {
        viewModel.selectTab(LauncherTab.WORKSPACE)
    }

    // Document Picker for importing .mtz / .mtx / .zip Xiaomi Theme Archives
    val importMtzLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveFileName(context, uri) ?: "imported_theme.mtz"
            viewModel.importExternalMtzArchive(uri, fileName)
        }
    }

    // Document Creator for exporting built-in .mtz archives to user storage
    val exportMtzLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val theme = pendingExportTheme
        if (uri != null && theme != null) {
            viewModel.exportMtzThemeToUri(theme.id, uri)
        }
        pendingExportTheme = null
    }

    // Native Android AppWidget Bind permission launcher
    val bindAppWidgetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val pending = pendingBindWidget
        if (pending != null) {
            viewModel.addNativeSystemWidget(
                appWidgetId = pending.first,
                providerComponent = pending.second.providerComponent,
                label = pending.second.label
            )
            pendingBindWidget = null
        }
    }

    val handleBindSystemWidget: (SystemWidgetProviderItem) -> Unit = { provider ->
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val comp = ComponentName.unflattenFromString(provider.providerComponent)
            val allocatedId = appWidgetHost?.allocateAppWidgetId() ?: -1
            if (comp != null && allocatedId >= 0) {
                val bound = appWidgetManager.bindAppWidgetIdIfAllowed(allocatedId, comp)
                if (bound) {
                    viewModel.addNativeSystemWidget(
                        appWidgetId = allocatedId,
                        providerComponent = provider.providerComponent,
                        label = provider.label
                    )
                } else {
                    pendingBindWidget = allocatedId to provider
                    val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, comp)
                    }
                    bindAppWidgetLauncher.launch(bindIntent)
                }
            } else {
                viewModel.addNativeSystemWidget(
                    appWidgetId = -1,
                    providerComponent = provider.providerComponent,
                    label = provider.label
                )
            }
        } catch (_: Exception) {
            viewModel.addNativeSystemWidget(
                appWidgetId = -1,
                providerComponent = provider.providerComponent,
                label = provider.label
            )
        }
    }

    // RoleManager launcher for setting Default Home App on Android 10+
    val homeRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    val triggerDefaultHomeSelection = {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    homeRoleLauncher.launch(intent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = SlateDeepBackground,
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = SlateSurface,
                        contentColor = TextPrimary,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .border(width = 1.dp, color = SlateBorder)
                            .testTag("bottom_navigation_bar")
                    ) {
                        NAV_TABS.forEach { item ->
                            val selected = uiState.currentTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldBright,
                                    selectedTextColor = EmeraldBright,
                                    indicatorColor = EmeraldContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = SlateSurface,
                        contentColor = TextPrimary,
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(width = 1.dp, color = SlateBorder)
                            .testTag("side_navigation_rail")
                    ) {
                        NAV_TABS.forEach { item ->
                            val selected = uiState.currentTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldBright,
                                    selectedTextColor = EmeraldBright,
                                    indicatorColor = EmeraldContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Status notification bar for MTZ import/export/theme updates
                    AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                        val msg = uiState.statusBannerMessage.orEmpty()
                        Surface(
                            color = EmeraldContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .border(1.dp, EmeraldPrimary, RoundedCornerShape(10.dp))
                                .testTag("status_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = msg,
                                    color = EmeraldBright,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification",
                                    tint = EmeraldBright,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { viewModel.clearStatusBanner() }
                                )
                            }
                        }
                    }

                    if (uiState.isLoading) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(color = EmeraldBright)
                        }
                    } else {
                        when (uiState.currentTab) {
                            LauncherTab.WORKSPACE -> {
                                WorkspaceScreen(
                                    activeTheme = uiState.activeThemeMetadata,
                                    wallpaperBitmap = uiState.activeWallpaperBitmap,
                                    engineConfig = uiState.engineConfig,
                                    allApps = uiState.allApps,
                                    workspaceApps = uiState.workspaceApps,
                                    dockApps = uiState.dockApps,
                                    workspaceFolders = uiState.workspaceFolders,
                                    workspaceWidgets = uiState.workspaceWidgets,
                                    deviceTelemetry = uiState.deviceTelemetry,
                                    appWidgetHost = appWidgetHost,
                                    onAppClick = { viewModel.launchInstalledApp(it) },
                                    onAppLongClick = { selectedAppForSheet = it },
                                    onExpandFolder = { folder -> expandedFolderId = folder.id },
                                    onCycleFolderMode = { folder ->
                                        val modes = HyperFolderLayoutMode.entries
                                        val nextIdx = (modes.indexOf(folder.layoutMode) + 1) % modes.size
                                        viewModel.updateFolderLayoutMode(folder.id, modes[nextIdx])
                                    },
                                    onOpenWidgetsAndFoldersPicker = { showWidgetsAndFoldersPicker = true },
                                    onRefreshTelemetry = viewModel::refreshTelemetry,
                                    onRemoveWidget = viewModel::removeWidget,
                                    onOpenAppDrawer = { viewModel.selectTab(LauncherTab.APP_DRAWER) },
                                    onOpenMtzStudio = { viewModel.selectTab(LauncherTab.MTZ_STUDIO) },
                                    onOpenApiPipeline = { viewModel.selectTab(LauncherTab.API_PIPELINE) }
                                )
                            }

                            LauncherTab.APP_DRAWER -> {
                                AppDrawerScreen(
                                    allApps = uiState.allApps,
                                    searchQuery = uiState.drawerSearchQuery,
                                    activeSourceFilter = uiState.drawerSourceFilter,
                                    onSearchQueryChange = viewModel::updateDrawerSearch,
                                    onSourceFilterSelect = viewModel::updateDrawerSourceFilter,
                                    onLaunchApp = viewModel::launchInstalledApp,
                                    onInspectApp = { app ->
                                        viewModel.inspectAppInPipeline(app, switchToTab = true)
                                    },
                                    onOpenAppOptions = { app -> selectedAppForSheet = app }
                                )
                            }

                            LauncherTab.MTZ_STUDIO -> {
                                MtzStudioScreen(
                                    themes = uiState.installedThemes,
                                    activeThemeId = uiState.engineConfig.activeThemeId,
                                    activeThemeMetadata = uiState.activeThemeMetadata,
                                    activeThemeCatalog = uiState.activeThemeIconCatalog,
                                    onActivateTheme = viewModel::activateMtzTheme,
                                    onRequestImportMtz = {
                                        importMtzLauncher.launch(arrayOf("*/*"))
                                    },
                                    onRequestExportMtz = { theme ->
                                        pendingExportTheme = theme
                                        exportMtzLauncher.launch(theme.archiveFileName)
                                    },
                                    onDeleteCustomTheme = viewModel::deleteCustomMtzTheme
                                )
                            }

                            LauncherTab.API_PIPELINE -> {
                                ApiPipelineScreen(
                                    allApps = uiState.allApps,
                                    inspectedApp = uiState.inspectedApp,
                                    breakdown = uiState.pipelineBreakdown,
                                    engineConfig = uiState.engineConfig,
                                    iconCatalog = uiState.activeThemeIconCatalog,
                                    onSelectAppToInspect = { app ->
                                        viewModel.inspectAppInPipeline(app, switchToTab = false)
                                    },
                                    onUpdateScaleOverride = viewModel::updateScaleOverride,
                                    onToggleDynamicMasking = viewModel::toggleDynamicMasking,
                                    onToggleThirdPartyColorFilter = viewModel::toggleThirdPartyColorFilter,
                                    onToggleFancyMaml = viewModel::toggleFancyMamlIcons,
                                    onToggleIconBorder = viewModel::toggleIconBorderOverlay,
                                    onAssignOverrideAsset = viewModel::setAppIconOverride
                                )
                            }

                            LauncherTab.SETTINGS -> {
                                LauncherSettingsScreen(
                                    engineConfig = uiState.engineConfig,
                                    onSetDefaultHomeLauncher = triggerDefaultHomeSelection,
                                    onUpdateGridColumns = viewModel::updateGridColumns,
                                    onUpdateIconSizeDp = viewModel::updateIconSizeDp,
                                    onToggleShowLabels = viewModel::toggleShowAppLabels,
                                    onToggleShowApiBadges = viewModel::toggleShowApiSourceBadges,
                                    onToggleMultiBgHash = viewModel::toggleMultiBgHash
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val sheetApp = selectedAppForSheet
    if (sheetApp != null) {
        val freshApp = uiState.allApps.firstOrNull { it.packageName == sheetApp.packageName } ?: sheetApp
        AppActionBottomSheet(
            app = freshApp,
            iconCatalog = uiState.activeThemeIconCatalog,
            onDismiss = { selectedAppForSheet = null },
            onLaunch = { viewModel.launchInstalledApp(freshApp) },
            onInspectPipeline = {
                viewModel.inspectAppInPipeline(freshApp, switchToTab = true)
            },
            onToggleWorkspacePin = {
                viewModel.togglePinApp(freshApp, toDock = false)
            },
            onToggleDockPin = {
                viewModel.togglePinApp(freshApp, toDock = true)
            },
            onSelectOverrideAsset = { assetKey ->
                viewModel.setAppIconOverride(freshApp.packageName, assetKey)
            }
        )
    }

    val activeExpandedFolder: WorkspaceFolder? = expandedFolderId?.let { id ->
        uiState.workspaceFolders.firstOrNull { it.id == id }
    }
    if (activeExpandedFolder != null) {
        ExpandedHyperFolderSheet(
            folder = activeExpandedFolder,
            allApps = uiState.allApps,
            onDismiss = { expandedFolderId = null },
            onLaunchApp = viewModel::launchInstalledApp,
            onSelectLayoutMode = { mode ->
                viewModel.updateFolderLayoutMode(activeExpandedFolder.id, mode)
            },
            onRenameFolder = { newTitle ->
                viewModel.renameFolder(activeExpandedFolder.id, newTitle)
            },
            onToggleAppMembership = { pkg ->
                viewModel.toggleAppInFolder(activeExpandedFolder.id, pkg)
            },
            onDeleteFolder = {
                viewModel.deleteFolder(activeExpandedFolder.id)
            }
        )
    }

    if (showWidgetsAndFoldersPicker) {
        WidgetsAndFoldersPickerSheet(
            allApps = uiState.allApps,
            systemWidgetProviders = uiState.systemWidgetProviders,
            onDismiss = { showWidgetsAndFoldersPicker = false },
            onCreateFolder = viewModel::createHyperFolder,
            onAddBuiltInWidget = viewModel::addBuiltInWidget,
            onBindSystemWidget = handleBindSystemWidget
        )
    }
}

private fun resolveFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path?.substringAfterLast('/')
    }
    return result
}
