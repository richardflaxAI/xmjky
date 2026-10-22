package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LauncherDatabase
import com.example.data.local.MtzThemeEntity
import com.example.data.repository.LauncherRepository
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.HyperFolderLayoutMode
import com.example.domain.model.HyperWidgetType
import com.example.domain.model.IconPipelineBreakdown
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MtzThemeMetadata
import com.example.domain.model.SystemWidgetProviderItem
import com.example.domain.model.WorkspaceFolder
import com.example.domain.model.WorkspaceWidgetItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LauncherTab {
    WORKSPACE,
    APP_DRAWER,
    MTZ_STUDIO,
    API_PIPELINE,
    SETTINGS
}

data class CatalogIconEntry(
    val key: String,
    val filePath: String,
    val bitmap: Bitmap
)

data class LauncherUiState(
    val isLoading: Boolean = true,
    val renderVersion: Long = 0L,
    val currentTab: LauncherTab = LauncherTab.WORKSPACE,
    val engineConfig: LauncherEngineConfig = LauncherEngineConfig(),
    val installedThemes: List<MtzThemeEntity> = emptyList(),
    val activeThemeMetadata: MtzThemeMetadata? = null,
    val activeWallpaperBitmap: Bitmap? = null,
    val allApps: List<InstalledAppItem> = emptyList(),
    val workspaceApps: List<InstalledAppItem> = emptyList(),
    val dockApps: List<InstalledAppItem> = emptyList(),
    val workspaceFolders: List<WorkspaceFolder> = emptyList(),
    val workspaceWidgets: List<WorkspaceWidgetItem> = emptyList(),
    val deviceTelemetry: DeviceTelemetrySnapshot = DeviceTelemetrySnapshot(),
    val systemWidgetProviders: List<SystemWidgetProviderItem> = emptyList(),
    val drawerSearchQuery: String = "",
    val drawerSourceFilter: IconResolutionSource? = null,
    val inspectedApp: InstalledAppItem? = null,
    val pipelineBreakdown: IconPipelineBreakdown? = null,
    val activeThemeIconCatalog: List<CatalogIconEntry> = emptyList(),
    val statusBannerMessage: String? = null
)

class LauncherViewModel(
    private val repository: LauncherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var immediateRecomputeJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                repository.initializeBuiltInThemesAndWorkspace()
                recomputeStateSnapshot(overrideConfig = null)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusBannerMessage = "Init warning: ${err.message}"
                    )
                }
            }

            // Start Room reactive observer
            observeLauncherData()

            // Extract remaining built-in .mtz themes in background without delaying startup
            launch(Dispatchers.IO) {
                runCatching {
                    repository.initializeSecondaryBuiltInThemes()
                    val allThemes = repository.getAllThemesOnce()
                    _uiState.update { it.copy(installedThemes = allThemes) }
                }
                // Preload system widget providers in background after themes are ready
                runCatching {
                    val currentTheme = _uiState.value.activeThemeMetadata
                    val currentConfig = _uiState.value.engineConfig
                    if (currentTheme != null) {
                        val sysWidgets = repository.querySystemAppWidgetProviders(currentTheme, currentConfig)
                        _uiState.update { it.copy(systemWidgetProviders = sysWidgets) }
                    }
                }
            }
        }
    }

    private fun observeLauncherData() {
        viewModelScope.launch {
            val coreFlow = combine(
                repository.engineConfigFlow,
                repository.themesFlow,
                repository.shortcutsFlow,
                repository.iconOverridesFlow
            ) { config, themes, shortcuts, overrides ->
                CoreDataTuple(config, themes, shortcuts, overrides)
            }

            combine(
                coreFlow,
                repository.foldersFlow,
                repository.widgetsFlow
            ) { core, folderEntities, widgetEntities ->
                Triple(core, folderEntities, widgetEntities)
            }.collectLatest { (core, folderEntities, widgetEntities) ->
                try {
                    applyResolvedData(
                        config = core.config,
                        themes = core.themes,
                        shortcuts = core.shortcuts,
                        overrides = core.overrides,
                        folderEntities = folderEntities,
                        widgetEntities = widgetEntities
                    )
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusBannerMessage = "Render pipeline error: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Immediately recomputes and pushes the updated themed state without waiting for SQLite
     * InvalidationTracker latency.
     */
    private fun triggerImmediateRecompute(overrideConfig: LauncherEngineConfig? = null) {
        immediateRecomputeJob?.cancel()
        immediateRecomputeJob = viewModelScope.launch {
            runCatching {
                recomputeStateSnapshot(overrideConfig = overrideConfig)
            }
        }
    }

    private suspend fun recomputeStateSnapshot(overrideConfig: LauncherEngineConfig?) {
        val config = overrideConfig ?: repository.getEngineConfigOnce()
        val themes = repository.getAllThemesOnce()
        val shortcuts = repository.getShortcutsOnce()
        val overrides = repository.getIconOverridesOnce()
        val folderEntities = repository.getFoldersOnce()
        val widgetEntities = repository.getWidgetsOnce()

        applyResolvedData(
            config = config,
            themes = themes,
            shortcuts = shortcuts,
            overrides = overrides,
            folderEntities = folderEntities,
            widgetEntities = widgetEntities
        )
    }

    private suspend fun applyResolvedData(
        config: LauncherEngineConfig,
        themes: List<MtzThemeEntity>,
        shortcuts: List<com.example.data.local.WorkspaceShortcutEntity>,
        overrides: List<com.example.data.local.IconOverrideEntity>,
        folderEntities: List<com.example.data.local.WorkspaceFolderEntity>,
        widgetEntities: List<com.example.data.local.WorkspaceWidgetEntity>
    ) {
        val activeThemeMeta = repository.getThemeMetadata(config.activeThemeId)
        val wallpaperBmp = repository.getThemeWallpaperBitmap(activeThemeMeta)
        val catalog = repository.getThemeIconCatalog(activeThemeMeta)

        val apps = repository.loadThemedInstalledApps(
            theme = activeThemeMeta,
            config = config,
            shortcuts = shortcuts,
            overrides = overrides
        )

        val shortcutOrderMap = shortcuts.associate { it.packageName to it.sortOrder }
        val dockList = apps.filter { it.isPinnedToDock }
            .sortedBy { shortcutOrderMap[it.packageName] ?: 0 }
            .take(5)
        val workspaceList = apps.filter { it.isPinnedToWorkspace }
            .sortedBy { shortcutOrderMap[it.packageName] ?: 0 }

        val domainFolders = repository.mapFoldersToDomain(folderEntities, apps)
        val domainWidgets = repository.mapWidgetsToDomain(widgetEntities)
        val telemetry = withContext(Dispatchers.IO) { repository.readDeviceTelemetry() }

        val currentInspectedPkg = _uiState.value.inspectedApp?.packageName
        val targetInspectApp = apps.firstOrNull { it.packageName == currentInspectedPkg }
            ?: apps.firstOrNull { it.resolutionSource == IconResolutionSource.MTZ_DYNAMIC_MASK }
            ?: apps.firstOrNull()

        val breakdown = if (targetInspectApp != null) {
            repository.buildInspectorBreakdown(targetInspectApp, activeThemeMeta, config)
        } else {
            null
        }

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                renderVersion = state.renderVersion + 1L,
                engineConfig = config,
                installedThemes = themes,
                activeThemeMetadata = activeThemeMeta,
                activeWallpaperBitmap = wallpaperBmp,
                allApps = apps,
                workspaceApps = workspaceList,
                dockApps = dockList,
                workspaceFolders = domainFolders,
                workspaceWidgets = domainWidgets,
                deviceTelemetry = telemetry,
                inspectedApp = targetInspectApp,
                pipelineBreakdown = breakdown,
                activeThemeIconCatalog = catalog
            )
        }
    }

    private fun mutateEngineConfigImmediate(
        statusMessage: String? = null,
        transform: (LauncherEngineConfig) -> LauncherEngineConfig
    ) {
        val optimistic = transform(_uiState.value.engineConfig)
        _uiState.update { state ->
            state.copy(
                engineConfig = optimistic,
                statusBannerMessage = statusMessage ?: state.statusBannerMessage
            )
        }
        immediateRecomputeJob?.cancel()
        immediateRecomputeJob = viewModelScope.launch {
            val saved = repository.updateEngineConfig { transform(optimistic) }
            recomputeStateSnapshot(overrideConfig = saved)
        }
    }

    fun refreshTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = repository.readDeviceTelemetry()
            _uiState.update { it.copy(deviceTelemetry = snap) }
        }
    }

    fun createHyperFolder(
        title: String,
        layoutMode: HyperFolderLayoutMode,
        packageNames: List<String>
    ) {
        viewModelScope.launch {
            repository.createHyperFolder(title, layoutMode, packageNames)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Created HyperOS ${layoutMode.displayName}: '$title'")
        }
    }

    fun updateFolderLayoutMode(folderId: String, mode: HyperFolderLayoutMode) {
        viewModelScope.launch {
            repository.updateFolderLayoutMode(folderId, mode)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Resized folder to ${mode.displayName} (${mode.code})")
        }
    }

    fun renameFolder(folderId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameFolder(folderId, newTitle)
            recomputeStateSnapshot(overrideConfig = null)
        }
    }

    fun toggleAppInFolder(folderId: String, packageName: String) {
        viewModelScope.launch {
            repository.toggleAppInFolder(folderId, packageName)
            recomputeStateSnapshot(overrideConfig = null)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Removed folder from Workspace.")
        }
    }

    fun addBuiltInWidget(type: HyperWidgetType) {
        viewModelScope.launch {
            repository.addBuiltInWidget(type)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Added ${type.title} widget to Workspace.")
        }
    }

    fun addNativeSystemWidget(appWidgetId: Int, providerComponent: String, label: String) {
        viewModelScope.launch {
            repository.addNativeSystemWidget(appWidgetId, providerComponent, label)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Bound system widget '$label' to Workspace.")
        }
    }

    fun removeWidget(widgetId: String) {
        viewModelScope.launch {
            repository.removeWidget(widgetId)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Removed widget from Workspace.")
        }
    }

    fun selectTab(tab: LauncherTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateDrawerSearch(query: String) {
        _uiState.update { it.copy(drawerSearchQuery = query) }
    }

    fun updateDrawerSourceFilter(source: IconResolutionSource?) {
        _uiState.update { state ->
            val next = if (state.drawerSourceFilter == source) null else source
            state.copy(drawerSourceFilter = next)
        }
    }

    fun inspectAppInPipeline(app: InstalledAppItem, switchToTab: Boolean = true) {
        viewModelScope.launch {
            val theme = _uiState.value.activeThemeMetadata ?: return@launch
            val config = _uiState.value.engineConfig
            val breakdown = repository.buildInspectorBreakdown(app, theme, config)
            _uiState.update { state ->
                state.copy(
                    inspectedApp = app,
                    pipelineBreakdown = breakdown,
                    currentTab = if (switchToTab) LauncherTab.API_PIPELINE else state.currentTab
                )
            }
        }
    }

    fun activateMtzTheme(themeId: String) {
        val themeTitle = _uiState.value.installedThemes.firstOrNull { it.id == themeId }?.title ?: themeId
        mutateEngineConfigImmediate(
            statusMessage = "Applied Xiaomi MTZ theme: $themeTitle"
        ) {
            it.copy(
                activeThemeId = themeId,
                useCustomScaleOverride = false
            )
        }
    }

    fun importExternalMtzArchive(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = "Parsing and extracting $fileName...") }
            val result = repository.importExternalMtzUri(uri, fileName)
            result.onSuccess { meta ->
                val updated = repository.updateEngineConfig {
                    it.copy(
                        activeThemeId = meta.id,
                        useCustomScaleOverride = false
                    )
                }
                recomputeStateSnapshot(overrideConfig = updated)
                postStatusMessage("Imported and activated '${meta.title}' (${meta.directIconsMap.size} icons, scale ${meta.iconScale}x)")
            }.onFailure { err ->
                postStatusMessage("MTZ Import failed: ${err.message ?: "Invalid archive format"}")
            }
        }
    }

    fun exportMtzThemeToUri(themeId: String, targetUri: Uri) {
        viewModelScope.launch {
            val result = repository.exportBuiltInMtzToUri(themeId, targetUri)
            result.onSuccess {
                postStatusMessage("Exported .mtz package successfully to device storage.")
            }.onFailure { err ->
                postStatusMessage("Export failed: ${err.message}")
            }
        }
    }

    fun deleteCustomMtzTheme(themeId: String) {
        viewModelScope.launch {
            repository.deleteCustomTheme(themeId)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Removed custom .mtz theme package.")
        }
    }

    fun setAppIconOverride(packageName: String, assetKey: String?) {
        viewModelScope.launch {
            val themeId = _uiState.value.engineConfig.activeThemeId
            repository.setPerAppIconOverride(packageName, themeId, assetKey)
            recomputeStateSnapshot(overrideConfig = null)
            val msg = if (assetKey == null) {
                "Reset icon for $packageName to automatic Xiaomi API resolution."
            } else {
                "Mapped $packageName to '$assetKey.png' from active .mtz archive."
            }
            postStatusMessage(msg)
        }
    }

    fun togglePinApp(app: InstalledAppItem, toDock: Boolean) {
        viewModelScope.launch {
            repository.toggleWorkspacePin(app, toDock)
            recomputeStateSnapshot(overrideConfig = null)
            val targetName = if (toDock) "Dock" else "Home Workspace"
            postStatusMessage("Updated $targetName shortcut for ${app.label}.")
        }
    }

    fun updateScaleOverride(enabled: Boolean, scaleValue: Float) {
        mutateEngineConfigImmediate {
            it.copy(
                useCustomScaleOverride = enabled,
                customScaleValue = scaleValue.coerceIn(0.50f, 1.00f)
            )
        }
    }

    fun toggleDynamicMasking(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableDynamicMasking = enabled)
        }
    }

    fun toggleThirdPartyColorFilter(enabled: Boolean) {
        val stateLabel = if (enabled) "ENABLED" else "DISABLED"
        mutateEngineConfigImmediate(
            statusMessage = "3rd-Party Red->White & Green Palette Shader: $stateLabel"
        ) {
            it.copy(enableThirdPartyColorFilter = enabled)
        }
    }

    fun toggleFancyMamlIcons(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableFancyMamlIcons = enabled)
        }
    }

    fun toggleIconBorderOverlay(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableIconBorderOverlay = enabled)
        }
    }

    fun toggleMultiBgHash(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableMultiBgHashSelection = enabled)
        }
    }

    fun updateGridColumns(columns: Int) {
        mutateEngineConfigImmediate {
            it.copy(gridColumns = columns.coerceIn(4, 5))
        }
    }

    fun updateIconSizeDp(sizeDp: Int) {
        mutateEngineConfigImmediate {
            it.copy(iconSizeDp = sizeDp.coerceIn(48, 68))
        }
    }

    fun toggleShowAppLabels(show: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(showAppLabels = show)
        }
    }

    fun toggleShowApiSourceBadges(show: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(showApiSourceBadges = show)
        }
    }

    fun launchInstalledApp(app: InstalledAppItem) {
        val launched = repository.launchApp(app.packageName, app.activityName)
        if (!launched) {
            inspectAppInPipeline(app, switchToTab = true)
            postStatusMessage("Inspecting Xiaomi MAML / MTZ pipeline for ${app.label} (${app.packageName})")
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    private fun postStatusMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    private data class CoreDataTuple(
        val config: LauncherEngineConfig,
        val themes: List<MtzThemeEntity>,
        val shortcuts: List<com.example.data.local.WorkspaceShortcutEntity>,
        val overrides: List<com.example.data.local.IconOverrideEntity>
    )

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = LauncherDatabase.getInstance(context)
                    val repo = LauncherRepository(context.applicationContext, db.launcherDao())
                    return LauncherViewModel(repo) as T
                }
            }
        }
    }
}
