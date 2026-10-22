package com.example.data.repository

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.example.data.local.EngineSettingsEntity
import com.example.data.local.IconOverrideEntity
import com.example.data.local.LauncherDao
import com.example.data.local.MtzThemeEntity
import com.example.data.local.WorkspaceFolderEntity
import com.example.data.local.WorkspaceShortcutEntity
import com.example.data.local.WorkspaceWidgetEntity
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.HyperFolderLayoutMode
import com.example.domain.model.HyperWidgetType
import com.example.domain.model.IconPipelineBreakdown
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MtzThemeMetadata
import com.example.domain.model.SystemWidgetProviderItem
import com.example.domain.model.WorkspaceFolder
import com.example.domain.model.WorkspaceWidgetItem
import com.example.mtz.MtzArchiveParser
import com.example.mtz.MtzSampleGenerator
import com.example.mtz.XiaomiIconRenderEngine
import com.example.ui.viewmodel.CatalogIconEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Clean Architecture Repository managing:
 * 1. Installed Android Launcher Activities (`PackageManager`) with in-memory caching
 * 2. Xiaomi `.mtz` / `.mtx` Archive Extraction & Metadata Caching
 * 3. Xiaomi Icon Theme API Rendering & Pipeline Diagnostics
 * 4. HyperOS Big Folders (`1x1`, `2x1 Capsule`, `2x2 Big Folder`, `3x3 XXL`)
 * 5. HyperOS Telemetry Widgets & Native Android `AppWidgetManager` Providers
 */
class LauncherRepository(
    private val context: Context,
    private val dao: LauncherDao,
    private val parser: MtzArchiveParser = MtzArchiveParser(),
    private val sampleGenerator: MtzSampleGenerator = MtzSampleGenerator(context),
    private val renderEngine: XiaomiIconRenderEngine = XiaomiIconRenderEngine()
) {

    private val metadataMemoryCache = ConcurrentHashMap<String, MtzThemeMetadata>()
    private val rawIconMemoryCache = ConcurrentHashMap<String, Bitmap>()
    private val wallpaperMemoryCache = ConcurrentHashMap<String, Bitmap>()
    private val catalogMemoryCache = ConcurrentHashMap<String, List<CatalogIconEntry>>()

    @Volatile
    private var cachedRawApps: List<RawAppRecord>? = null

    @Volatile
    private var cachedRawWidgets: List<RawWidgetRecord>? = null

    val engineConfigFlow: Flow<LauncherEngineConfig> = dao.observeEngineSettings().map { entity ->
        entity?.toDomain() ?: LauncherEngineConfig()
    }

    val themesFlow: Flow<List<MtzThemeEntity>> = dao.observeAllThemes()
    val shortcutsFlow: Flow<List<WorkspaceShortcutEntity>> = dao.observeWorkspaceShortcuts()
    val foldersFlow: Flow<List<WorkspaceFolderEntity>> = dao.observeWorkspaceFolders()
    val widgetsFlow: Flow<List<WorkspaceWidgetEntity>> = dao.observeWorkspaceWidgets()
    val iconOverridesFlow: Flow<List<IconOverrideEntity>> = dao.observeIconOverrides()

    suspend fun getEngineConfigOnce(): LauncherEngineConfig = withContext(Dispatchers.IO) {
        dao.getEngineSettingsOnce()?.toDomain() ?: LauncherEngineConfig()
    }

    suspend fun getAllThemesOnce(): List<MtzThemeEntity> = withContext(Dispatchers.IO) {
        dao.getAllThemesOnce()
    }

    suspend fun getShortcutsOnce(): List<WorkspaceShortcutEntity> = withContext(Dispatchers.IO) {
        dao.getWorkspaceShortcutsOnce()
    }

    suspend fun getFoldersOnce(): List<WorkspaceFolderEntity> = withContext(Dispatchers.IO) {
        dao.getWorkspaceFoldersOnce()
    }

    suspend fun getWidgetsOnce(): List<WorkspaceWidgetEntity> = withContext(Dispatchers.IO) {
        dao.getWorkspaceWidgetsOnce()
    }

    /**
     * Fast startup initialization: extracts ONLY the active theme and seeds default Workspace items
     * so the first frame renders in under 200ms.
     */
    suspend fun initializeBuiltInThemesAndWorkspace() = withContext(Dispatchers.IO) {
        val extractedRoot = File(context.filesDir, "extracted_themes")
        if (!extractedRoot.exists()) extractedRoot.mkdirs()

        val existingSettings = dao.getEngineSettingsOnce()
        val activeThemeId = existingSettings?.activeThemeId ?: "builtin_red_white_emerald"
        if (existingSettings == null) {
            dao.saveEngineSettings(EngineSettingsEntity(activeThemeId = activeThemeId))
        }

        val specs = sampleGenerator.getBuiltInSpecs()
        val activeSpec = specs.firstOrNull { it.id == activeThemeId } ?: specs.first()

        // Ensure the active theme is extracted immediately
        ensureBuiltInSpecExtractedAndSaved(activeSpec, extractedRoot)

        val rawApps = queryRawLaunchableApps()

        // Seed initial workspace and dock shortcuts if empty
        val existingShortcuts = dao.getWorkspaceShortcutsOnce()
        if (existingShortcuts.isEmpty()) {
            val dockCandidates = rawApps.take(4)
            dockCandidates.forEachIndexed { idx, app ->
                dao.upsertWorkspaceShortcut(
                    WorkspaceShortcutEntity(
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        isDockItem = true,
                        pageIndex = 0,
                        sortOrder = idx
                    )
                )
            }
            val gridCandidates = rawApps.drop(4).take(8)
            gridCandidates.forEachIndexed { idx, app ->
                dao.upsertWorkspaceShortcut(
                    WorkspaceShortcutEntity(
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        isDockItem = false,
                        pageIndex = 0,
                        sortOrder = idx
                    )
                )
            }
        }

        // Seed initial HyperOS Big Folders if empty
        val existingFolders = dao.getWorkspaceFoldersOnce()
        if (existingFolders.isEmpty() && rawApps.isNotEmpty()) {
            val corePackages = rawApps.take(6).map { it.packageName }
            dao.upsertWorkspaceFolder(
                WorkspaceFolderEntity(
                    id = "folder_system_core",
                    title = "System Core",
                    layoutModeCode = HyperFolderLayoutMode.BIG_FOLDER_2X2.code,
                    packageNamesCsv = corePackages.joinToString(","),
                    sortOrder = 0
                )
            )
            val utilityPackages = rawApps.reversed().take(5).map { it.packageName }
            dao.upsertWorkspaceFolder(
                WorkspaceFolderEntity(
                    id = "folder_daily_tools",
                    title = "HyperOS Suite",
                    layoutModeCode = HyperFolderLayoutMode.CAPSULE_2X1.code,
                    packageNamesCsv = utilityPackages.joinToString(","),
                    sortOrder = 1
                )
            )
        }

        // Seed initial HyperOS Widgets if empty
        val existingWidgets = dao.getWorkspaceWidgetsOnce()
        if (existingWidgets.isEmpty()) {
            dao.upsertWorkspaceWidget(
                WorkspaceWidgetEntity(
                    id = "widget_clock_mtz",
                    widgetTypeCode = HyperWidgetType.CLOCK_MTZ_STATUS.code,
                    appWidgetId = -1,
                    providerComponent = null,
                    customTitle = HyperWidgetType.CLOCK_MTZ_STATUS.title,
                    sortOrder = 0
                )
            )
            dao.upsertWorkspaceWidget(
                WorkspaceWidgetEntity(
                    id = "widget_telemetry",
                    widgetTypeCode = HyperWidgetType.DEVICE_TELEMETRY.code,
                    appWidgetId = -1,
                    providerComponent = null,
                    customTitle = HyperWidgetType.DEVICE_TELEMETRY.title,
                    sortOrder = 1
                )
            )
            dao.upsertWorkspaceWidget(
                WorkspaceWidgetEntity(
                    id = "widget_storage",
                    widgetTypeCode = HyperWidgetType.STORAGE_AND_PACKS.code,
                    appWidgetId = -1,
                    providerComponent = null,
                    customTitle = HyperWidgetType.STORAGE_AND_PACKS.title,
                    sortOrder = 2
                )
            )
        }
    }

    /**
     * Background initialization for the remaining built-in `.mtz` themes so startup is never delayed.
     */
    suspend fun initializeSecondaryBuiltInThemes() = withContext(Dispatchers.IO) {
        val extractedRoot = File(context.filesDir, "extracted_themes")
        if (!extractedRoot.exists()) extractedRoot.mkdirs()
        for (spec in sampleGenerator.getBuiltInSpecs()) {
            ensureBuiltInSpecExtractedAndSaved(spec, extractedRoot)
        }
    }

    private suspend fun ensureBuiltInSpecExtractedAndSaved(
        spec: MtzSampleGenerator.BuiltInMtzSpec,
        extractedRoot: File
    ): MtzThemeMetadata {
        metadataMemoryCache[spec.id]?.let { cached ->
            dao.upsertTheme(cached.toEntity())
            return cached
        }
        val targetDir = File(extractedRoot, spec.id)
        val versionMarker = File(targetDir, ".spec_v5_${spec.version}_${spec.iconScale}")
        val metadata = if (targetDir.exists() && versionMarker.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            parser.loadExtractedTheme(
                themeId = spec.id,
                extractedDir = targetDir,
                archiveFileName = spec.fileName,
                isBuiltIn = true,
                importedAtMillis = 1700000000000L
            )
        } else {
            if (targetDir.exists()) targetDir.deleteRecursively()
            val mtzFile = sampleGenerator.ensureSingleMtzArchiveCreated(spec)
            val parsed = FileInputStream(mtzFile).use { fis ->
                parser.parseAndExtractMtzStream(
                    inputStream = fis,
                    themeId = spec.id,
                    archiveFileName = spec.fileName,
                    targetExtractDir = targetDir,
                    isBuiltIn = true
                )
            }
            runCatching { versionMarker.writeText(spec.version) }
            parsed
        }
        metadataMemoryCache[spec.id] = metadata
        dao.upsertTheme(metadata.toEntity())
        return metadata
    }

    suspend fun getThemeWallpaperBitmap(theme: MtzThemeMetadata): Bitmap? = withContext(Dispatchers.IO) {
        wallpaperMemoryCache[theme.id]?.let { return@withContext it }
        val path = theme.wallpaperPath ?: return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        val bmp = BitmapFactory.decodeFile(path) ?: return@withContext null
        wallpaperMemoryCache[theme.id] = bmp
        bmp
    }

    suspend fun getThemeIconCatalog(theme: MtzThemeMetadata): List<CatalogIconEntry> = withContext(Dispatchers.IO) {
        catalogMemoryCache[theme.id]?.let { return@withContext it }
        val list = theme.directIconsMap.entries.mapNotNull { (key, path) ->
            val bmp = BitmapFactory.decodeFile(path)
            if (bmp != null) CatalogIconEntry(key = key, filePath = path, bitmap = bmp) else null
        }.sortedBy { it.key }
        catalogMemoryCache[theme.id] = list
        list
    }

    suspend fun createHyperFolder(
        title: String,
        layoutMode: HyperFolderLayoutMode,
        packageNames: List<String>
    ) = withContext(Dispatchers.IO) {
        val current = dao.getWorkspaceFoldersOnce()
        val nextSort = (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val folderId = "folder_" + UUID.randomUUID().toString().take(8)
        dao.upsertWorkspaceFolder(
            WorkspaceFolderEntity(
                id = folderId,
                title = title.ifBlank { "New Folder" },
                layoutModeCode = layoutMode.code,
                packageNamesCsv = packageNames.distinct().joinToString(","),
                sortOrder = nextSort
            )
        )
    }

    suspend fun updateFolderLayoutMode(folderId: String, mode: HyperFolderLayoutMode) =
        withContext(Dispatchers.IO) {
            val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext
            dao.upsertWorkspaceFolder(folder.copy(layoutModeCode = mode.code))
        }

    suspend fun renameFolder(folderId: String, newTitle: String) =
        withContext(Dispatchers.IO) {
            val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext
            dao.upsertWorkspaceFolder(folder.copy(title = newTitle.ifBlank { folder.title }))
        }

    suspend fun toggleAppInFolder(folderId: String, packageName: String) =
        withContext(Dispatchers.IO) {
            val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext
            val currentList = folder.packageNamesCsv.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            if (packageName in currentList) {
                currentList.remove(packageName)
            } else {
                currentList.add(packageName)
            }
            dao.upsertWorkspaceFolder(folder.copy(packageNamesCsv = currentList.joinToString(",")))
        }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        dao.deleteWorkspaceFolder(folderId)
    }

    suspend fun addBuiltInWidget(type: HyperWidgetType) = withContext(Dispatchers.IO) {
        val current = dao.getWorkspaceWidgetsOnce()
        val nextSort = (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.upsertWorkspaceWidget(
            WorkspaceWidgetEntity(
                id = "widget_" + UUID.randomUUID().toString().take(8),
                widgetTypeCode = type.code,
                appWidgetId = -1,
                providerComponent = null,
                customTitle = type.title,
                sortOrder = nextSort
            )
        )
    }

    suspend fun addNativeSystemWidget(
        appWidgetId: Int,
        providerComponent: String,
        label: String
    ) = withContext(Dispatchers.IO) {
        val current = dao.getWorkspaceWidgetsOnce()
        val nextSort = (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.upsertWorkspaceWidget(
            WorkspaceWidgetEntity(
                id = "appwidget_$appWidgetId",
                widgetTypeCode = HyperWidgetType.NATIVE_APPWIDGET.code,
                appWidgetId = appWidgetId,
                providerComponent = providerComponent,
                customTitle = label,
                sortOrder = nextSort
            )
        )
    }

    suspend fun removeWidget(widgetId: String) = withContext(Dispatchers.IO) {
        dao.deleteWorkspaceWidget(widgetId)
    }

    fun readDeviceTelemetry(): DeviceTelemetrySnapshot {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 85
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 295) ?: 295
        val tempCelsius = tempTenths / 10f

        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)
        val totalRamMb = if (memInfo.totalMem > 0) memInfo.totalMem / (1024L * 1024L) else 4096L
        val availRamMb = if (memInfo.availMem > 0) memInfo.availMem / (1024L * 1024L) else 1800L
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(256L)

        var totalStorageGb = 64f
        var usedStorageGb = 18.5f
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            val availBytes = stat.blockSizeLong * stat.availableBlocksLong
            totalStorageGb = (totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat().coerceAtLeast(1f)
            usedStorageGb = ((totalBytes - availBytes).toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat().coerceAtLeast(0.1f)
        } catch (_: Exception) {
        }

        return DeviceTelemetrySnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempCelsius,
            usedRamMb = usedRamMb,
            totalRamMb = totalRamMb,
            usedStorageGb = usedStorageGb,
            totalStorageGb = totalStorageGb
        )
    }

    private data class RawWidgetRecord(
        val providerComponent: String,
        val packageName: String,
        val className: String,
        val label: String,
        val minWidthDp: Int,
        val minHeightDp: Int,
        val rawBitmap: Bitmap
    )

    suspend fun querySystemAppWidgetProviders(
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig
    ): List<SystemWidgetProviderItem> = withContext(Dispatchers.Default) {
        val rawProviders = cachedRawWidgets ?: run {
            val loaded = mutableListOf<RawWidgetRecord>()
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val pm = context.packageManager
                val providers = appWidgetManager.installedProviders.take(24)
                for (info in providers) {
                    val pkg = info.provider.packageName
                    val cls = info.provider.className
                    val label = info.loadLabel(pm)?.takeIf { it.isNotBlank() } ?: pkg.substringAfterLast('.')
                    val rawDrawable = info.loadIcon(context, context.resources.displayMetrics.densityDpi)
                    val rawBmp = drawableToBitmap(rawDrawable)
                    loaded.add(
                        RawWidgetRecord(
                            providerComponent = info.provider.flattenToString(),
                            packageName = pkg,
                            className = cls,
                            label = label,
                            minWidthDp = info.minWidth.coerceAtLeast(110),
                            minHeightDp = info.minHeight.coerceAtLeast(60),
                            rawBitmap = rawBmp
                        )
                    )
                }
            } catch (_: Exception) {
            }
            val sorted = loaded.sortedBy { it.label.lowercase() }
            cachedRawWidgets = sorted
            sorted
        }

        rawProviders.map { raw ->
            val themed = renderEngine.resolveAppIcon(
                packageName = raw.packageName,
                activityName = raw.className,
                rawAppBitmap = raw.rawBitmap,
                theme = theme,
                config = config,
                customOverrideAssetKey = null
            )
            SystemWidgetProviderItem(
                providerComponent = raw.providerComponent,
                packageName = raw.packageName,
                label = raw.label,
                minWidthDp = raw.minWidthDp,
                minHeightDp = raw.minHeightDp,
                iconBitmap = themed.bitmap
            )
        }
    }

    suspend fun getThemeMetadata(themeId: String): MtzThemeMetadata = withContext(Dispatchers.IO) {
        metadataMemoryCache[themeId]?.let { return@withContext it }

        val extractedRoot = File(context.filesDir, "extracted_themes")
        val builtInSpec = sampleGenerator.getBuiltInSpecs().firstOrNull { it.id == themeId }
        if (builtInSpec != null) {
            return@withContext ensureBuiltInSpecExtractedAndSaved(builtInSpec, extractedRoot)
        }

        val themes = dao.getAllThemesOnce()
        val entity = themes.firstOrNull { it.id == themeId } ?: themes.firstOrNull()
        if (entity != null) {
            val dir = File(entity.extractedDirPath)
            if (dir.exists()) {
                val loaded = parser.loadExtractedTheme(
                    themeId = entity.id,
                    extractedDir = dir,
                    archiveFileName = entity.archiveFileName,
                    isBuiltIn = entity.isBuiltIn,
                    importedAtMillis = entity.importedAtMillis
                )
                metadataMemoryCache[entity.id] = loaded
                dao.upsertTheme(loaded.toEntity())
                return@withContext loaded
            }
        }

        val fallbackSpec = sampleGenerator.getBuiltInSpecs().first()
        ensureBuiltInSpecExtractedAndSaved(fallbackSpec, extractedRoot)
    }

    suspend fun importExternalMtzUri(uri: Uri, displayFileName: String): Result<MtzThemeMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val themeId = "mtz_" + UUID.randomUUID().toString().take(8)
                val extractedRoot = File(context.filesDir, "extracted_themes")
                val targetDir = File(extractedRoot, themeId)
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Unable to open selected .mtz archive stream.")

                val metadata = inputStream.use { stream ->
                    parser.parseAndExtractMtzStream(
                        inputStream = stream,
                        themeId = themeId,
                        archiveFileName = displayFileName,
                        targetExtractDir = targetDir,
                        isBuiltIn = false
                    )
                }

                metadataMemoryCache[themeId] = metadata
                dao.upsertTheme(metadata.toEntity())
                renderEngine.clearBitmapCache()
                metadata
            }
        }

    suspend fun exportBuiltInMtzToUri(themeId: String, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val spec = sampleGenerator.getBuiltInSpecs().firstOrNull { it.id == themeId }
                    ?: sampleGenerator.getBuiltInSpecs().first()
                val sourceFile = sampleGenerator.ensureSingleMtzArchiveCreated(spec)
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Could not open destination URI for writing.")
                Unit
            }
        }

    suspend fun deleteCustomTheme(themeId: String) = withContext(Dispatchers.IO) {
        val currentSettings = dao.getEngineSettingsOnce() ?: EngineSettingsEntity()
        if (currentSettings.activeThemeId == themeId) {
            dao.saveEngineSettings(currentSettings.copy(activeThemeId = "builtin_red_white_emerald"))
        }
        dao.deleteCustomTheme(themeId)
        metadataMemoryCache.remove(themeId)
        wallpaperMemoryCache.remove(themeId)
        catalogMemoryCache.remove(themeId)
        val dir = File(File(context.filesDir, "extracted_themes"), themeId)
        if (dir.exists()) dir.deleteRecursively()
    }

    suspend fun updateEngineConfig(transform: (LauncherEngineConfig) -> LauncherEngineConfig): LauncherEngineConfig =
        withContext(Dispatchers.IO) {
            val current = dao.getEngineSettingsOnce()?.toDomain() ?: LauncherEngineConfig()
            val updated = transform(current)
            dao.saveEngineSettings(updated.toEntity())
            updated
        }

    suspend fun getIconOverridesOnce(): List<IconOverrideEntity> = withContext(Dispatchers.IO) {
        dao.getIconOverridesOnce()
    }

    suspend fun setPerAppIconOverride(packageName: String, themeId: String, assetKey: String?) =
        withContext(Dispatchers.IO) {
            if (assetKey.isNullOrBlank()) {
                dao.clearIconOverride(packageName)
            } else {
                dao.upsertIconOverride(
                    IconOverrideEntity(
                        packageName = packageName,
                        themeId = themeId,
                        assetKey = assetKey
                    )
                )
            }
        }

    suspend fun toggleWorkspacePin(app: InstalledAppItem, toDock: Boolean) =
        withContext(Dispatchers.IO) {
            val current = dao.getWorkspaceShortcutsOnce()
            val existing = current.firstOrNull { it.packageName == app.packageName }
            if (existing != null && existing.isDockItem == toDock) {
                dao.removeWorkspaceShortcut(app.packageName)
            } else {
                val maxOrder = current.maxOfOrNull { it.sortOrder } ?: 0
                dao.upsertWorkspaceShortcut(
                    WorkspaceShortcutEntity(
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        isDockItem = toDock,
                        pageIndex = 0,
                        sortOrder = maxOrder + 1
                    )
                )
            }
        }

    suspend fun loadThemedInstalledApps(
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        shortcuts: List<WorkspaceShortcutEntity>,
        overrides: List<IconOverrideEntity>,
        calendar: Calendar = Calendar.getInstance()
    ): List<InstalledAppItem> = withContext(Dispatchers.Default) {
        val rawApps = queryRawLaunchableApps()
        val shortcutByPkg = shortcuts.associateBy { it.packageName }
        val overrideByPkg = overrides.associate { it.packageName to it.assetKey }

        rawApps.map { raw ->
            val shortcut = shortcutByPkg[raw.packageName]
            val overrideKey = overrideByPkg[raw.packageName]
            val resolved = renderEngine.resolveAppIcon(
                packageName = raw.packageName,
                activityName = raw.activityName,
                rawAppBitmap = raw.rawBitmap,
                theme = theme,
                config = config,
                customOverrideAssetKey = overrideKey,
                calendar = calendar
            )
            InstalledAppItem(
                packageName = raw.packageName,
                activityName = raw.activityName,
                label = raw.label,
                isSystemApp = raw.isSystemApp,
                rawIconBitmap = raw.rawBitmap,
                themedIconBitmap = resolved.bitmap,
                resolutionSource = resolved.source,
                matchedAssetName = resolved.matchedAssetName,
                isPinnedToWorkspace = shortcut != null && !shortcut.isDockItem,
                isPinnedToDock = shortcut != null && shortcut.isDockItem,
                workspacePage = shortcut?.pageIndex ?: 0,
                customOverrideAssetKey = overrideKey,
                renderKey = resolved.renderKey
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun mapFoldersToDomain(
        folderEntities: List<WorkspaceFolderEntity>,
        allApps: List<InstalledAppItem>
    ): List<WorkspaceFolder> {
        val appByPkg = allApps.associateBy { it.packageName }
        return folderEntities.map { entity ->
            val pkgNames = entity.packageNamesCsv.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val resolvedApps = pkgNames.mapNotNull { appByPkg[it] }
            val combinedRenderKey = resolvedApps.firstOrNull()?.renderKey.orEmpty() + "_" + entity.layoutModeCode + "_" + resolvedApps.size
            WorkspaceFolder(
                id = entity.id,
                title = entity.title,
                layoutMode = HyperFolderLayoutMode.fromCode(entity.layoutModeCode),
                packageNames = pkgNames,
                apps = resolvedApps,
                sortOrder = entity.sortOrder,
                renderKey = combinedRenderKey
            )
        }
    }

    fun mapWidgetsToDomain(
        widgetEntities: List<WorkspaceWidgetEntity>
    ): List<WorkspaceWidgetItem> {
        return widgetEntities.map { entity ->
            WorkspaceWidgetItem(
                id = entity.id,
                widgetType = HyperWidgetType.fromCode(entity.widgetTypeCode),
                appWidgetId = entity.appWidgetId,
                providerComponent = entity.providerComponent,
                customTitle = entity.customTitle,
                sortOrder = entity.sortOrder
            )
        }
    }

    suspend fun buildInspectorBreakdown(
        app: InstalledAppItem,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        calendar: Calendar = Calendar.getInstance()
    ): IconPipelineBreakdown = withContext(Dispatchers.Default) {
        renderEngine.buildPipelineBreakdown(
            appLabel = app.label,
            packageName = app.packageName,
            activityName = app.activityName,
            rawAppBitmap = app.rawIconBitmap,
            theme = theme,
            config = config,
            customOverrideAssetKey = app.customOverrideAssetKey,
            calendar = calendar
        )
    }

    fun launchApp(packageName: String, activityName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = context.packageManager.getLaunchIntentForPackage(packageName)
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallback)
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    private data class RawAppRecord(
        val packageName: String,
        val activityName: String,
        val label: String,
        val isSystemApp: Boolean,
        val rawBitmap: Bitmap
    )

    private fun queryRawLaunchableApps(): List<RawAppRecord> {
        cachedRawApps?.let { return it }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val records = mutableListOf<RawAppRecord>()
        val seenPackages = mutableSetOf<String>()

        for (ri in resolveInfos) {
            val activityInfo = ri.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (!seenPackages.add(pkg)) continue

            val actName = activityInfo.name ?: pkg
            val label = ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            val isSystem = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val rawBmp = rawIconMemoryCache.getOrPut(pkg) {
                drawableToBitmap(ri.loadIcon(pm))
            }
            records.add(
                RawAppRecord(
                    packageName = pkg,
                    activityName = actName,
                    label = label,
                    isSystemApp = isSystem,
                    rawBitmap = rawBmp
                )
            )
        }

        val essentialPackages = listOf(
            Triple("com.android.deskclock", "com.android.deskclock.DeskClock", "Clock (MAML)"),
            Triple("com.android.calendar", "com.android.calendar.AllInOneActivity", "Calendar (MAML)"),
            Triple("com.android.settings", "com.android.settings.Settings", "Settings"),
            Triple("com.android.dialer", "com.android.dialer.DialtactsActivity", "Phone"),
            Triple("com.android.chrome", "com.google.android.apps.chrome.Main", "Browser"),
            Triple("com.android.camera2", "com.android.camera.CameraLauncher", "Camera"),
            Triple("com.android.calculator2", "com.android.calculator2.Calculator", "Calculator"),
            Triple("com.android.documentsui", "com.android.documentsui.files.FilesActivity", "Files")
        )
        for ((pkg, act, label) in essentialPackages) {
            if (pkg !in seenPackages) {
                val rawBmp = rawIconMemoryCache.getOrPut(pkg) {
                    createFallbackRawAppIcon(label)
                }
                records.add(
                    RawAppRecord(
                        packageName = pkg,
                        activityName = act,
                        label = label,
                        isSystemApp = true,
                        rawBitmap = rawBmp
                    )
                )
            }
        }

        cachedRawApps = records
        return records
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap {
        val targetSize = 128
        if (drawable == null) return createFallbackRawAppIcon("App")
        return try {
            val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, targetSize, targetSize)
            drawable.draw(canvas)
            bmp
        } catch (_: Exception) {
            createFallbackRawAppIcon("App")
        }
    }

    private fun createFallbackRawAppIcon(label: String): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#25374A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(6f, 6f, 122f, 122f), 26f, 26f, paint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34D399")
            textSize = 52f
            textAlign = Paint.Align.CENTER
        }
        val initial = label.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        canvas.drawText(initial, 64f, 82f, textPaint)
        return bmp
    }

    private fun MtzThemeMetadata.toEntity(): MtzThemeEntity = MtzThemeEntity(
        id = id,
        title = title,
        author = author,
        designer = designer,
        version = version,
        uiVersion = uiVersion,
        description = description,
        archiveFileName = archiveFileName,
        extractedDirPath = extractedDirPath,
        iconScale = iconScale,
        iconPaddingDp = iconPaddingDp,
        titleColorHex = titleColorHex,
        directIconCount = directIconsMap.size,
        fancyIconCount = fancyIconSpecs.size,
        hasMask = iconMaskPath != null,
        hasBg = iconBgPaths.isNotEmpty(),
        hasBorder = iconBorderPath != null,
        hasWallpaper = wallpaperPath != null,
        isBuiltIn = isBuiltIn,
        importedAtMillis = importedAtMillis
    )

    private fun EngineSettingsEntity.toDomain(): LauncherEngineConfig = LauncherEngineConfig(
        activeThemeId = activeThemeId,
        useCustomScaleOverride = useCustomScaleOverride,
        customScaleValue = customScaleValue,
        enableDynamicMasking = enableDynamicMasking,
        enableThirdPartyColorFilter = enableThirdPartyColorFilter,
        enableFancyMamlIcons = enableFancyMamlIcons,
        enableIconBorderOverlay = enableIconBorderOverlay,
        enableMultiBgHashSelection = enableMultiBgHashSelection,
        gridColumns = gridColumns,
        iconSizeDp = iconSizeDp,
        showAppLabels = showAppLabels,
        showApiSourceBadges = showApiSourceBadges
    )

    private fun LauncherEngineConfig.toEntity(): EngineSettingsEntity = EngineSettingsEntity(
        configKey = 1,
        activeThemeId = activeThemeId,
        useCustomScaleOverride = useCustomScaleOverride,
        customScaleValue = customScaleValue,
        enableDynamicMasking = enableDynamicMasking,
        enableThirdPartyColorFilter = enableThirdPartyColorFilter,
        enableFancyMamlIcons = enableFancyMamlIcons,
        enableIconBorderOverlay = enableIconBorderOverlay,
        enableMultiBgHashSelection = enableMultiBgHashSelection,
        gridColumns = gridColumns,
        iconSizeDp = iconSizeDp,
        showAppLabels = showAppLabels,
        showApiSourceBadges = showApiSourceBadges
    )
}
