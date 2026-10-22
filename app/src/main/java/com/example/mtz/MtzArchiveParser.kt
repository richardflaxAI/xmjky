package com.example.mtz

import android.util.Xml
import com.example.domain.model.MamlFancyIconSpec
import com.example.domain.model.MamlLayerSpec
import com.example.domain.model.MtzThemeMetadata
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Parses Xiaomi MIUI / HyperOS `.mtz` and `.mtx` theme archives.
 *
 * Standard Xiaomi `.mtz` structure (including real MIUI themes such as `Midnight+Blue.mtz`):
 * - `description.xml`: Theme title, author, designer, version, uiVersion
 * - `icons` / `com.miui.home`: Inner ZIP archives containing:
 *   - `theme_values.xml`: `<dimen name="icon_scale">`, `<color name="icon_title_color">` / `workspace_icon_text_color`
 *   - `transform_config.xml`: `<IconTransform>` with `<PointsMapping>` (`0..90` -> `10..80`) and
 *     `<IconFilters>` (`GrayScale` `BlackColor="#465967"` + `Levels` `InputMin="95"`)
 *   - `res/drawable-xxhdpi/`: `icon_background.png` / `icon_bg.png`, `icon_mask.png`, `icon_border.png`, `<package>.png`
 *   - `fancy_icons/<package>/manifest.xml`: Xiaomi MAML dynamic animated/live icons
 * - `wallpaper/default_wallpaper.png`, `default_wallpaper.jpg`, or `default_lock_wallpaper.jpg`
 */
class MtzArchiveParser {

    companion object {
        private val SKIPPED_TOP_LEVEL_SYSTEM_MODULES = setOf(
            "framework-miui-res",
            "framework-res",
            "com.android.contacts",
            "com.android.mms",
            "com.android.settings",
            "com.android.systemui",
            "com.android.updater",
            "com.miui.player",
            "lockscreen",
            "clock_2x4",
            "miwallpaper"
        )

        private val NON_APP_TEMPLATE_NAMES = setOf(
            "icon_pattern",
            "folder_icon_cover",
            "icon_folder",
            "sym_def_app_icon"
        )
    }

    fun parseAndExtractMtzStream(
        inputStream: InputStream,
        themeId: String,
        archiveFileName: String,
        targetExtractDir: File,
        isBuiltIn: Boolean
    ): MtzThemeMetadata {
        if (targetExtractDir.exists()) {
            targetExtractDir.deleteRecursively()
        }
        targetExtractDir.mkdirs()

        extractZipStreamRecursive(inputStream, targetExtractDir, isRootArchive = true)
        return loadExtractedTheme(
            themeId = themeId,
            extractedDir = targetExtractDir,
            archiveFileName = archiveFileName,
            isBuiltIn = isBuiltIn,
            importedAtMillis = System.currentTimeMillis()
        )
    }

    private fun extractZipStreamRecursive(
        inputStream: InputStream,
        destDir: File,
        isRootArchive: Boolean
    ) {
        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry

        while (entry != null) {
            val cleanName = entry.name.removePrefix("/").replace("\\", "/")
            if (cleanName.isNotEmpty() && !cleanName.contains("..")) {
                val shouldSkip = isRootArchive && (
                    cleanName in SKIPPED_TOP_LEVEL_SYSTEM_MODULES ||
                        cleanName.startsWith("boots/") ||
                        cleanName.startsWith("preview/")
                    )
                if (!shouldSkip) {
                    val outFile = File(destDir, cleanName)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        val bytes = zis.readBytes()
                        if ((cleanName == "icons" || cleanName == "icons.zip") && isZipPayload(bytes)) {
                            val iconsDir = File(destDir, "icons_unpacked")
                            iconsDir.mkdirs()
                            extractZipStreamRecursive(ByteArrayInputStream(bytes), iconsDir, isRootArchive = false)
                        } else if (cleanName == "com.miui.home" && isZipPayload(bytes)) {
                            val homeDir = File(destDir, "com_miui_home_unpacked")
                            homeDir.mkdirs()
                            extractZipStreamRecursive(ByteArrayInputStream(bytes), homeDir, isRootArchive = false)
                        } else {
                            FileOutputStream(outFile).use { fos ->
                                fos.write(bytes)
                            }
                        }
                    }
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }

    private fun isZipPayload(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    fun loadExtractedTheme(
        themeId: String,
        extractedDir: File,
        archiveFileName: String,
        isBuiltIn: Boolean,
        importedAtMillis: Long
    ): MtzThemeMetadata {
        // If an older extraction left `com.miui.home` or `icons` as a raw zip file on disk, unpack it once
        val rawIconsZip = File(extractedDir, "icons")
        val iconsUnpackedDir = File(extractedDir, "icons_unpacked")
        if (rawIconsZip.isFile && !iconsUnpackedDir.exists()) {
            runCatching {
                val bytes = rawIconsZip.readBytes()
                if (isZipPayload(bytes)) {
                    iconsUnpackedDir.mkdirs()
                    extractZipStreamRecursive(ByteArrayInputStream(bytes), iconsUnpackedDir, isRootArchive = false)
                }
            }
        }
        val rawHomeZip = File(extractedDir, "com.miui.home")
        val homeUnpackedDir = File(extractedDir, "com_miui_home_unpacked")
        if (rawHomeZip.isFile && !homeUnpackedDir.exists()) {
            runCatching {
                val bytes = rawHomeZip.readBytes()
                if (isZipPayload(bytes)) {
                    homeUnpackedDir.mkdirs()
                    extractZipStreamRecursive(ByteArrayInputStream(bytes), homeUnpackedDir, isRootArchive = false)
                }
            }
        }

        val allFiles = extractedDir.walkTopDown().filter { it.isFile }.toList()

        // 1. Parse description.xml
        val descriptionFile = allFiles.firstOrNull {
            it.name.equals("description.xml", ignoreCase = true)
        }
        val rawDescriptionXml = descriptionFile?.readText() ?: defaultDescriptionXml(archiveFileName)
        val descFields = parseDescriptionXml(rawDescriptionXml, archiveFileName)

        // 2. Parse theme_values.xml and transform_config.xml
        val themeValuesFile = allFiles.firstOrNull {
            it.name.equals("theme_values.xml", ignoreCase = true)
        }
        val rawThemeValuesXml = themeValuesFile?.readText() ?: defaultThemeValuesXml()
        val themeValues = parseThemeValuesXml(rawThemeValuesXml)

        val transformConfigFile = allFiles.firstOrNull {
            it.name.equals("transform_config.xml", ignoreCase = true) ||
                it.name.equals("icon_config.xml", ignoreCase = true)
        }
        val rawTransformXml = transformConfigFile?.readText()
            ?: defaultTransformConfigXml(themeValues.iconScale ?: 0.78f)
        val parsedTransform = parseTransformConfigXml(rawTransformXml)

        val effectiveIconScale = themeValues.iconScale
            ?: parsedTransform.pointsScale
            ?: 0.78f

        // 3. Index Xiaomi Icon API Layer Templates (icon_background / icon_bg, icon_mask, icon_border) + Direct Package Icons
        val iconBgPaths = mutableListOf<String>()
        var iconMaskPath: String? = null
        var iconBorderPath: String? = null
        var fallbackPatternBorderPath: String? = null
        val directIconsMap = mutableMapOf<String, String>()

        val imageFiles = allFiles.filter {
            val ext = it.extension.lowercase()
            (ext == "png" || ext == "webp" || ext == "jpg") &&
                !it.invariantSeparatorsPath.contains("/fancy_icons/") &&
                !it.invariantSeparatorsPath.contains("/wallpaper/") &&
                !it.invariantSeparatorsPath.contains("/preview/")
        }

        for (img in imageFiles) {
            val baseName = img.nameWithoutExtension
            val lowerName = baseName.lowercase()
            when {
                lowerName == "icon_background" ||
                    lowerName == "icon_bg" ||
                    lowerName == "icon_back" ||
                    lowerName.startsWith("icon_bg_") ||
                    lowerName.startsWith("icon_back_") -> {
                    iconBgPaths.add(img.absolutePath)
                }
                lowerName == "icon_mask" || lowerName == "mask" -> {
                    iconMaskPath = img.absolutePath
                }
                lowerName == "icon_border" || lowerName == "icon_fg" || lowerName == "icon_upon" -> {
                    iconBorderPath = img.absolutePath
                }
                lowerName == "icon_pattern" -> {
                    if (img.length() > 256L) {
                        fallbackPatternBorderPath = img.absolutePath
                    }
                }
                lowerName in NON_APP_TEMPLATE_NAMES -> {
                    // Skip non-app folder/placeholder templates from directIconsMap
                }
                else -> {
                    directIconsMap[baseName] = img.absolutePath
                }
            }
        }
        if (iconBorderPath == null && fallbackPatternBorderPath != null) {
            iconBorderPath = fallbackPatternBorderPath
        }
        iconBgPaths.sort()

        // 4. Parse Xiaomi MAML fancy_icons/<package>/manifest.xml
        val fancyIconSpecs = mutableMapOf<String, MamlFancyIconSpec>()
        val manifestFiles = allFiles.filter {
            it.name.equals("manifest.xml", ignoreCase = true) &&
                it.invariantSeparatorsPath.contains("fancy_icons")
        }
        for (manifestFile in manifestFiles) {
            val pkgFolder = manifestFile.parentFile ?: continue
            val pkgName = pkgFolder.name
            val rawXml = manifestFile.readText()
            val spec = parseFancyIconManifest(pkgName, pkgFolder.absolutePath, rawXml)
            fancyIconSpecs[pkgName] = spec
        }

        // 5. Locate Wallpaper (prefer default_wallpaper, fallback to default_lock_wallpaper)
        val wallpaperFiles = allFiles.filter {
            it.invariantSeparatorsPath.contains("wallpaper") &&
                (it.extension.equals("png", true) || it.extension.equals("jpg", true))
        }
        val wallpaperFile = wallpaperFiles.firstOrNull {
            it.name.contains("default_wallpaper", ignoreCase = true)
        } ?: wallpaperFiles.firstOrNull()

        return MtzThemeMetadata(
            id = themeId,
            title = descFields["title"]?.takeIf { it.isNotBlank() }
                ?: archiveFileName.removeSuffix(".mtz").removeSuffix(".mtx").replace("+", " "),
            author = descFields["author"] ?: "Xiaomi Theme Studio",
            designer = descFields["designer"] ?: descFields["author"] ?: "MIUI / HyperOS Designer",
            version = descFields["version"] ?: "1.0.0",
            uiVersion = descFields["uiVersion"] ?: "HyperOS 2.0 / MIUI 14",
            description = descFields["description"] ?: "Xiaomi MTZ Icon Theme Package",
            archiveFileName = archiveFileName,
            extractedDirPath = extractedDir.absolutePath,
            iconScale = effectiveIconScale,
            iconPaddingDp = themeValues.iconPaddingDp,
            titleColorHex = themeValues.titleColorHex,
            iconBgPaths = iconBgPaths,
            iconMaskPath = iconMaskPath,
            iconBorderPath = iconBorderPath,
            directIconsMap = directIconsMap,
            fancyIconSpecs = fancyIconSpecs,
            wallpaperPath = wallpaperFile?.absolutePath,
            rawDescriptionXml = rawDescriptionXml,
            rawThemeValuesXml = rawThemeValuesXml,
            rawTransformConfigXml = rawTransformXml,
            isBuiltIn = isBuiltIn,
            importedAtMillis = importedAtMillis,
            filterBlackColorHex = parsedTransform.blackColorHex,
            filterLevelsInputMin = parsedTransform.levelsInputMin,
            useEmeraldThirdPartyShader = parsedTransform.useEmeraldShader
        )
    }

    private fun parseDescriptionXml(xmlContent: String, fallbackTitle: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            var currentTag: String? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        val tag = currentTag
                        val text = parser.text?.trim()
                        if (!tag.isNullOrEmpty() && !text.isNullOrEmpty()) {
                            result[tag] = text
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = null
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            result["title"] = fallbackTitle.removeSuffix(".mtz").replace("+", " ")
        }
        return result
    }

    private data class ParsedThemeValues(
        val iconScale: Float? = null,
        val iconPaddingDp: Float = 10f,
        val titleColorHex: String = "#ECF0F1"
    )

    private fun parseThemeValuesXml(xmlContent: String): ParsedThemeValues {
        var scale: Float? = null
        var padding = 10f
        var colorHex = "#ECF0F1"
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            var currentNameAttr: String? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentNameAttr = parser.getAttributeValue(null, "name")
                    }
                    XmlPullParser.TEXT -> {
                        val value = parser.text?.trim().orEmpty()
                        if (value.isNotEmpty() && currentNameAttr != null) {
                            when (currentNameAttr) {
                                "icon_scale", "customize_icon_scale" -> {
                                    val cleanFloat = value.replace("dp", "").replace("px", "").replace("f", "").trim()
                                    scale = cleanFloat.toFloatOrNull()?.coerceIn(0.45f, 1.0f) ?: scale
                                }
                                "icon_padding" -> {
                                    val cleanFloat = value.replace("dp", "").replace("px", "").trim()
                                    padding = cleanFloat.toFloatOrNull()?.coerceIn(0f, 36f) ?: padding
                                }
                                "icon_title_color", "workspace_icon_text_color" -> {
                                    if (value.startsWith("#")) colorHex = value
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentNameAttr = null
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return ParsedThemeValues(scale, padding, colorHex)
    }

    private data class ParsedTransformConfig(
        val pointsScale: Float? = null,
        val blackColorHex: String = "#465967",
        val levelsInputMin: Int = 95,
        val useEmeraldShader: Boolean = false
    )

    /**
     * Parses MIUI `<IconTransform>` (`transform_config.xml`) including:
     * - `<PointsMapping><Point fromX="0.0" fromY="0.0" toX="10.0" toY="10.0" />...</PointsMapping>`
     * - `<IconFilters><Filter name="GrayScale"><Param name="BlackColor" value="#465967" /></Filter>`
     * - `<Filter name="Levels"><Param name="InputMin" value="95" /></Filter></IconFilters>`
     * - `<ThirdPartyColorShader enabled="true">` (for Emerald/White shader themes)
     */
    private fun parseTransformConfigXml(xmlContent: String): ParsedTransformConfig {
        var minFromX = Float.MAX_VALUE
        var maxFromX = -Float.MAX_VALUE
        var minToX = Float.MAX_VALUE
        var maxToX = -Float.MAX_VALUE
        var explicitScale: Float? = null
        var blackColorHex = "#465967"
        var levelsInputMin = 95
        var useEmeraldShader = false
        var activeFilterName: String? = null

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Point" -> {
                                val fx = parser.getAttributeValue(null, "fromX")?.toFloatOrNull()
                                val tx = parser.getAttributeValue(null, "toX")?.toFloatOrNull()
                                if (fx != null && tx != null) {
                                    minFromX = min(minFromX, fx)
                                    maxFromX = max(maxFromX, fx)
                                    minToX = min(minToX, tx)
                                    maxToX = max(maxToX, tx)
                                }
                            }
                            "Scale", "IconMatrix" -> {
                                val s = parser.getAttributeValue(null, "value")?.toFloatOrNull()
                                    ?: parser.getAttributeValue(null, "scaleX")?.toFloatOrNull()
                                if (s != null) {
                                    explicitScale = s.coerceIn(0.45f, 1.0f)
                                }
                            }
                            "Filter" -> {
                                activeFilterName = parser.getAttributeValue(null, "name")
                            }
                            "Param" -> {
                                val pName = parser.getAttributeValue(null, "name")
                                val pVal = parser.getAttributeValue(null, "value")
                                if (pName != null && pVal != null) {
                                    if (activeFilterName.equals("GrayScale", ignoreCase = true) &&
                                        pName.equals("BlackColor", ignoreCase = true) &&
                                        pVal.startsWith("#")
                                    ) {
                                        blackColorHex = pVal
                                    } else if (activeFilterName.equals("Levels", ignoreCase = true) &&
                                        pName.equals("InputMin", ignoreCase = true)
                                    ) {
                                        levelsInputMin = pVal.toIntOrNull()?.coerceIn(0, 240) ?: 95
                                    } else if (pName.equals("EmeraldShader", ignoreCase = true)) {
                                        useEmeraldShader = pVal.equals("true", ignoreCase = true)
                                    }
                                }
                            }
                            "ThirdPartyColorShader" -> {
                                val enabled = parser.getAttributeValue(null, "enabled")
                                if (enabled == null || enabled.equals("true", ignoreCase = true)) {
                                    useEmeraldShader = true
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Filter") {
                            activeFilterName = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        val computedPointsScale = if (maxFromX > minFromX && maxToX > minToX) {
            ((maxToX - minToX) / (maxFromX - minFromX)).coerceIn(0.45f, 1.0f)
        } else {
            explicitScale
        }

        return ParsedTransformConfig(
            pointsScale = computedPointsScale,
            blackColorHex = blackColorHex,
            levelsInputMin = levelsInputMin,
            useEmeraldShader = useEmeraldShader
        )
    }

    private fun parseFancyIconManifest(
        targetPackage: String,
        directoryPath: String,
        xmlContent: String
    ): MamlFancyIconSpec {
        var width = 192
        var height = 192
        var frameRate = 30
        val layers = mutableListOf<MamlLayerSpec>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "Icon" -> {
                            width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 192
                            height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 192
                            frameRate = parser.getAttributeValue(null, "frameRate")?.toIntOrNull() ?: 30
                        }
                        "Image" -> {
                            val src = parser.getAttributeValue(null, "src")
                            val name = parser.getAttributeValue(null, "name") ?: src ?: "layer"
                            val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: (width / 2f)
                            val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: (height / 2f)
                            val pivotX = parser.getAttributeValue(null, "pivotX")?.toFloatOrNull() ?: (width / 2f)
                            val pivotY = parser.getAttributeValue(null, "pivotY")?.toFloatOrNull() ?: (height / 2f)
                            val rotation = parser.getAttributeValue(null, "rotation")
                            val alignV = parser.getAttributeValue(null, "alignV")
                            layers.add(
                                MamlLayerSpec(
                                    name = name,
                                    imageFileName = src,
                                    centerX = x,
                                    centerY = y,
                                    pivotX = pivotX,
                                    pivotY = pivotY,
                                    rotationExpression = rotation,
                                    alignVCenter = alignV.equals("center", ignoreCase = true)
                                )
                            )
                        }
                        "DateTime", "Text" -> {
                            val name = parser.getAttributeValue(null, "name") ?: "text_layer"
                            val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: (width / 2f)
                            val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: (height / 2f)
                            val exp = parser.getAttributeValue(null, "format")
                                ?: parser.getAttributeValue(null, "exp")
                                ?: "#date"
                            val color = parser.getAttributeValue(null, "color") ?: "#ECF0F1"
                            val size = parser.getAttributeValue(null, "size")?.toFloatOrNull() ?: 46f
                            val alignV = parser.getAttributeValue(null, "alignV")
                            layers.add(
                                MamlLayerSpec(
                                    name = name,
                                    imageFileName = null,
                                    centerX = x,
                                    centerY = y,
                                    textExpression = exp,
                                    textColorHex = color,
                                    textSizePx = size,
                                    alignVCenter = alignV.equals("center", ignoreCase = true)
                                )
                            )
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        return MamlFancyIconSpec(
            targetPackage = targetPackage,
            directoryPath = directoryPath,
            width = width,
            height = height,
            frameRate = frameRate,
            layers = layers,
            rawManifestXml = xmlContent
        )
    }

    private fun defaultDescriptionXml(fileName: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <MIUI-Theme>
            <title>${fileName.removeSuffix(".mtz").replace("+", " ")}</title>
            <author>Custom MTZ Archive</author>
            <designer>Xiaomi Theme Developer</designer>
            <version>1.0</version>
            <uiVersion>HyperOS / MIUI 14</uiVersion>
        </MIUI-Theme>
    """.trimIndent()

    private fun defaultThemeValuesXml() = """
        <?xml version="1.0" encoding="utf-8"?>
        <MIUI_Theme_Values>
            <dimen name="icon_scale">0.78</dimen>
            <dimen name="icon_padding">10dp</dimen>
            <color name="icon_title_color">#ECF0F1</color>
        </MIUI_Theme_Values>
    """.trimIndent()

    private fun defaultTransformConfigXml(scale: Float): String {
        val inset = ((1f - scale) * 45f).coerceIn(0f, 25f)
        val maxCoord = 90f - inset
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <IconTransform>
                <PointsMapping>
                    <Point fromX="0.0" fromY="0.0" toX="$inset" toY="$inset" />
                    <Point fromX="0.0" fromY="90.0" toX="$inset" toY="$maxCoord" />
                    <Point fromX="90.0" fromY="90.0" toX="$maxCoord" toY="$maxCoord" />
                    <Point fromX="90.0" fromY="0.0" toX="$maxCoord" toY="$inset" />
                </PointsMapping>
                <IconFilters>
                    <Filter name="GrayScale">
                        <Param name="BlackColor" value="#465967" />
                    </Filter>
                    <Filter name="Levels">
                        <Param name="InputMin" value="95" />
                    </Filter>
                </IconFilters>
            </IconTransform>
        """.trimIndent()
    }
}
