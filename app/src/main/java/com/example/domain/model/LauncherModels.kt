package com.example.domain.model

import android.graphics.Bitmap

/**
 * Identifies which stage of the Xiaomi MIUI / HyperOS Icon Theme API resolved the icon.
 */
enum class IconResolutionSource(val code: String, val label: String) {
    MANUAL_OVERRIDE("OVERRIDE", "Manual Per-App Mapping"),
    MTZ_FANCY_MAML("MAML_FANCY", "fancy_icons/manifest.xml (MAML)"),
    MTZ_DIRECT_MAP("DIRECT_PNG", "res/drawable-xxhdpi Package Match"),
    MTZ_DYNAMIC_MASK("PORTER_DUFF", "Red->White & Green Palette + PorterDuff Mask"),
    SYSTEM_RAW("RAW_SYSTEM", "Unmodified PackageManager Drawable")
}

/**
 * Xiaomi HyperOS Folder Sizing Modes:
 * - REGULAR_1X1: Compact 1x1 tile; tapping anywhere opens the expanded folder modal.
 * - CAPSULE_2X1: Horizontal 2x1 pill; first 2 apps launch directly on tap, 3rd slot expands folder.
 * - BIG_FOLDER_2X2: Signature HyperOS 2x2 Big Folder; first 3 apps launch directly on tap,
 *   4th bottom-right 2x2 mini-grid cluster opens the expanded folder.
 * - XXL_FOLDER_3X3: HyperOS 3x3 Super Folder; first 8 apps launch directly, 9th slot expands folder.
 */
enum class HyperFolderLayoutMode(
    val code: String,
    val displayName: String,
    val directTapSlots: Int
) {
    REGULAR_1X1("1x1", "Compact 1x1", 0),
    CAPSULE_2X1("2x1", "Capsule 2x1", 2),
    BIG_FOLDER_2X2("2x2", "Big Folder 2x2", 3),
    XXL_FOLDER_3X3("3x3", "XXL Folder 3x3", 8);

    companion object {
        fun fromCode(code: String): HyperFolderLayoutMode {
            return entries.firstOrNull { it.code == code } ?: BIG_FOLDER_2X2
        }
    }
}

/**
 * Represents a HyperOS Folder on the Home Workspace containing `.mtz`-themed applications.
 */
data class WorkspaceFolder(
    val id: String,
    val title: String,
    val layoutMode: HyperFolderLayoutMode,
    val packageNames: List<String>,
    val apps: List<InstalledAppItem>,
    val sortOrder: Int,
    val renderKey: String = ""
)

/**
 * Built-in HyperOS Widget Types & Native Android AppWidgetHost integration.
 */
enum class HyperWidgetType(
    val code: String,
    val title: String,
    val badgeSize: String,
    val description: String
) {
    CLOCK_MTZ_STATUS(
        "CLOCK_MTZ_STATUS",
        "HyperOS Clock & MTZ Engine",
        "4x2",
        "Live digital clock, date, active .mtz package, and icon_scale telemetry"
    ),
    DEVICE_TELEMETRY(
        "DEVICE_TELEMETRY",
        "HyperOS Battery & RAM Matrix",
        "2x2",
        "Real-time Android BatteryManager charge/temperature and ActivityManager RAM load"
    ),
    STORAGE_AND_PACKS(
        "STORAGE_AND_PACKS",
        "HyperOS Storage & MTZ Coverage",
        "2x2",
        "Internal partition StatFs usage and .mtz icon pipeline coverage bar"
    ),
    MAML_LIVE_DUO(
        "MAML_LIVE_DUO",
        "MAML Dynamic Clock & Calendar",
        "4x1",
        "Live 1fps MAML fancy_icons renderer with real-time second hand and calendar date"
    ),
    NATIVE_APPWIDGET(
        "NATIVE_APPWIDGET",
        "Android System AppWidget",
        "Native",
        "Bound system AppWidgetProvider hosted via AppWidgetHostView"
    );

    companion object {
        fun fromCode(code: String): HyperWidgetType {
            return entries.firstOrNull { it.code == code } ?: DEVICE_TELEMETRY
        }
    }
}

/**
 * Represents an active Widget card pinned to the Home Workspace.
 */
data class WorkspaceWidgetItem(
    val id: String,
    val widgetType: HyperWidgetType,
    val appWidgetId: Int = -1,
    val providerComponent: String? = null,
    val customTitle: String,
    val sortOrder: Int
)

/**
 * Real hardware and OS telemetry read from BatteryManager, ActivityManager, and StatFs.
 */
data class DeviceTelemetrySnapshot(
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 29.5f,
    val usedRamMb: Long = 2048L,
    val totalRamMb: Long = 4096L,
    val usedStorageGb: Float = 12.4f,
    val totalStorageGb: Float = 64.0f
)

/**
 * Represents a real Android AppWidgetProvider discovered via AppWidgetManager.
 */
data class SystemWidgetProviderItem(
    val providerComponent: String,
    val packageName: String,
    val label: String,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val iconBitmap: Bitmap
)

/**
 * Represents a single layer in a Xiaomi MAML (`fancy_icons/<package>/manifest.xml`) dynamic icon.
 */
data class MamlLayerSpec(
    val name: String,
    val imageFileName: String?,
    val centerX: Float = 96f,
    val centerY: Float = 96f,
    val pivotX: Float = 96f,
    val pivotY: Float = 96f,
    val rotationExpression: String? = null,
    val textExpression: String? = null,
    val textColorHex: String = "#ECF3F8",
    val textSizePx: Float = 44f,
    val alignVCenter: Boolean = false
)

/**
 * Represents a parsed Xiaomi `fancy_icons/<package>/manifest.xml` specification.
 */
data class MamlFancyIconSpec(
    val targetPackage: String,
    val directoryPath: String,
    val width: Int = 192,
    val height: Int = 192,
    val frameRate: Int = 30,
    val layers: List<MamlLayerSpec>,
    val rawManifestXml: String
)

/**
 * Complete parsed structure of a Xiaomi `.mtz` or `.mtx` theme archive.
 */
data class MtzThemeMetadata(
    val id: String,
    val title: String,
    val author: String,
    val designer: String,
    val version: String,
    val uiVersion: String,
    val description: String,
    val archiveFileName: String,
    val extractedDirPath: String,
    val iconScale: Float,
    val iconPaddingDp: Float,
    val titleColorHex: String,
    val iconBgPaths: List<String>,
    val iconMaskPath: String?,
    val iconBorderPath: String?,
    val directIconsMap: Map<String, String>,
    val fancyIconSpecs: Map<String, MamlFancyIconSpec>,
    val wallpaperPath: String?,
    val rawDescriptionXml: String,
    val rawThemeValuesXml: String,
    val rawTransformConfigXml: String,
    val isBuiltIn: Boolean,
    val importedAtMillis: Long,
    val filterBlackColorHex: String = "#465967",
    val filterLevelsInputMin: Int = 95,
    val useEmeraldThirdPartyShader: Boolean = false
)

/**
 * Represents an installed launchable Android application on the user's device,
 * enriched with its resolved Xiaomi MTZ themed icon.
 */
data class InstalledAppItem(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemApp: Boolean,
    val rawIconBitmap: Bitmap,
    val themedIconBitmap: Bitmap,
    val resolutionSource: IconResolutionSource,
    val matchedAssetName: String,
    val isPinnedToWorkspace: Boolean,
    val isPinnedToDock: Boolean,
    val workspacePage: Int,
    val customOverrideAssetKey: String?,
    val renderKey: String = ""
)

/**
 * Step-by-step visual and technical breakdown of the Xiaomi Icon Theme API pipeline
 * for a specific application.
 */
data class IconPipelineBreakdown(
    val appLabel: String,
    val packageName: String,
    val activityName: String,
    val resolutionSource: IconResolutionSource,
    val appliedScale: Float,
    val matchedEntryPath: String,
    val rawAppBitmap: Bitmap,
    val backgroundPlateBitmap: Bitmap?,
    val scaledInputBitmap: Bitmap,
    val maskTemplateBitmap: Bitmap?,
    val maskedLayerBitmap: Bitmap,
    val borderOverlayBitmap: Bitmap?,
    val finalOutputBitmap: Bitmap,
    val xmlDiagnosticSnippet: String
)

/**
 * Global configuration for the Launcher Grid and Xiaomi Icon Theme API Engine.
 */
data class LauncherEngineConfig(
    val activeThemeId: String = "builtin_red_white_emerald",
    val useCustomScaleOverride: Boolean = false,
    val customScaleValue: Float = 0.78f,
    val enableDynamicMasking: Boolean = true,
    val enableThirdPartyColorFilter: Boolean = true,
    val enableFancyMamlIcons: Boolean = true,
    val enableIconBorderOverlay: Boolean = true,
    val enableMultiBgHashSelection: Boolean = true,
    val gridColumns: Int = 4,
    val iconSizeDp: Int = 58,
    val showAppLabels: Boolean = true,
    val showApiSourceBadges: Boolean = false
)
