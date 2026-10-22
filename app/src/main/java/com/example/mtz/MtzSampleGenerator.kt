package com.example.mtz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates standards-compliant Xiaomi `.mtz` theme archives on device storage
 * following the exact MIUI `<IconTransform>` specification used in `Midnight+Blue.mtz`:
 * - `description.xml`: Theme title, author, designer, version, uiVersion
 * - `icons`: Inner ZIP archive containing:
 *   - `theme_values.xml`: `<dimen name="icon_scale">`, `<color name="icon_title_color">`
 *   - `transform_config.xml`: `<IconTransform>` with `<PointsMapping>` (`(0,0)-(90,90) -> (10,10)-(80,80)`)
 *     and `<IconFilters>` (`GrayScale` `BlackColor` + `Levels` `InputMin="95"`)
 *   - `res/drawable-xxhdpi/`: 192x192 `icon_background.png` (`r=84`), `icon_mask.png` (`r=84`),
 *     `icon_border.png` (`r=76..90` thick outer ring), and flat-filled package-mapped PNGs
 *   - `fancy_icons/<pkg>/manifest.xml`: Xiaomi MAML dynamic live icons
 */
class MtzSampleGenerator(private val context: Context) {

    data class BuiltInMtzSpec(
        val id: String,
        val fileName: String,
        val title: String,
        val author: String,
        val version: String,
        val uiVersion: String,
        val description: String,
        val iconScale: Float,
        val shapeStyle: ShapeStyle,
        val bgStartHex: String,
        val bgEndHex: String,
        val borderRingHex: String,
        val accentHex: String,
        val secondaryAccentHex: String,
        val useEmeraldShader: Boolean = false,
        val bundledAssetName: String? = null
    )

    enum class ShapeStyle {
        PRECISION_CIRCLE,
        HYPER_SQUIRCLE,
        HEX_CHAMFER
    }

    fun getBuiltInSpecs(): List<BuiltInMtzSpec> = listOf(
        BuiltInMtzSpec(
            id = "builtin_midnight_blue",
            fileName = "Midnight_Blue.mtz",
            title = "Midnight Blue",
            author = "DriffeX",
            version = "1.0.2",
            uiVersion = "MIUI 6 / HyperOS IconTransform",
            description = "Authentic MIUI Midnight Blue theme with 85 circular flat tokens, #43515E outer ring, #465967 GrayScale BlackColor plate, Levels(InputMin=95), and PointsMapping(10..80).",
            iconScale = 0.78f,
            shapeStyle = ShapeStyle.PRECISION_CIRCLE,
            bgStartHex = "#465967",
            bgEndHex = "#495968",
            borderRingHex = "#43515E",
            accentHex = "#9CB0BF",
            secondaryAccentHex = "#ECF0F1",
            useEmeraldShader = false,
            bundledAssetName = "Midnight_Blue.mtz"
        ),
        BuiltInMtzSpec(
            id = "builtin_red_white_emerald",
            fileName = "CrimsonToWhite_Emerald.mtz",
            title = "Red-to-White & Emerald",
            author = "HyperMTZ Shader Lab",
            version = "4.1.0",
            uiVersion = "HyperOS 2.0 / MIUI IconTransform",
            description = "Built with the exact Midnight Blue IconTransform circular token architecture: #1B302A outer ring, #254038 inner plate, red shades mapped to crisp #ECF0F1 white, and other colors to a 3-tone emerald/mint palette.",
            iconScale = 0.78f,
            shapeStyle = ShapeStyle.PRECISION_CIRCLE,
            bgStartHex = "#254038",
            bgEndHex = "#2B4A41",
            borderRingHex = "#1B302A",
            accentHex = "#34D399",
            secondaryAccentHex = "#ECF0F1",
            useEmeraldShader = true,
            bundledAssetName = "CrimsonToWhite_Emerald.mtz"
        ),
        BuiltInMtzSpec(
            id = "builtin_jade_matrix",
            fileName = "MIUI_Jade_Matrix.mtz",
            title = "MIUI Jade Matrix",
            author = "Xiaomi Theme Studio Lab",
            version = "2.2.0",
            uiVersion = "MIUI 14 / V140",
            description = "Deep forest-jade circular tokens with #122923 outer border ring, #1A3A31 BlackColor plate, and #6EE7B7 / #ECF0F1 duotone glyphs.",
            iconScale = 0.74f,
            shapeStyle = ShapeStyle.PRECISION_CIRCLE,
            bgStartHex = "#1A3A31",
            bgEndHex = "#21473C",
            borderRingHex = "#122923",
            accentHex = "#6EE7B7",
            secondaryAccentHex = "#ECF0F1",
            useEmeraldShader = true
        ),
        BuiltInMtzSpec(
            id = "builtin_cyberslate_minimal",
            fileName = "CyberSlate_Minimal.mtz",
            title = "CyberSlate Squircle",
            author = "SlateGreen Design Works",
            version = "3.4.0",
            uiVersion = "HyperOS 2.0",
            description = "HyperOS squircle tokens with #263747 dark slate border, #32475B BlackColor plate, and #99F6E4 / #ECF0F1 duotone glyphs.",
            iconScale = 0.80f,
            shapeStyle = ShapeStyle.HYPER_SQUIRCLE,
            bgStartHex = "#32475B",
            bgEndHex = "#3A5268",
            borderRingHex = "#263747",
            accentHex = "#5EEAD4",
            secondaryAccentHex = "#ECF0F1",
            useEmeraldShader = false
        )
    )

    fun ensureSingleMtzArchiveCreated(spec: BuiltInMtzSpec): File {
        val sampleDir = File(context.filesDir, "sample_mtz_archives")
        if (!sampleDir.exists()) sampleDir.mkdirs()
        val mtzFile = File(sampleDir, spec.fileName)
        val verFile = File(sampleDir, "${spec.id}_${spec.version}.ver")
        if (!mtzFile.exists() || mtzFile.length() == 0L || !verFile.exists()) {
            if (spec.bundledAssetName != null) {
                val copied = runCatching {
                    context.assets.open(spec.bundledAssetName).use { input ->
                        FileOutputStream(mtzFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                }.getOrDefault(false)
                if (copied && mtzFile.length() > 0L) {
                    runCatching { verFile.writeText(spec.version) }
                    return mtzFile
                }
            }
            buildMtzArchiveFile(spec, mtzFile)
            runCatching { verFile.writeText(spec.version) }
        }
        return mtzFile
    }

    fun ensureSampleMtzArchivesCreated(): Map<String, File> {
        val result = mutableMapOf<String, File>()
        for (spec in getBuiltInSpecs()) {
            result[spec.id] = ensureSingleMtzArchiveCreated(spec)
        }
        return result
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun buildMtzArchiveFile(spec: BuiltInMtzSpec, targetFile: File) {
        val descriptionXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <MIUI-Theme>
                <title>${escapeXml(spec.title)}</title>
                <author>${escapeXml(spec.author)}</author>
                <designer>${escapeXml(spec.author)}</designer>
                <version>${escapeXml(spec.version)}</version>
                <uiVersion>${escapeXml(spec.uiVersion)}</uiVersion>
                <description>${escapeXml(spec.description)}</description>
            </MIUI-Theme>
        """.trimIndent()

        val innerIconsBytes = buildInnerIconsZip(spec)
        val wallpaperBytes = bitmapToPngBytes(renderThemeWallpaper(spec))

        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                writeZipEntry(zos, "description.xml", descriptionXml.toByteArray(Charsets.UTF_8))
                writeZipEntry(zos, "icons", innerIconsBytes)
                writeZipEntry(zos, "wallpaper/default_wallpaper.png", wallpaperBytes)
            }
        }
    }

    private fun buildInnerIconsZip(spec: BuiltInMtzSpec): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val themeValuesXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <MIUI_Theme_Values>
                    <dimen name="icon_scale">${spec.iconScale}</dimen>
                    <dimen name="customize_icon_scale">${spec.iconScale}</dimen>
                    <dimen name="icon_padding">10dp</dimen>
                    <color name="icon_title_color">#ECF0F1</color>
                    <color name="workspace_icon_text_color">#AAFFFFFF</color>
                </MIUI_Theme_Values>
            """.trimIndent()

            val inset = ((1f - spec.iconScale) * 45f).toInt().coerceIn(6, 20)
            val maxPt = 90 - inset
            val transformConfigXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <IconTransform>
                    <PointsMapping>
                        <Point fromX="0.0" fromY="0.0" toX="$inset.0" toY="$inset.0" />
                        <Point fromX="0.0" fromY="90.0" toX="$inset.0" toY="$maxPt.0" />
                        <Point fromX="90.0" fromY="90.0" toX="$maxPt.0" toY="$maxPt.0" />
                        <Point fromX="90.0" fromY="0.0" toX="$maxPt.0" toY="$inset.0" />
                    </PointsMapping>
                    <IconFilters>
                        <Filter name="GrayScale">
                            <Param name="BlackColor" value="${spec.bgStartHex}" />
                            <Param name="EmeraldShader" value="${spec.useEmeraldShader}" />
                        </Filter>
                        <Filter name="Levels">
                            <Param name="InputMin" value="95" />
                        </Filter>
                    </IconFilters>
                </IconTransform>
            """.trimIndent()

            writeZipEntry(zos, "theme_values.xml", themeValuesXml.toByteArray(Charsets.UTF_8))
            writeZipEntry(zos, "transform_config.xml", transformConfigXml.toByteArray(Charsets.UTF_8))

            val bg0 = renderIconBackgroundPlate(spec, variant = 0)
            val bg1 = renderIconBackgroundPlate(spec, variant = 1)
            val mask = renderIconMaskShape(spec)
            val border = renderIconBorderOverlay(spec)

            val bg0Bytes = bitmapToPngBytes(bg0)
            writeZipEntry(zos, "res/drawable-xxhdpi/icon_background.png", bg0Bytes)
            writeZipEntry(zos, "res/drawable-xxhdpi/icon_bg.png", bg0Bytes)
            writeZipEntry(zos, "res/drawable-xxhdpi/icon_bg_0.png", bg0Bytes)
            writeZipEntry(zos, "res/drawable-xxhdpi/icon_bg_1.png", bitmapToPngBytes(bg1))
            writeZipEntry(zos, "res/drawable-xxhdpi/icon_mask.png", bitmapToPngBytes(mask))
            writeZipEntry(zos, "res/drawable-xxhdpi/icon_border.png", bitmapToPngBytes(border))

            val packageGlyphs = listOf(
                "com.android.settings" to GlyphType.SETTINGS_TOGGLE,
                "com.android.browser" to GlyphType.BROWSER_COMPASS,
                "com.android.chrome" to GlyphType.BROWSER_COMPASS,
                "com.google.android.apps.chrome" to GlyphType.BROWSER_COMPASS,
                "com.android.vending" to GlyphType.STORE_BAG,
                "com.android.vending.AssetBrowserActivity" to GlyphType.STORE_BAG,
                "com.android.dialer" to GlyphType.PHONE_HANDSET,
                "com.google.android.dialer" to GlyphType.PHONE_HANDSET,
                "com.android.contacts.activities.TwelveKeyDialer" to GlyphType.PHONE_HANDSET,
                "com.android.contacts" to GlyphType.CONTACTS_PERSON,
                "com.google.android.contacts" to GlyphType.CONTACTS_PERSON,
                "com.android.mms" to GlyphType.MESSAGES_BUBBLE,
                "com.google.android.apps.messaging" to GlyphType.MESSAGES_BUBBLE,
                "com.android.camera2" to GlyphType.CAMERA_LENS,
                "com.android.camera" to GlyphType.CAMERA_LENS,
                "com.android.calculator2" to GlyphType.CALCULATOR_PAD,
                "com.google.android.calculator" to GlyphType.CALCULATOR_PAD,
                "com.android.fileexplorer" to GlyphType.FILES_FOLDER,
                "com.android.documentsui" to GlyphType.FILES_FOLDER,
                "com.google.android.documentsui" to GlyphType.FILES_FOLDER,
                "com.aistudio.hypermtzlauncher.vqxk" to GlyphType.HYPER_CUBE
            )

            val glyphBytesByType = mutableMapOf<GlyphType, ByteArray>()
            for ((pkg, glyph) in packageGlyphs) {
                val pngBytes = glyphBytesByType.getOrPut(glyph) {
                    bitmapToPngBytes(renderDirectThemedAppIcon(spec, bg0, border, glyph))
                }
                writeZipEntry(zos, "res/drawable-xxhdpi/$pkg.png", pngBytes)
            }

            // Xiaomi MAML fancy_icons/com.android.deskclock/manifest.xml + layers
            val clockManifest = """
                <?xml version="1.0" encoding="utf-8"?>
                <Icon version="1" frameRate="1" width="192" height="192" screenWidth="1080">
                    <Image name="clock_dial" src="bg.png" x="96" y="96" />
                    <Image name="hour_hand" src="hour.png" x="96" y="96" pivotX="96" pivotY="96" rotation="#hour12*30+#minute/2" />
                    <Image name="minute_hand" src="minute.png" x="96" y="96" pivotX="96" pivotY="96" rotation="#minute*6" />
                    <Image name="second_hand" src="second.png" x="96" y="96" pivotX="96" pivotY="96" rotation="#second*6" />
                </Icon>
            """.trimIndent()
            val clockDialBytes = bitmapToPngBytes(renderClockDial(spec, bg0, border))
            val hourHandBytes = bitmapToPngBytes(renderClockHand(spec.secondaryAccentHex, length = 34f, stroke = 8f))
            val minuteHandBytes = bitmapToPngBytes(renderClockHand(spec.secondaryAccentHex, length = 48f, stroke = 6f))
            val secondHandBytes = bitmapToPngBytes(renderClockHand(spec.accentHex, length = 54f, stroke = 3.5f))

            for (clockPkg in listOf("com.android.deskclock", "com.google.android.deskclock")) {
                writeZipEntry(zos, "fancy_icons/$clockPkg/manifest.xml", clockManifest.toByteArray(Charsets.UTF_8))
                writeZipEntry(zos, "fancy_icons/$clockPkg/bg.png", clockDialBytes)
                writeZipEntry(zos, "fancy_icons/$clockPkg/hour.png", hourHandBytes)
                writeZipEntry(zos, "fancy_icons/$clockPkg/minute.png", minuteHandBytes)
                writeZipEntry(zos, "fancy_icons/$clockPkg/second.png", secondHandBytes)
            }

            // Xiaomi MAML fancy_icons/com.android.calendar/manifest.xml matching Midnight+Blue.mtz
            val calendarManifest = """
                <?xml version="1.0" encoding="utf-8"?>
                <Icon version="1" frameRate="0" width="192" height="192" screenWidth="1080" resDensity="480">
                    <Image x="96" y="96" align="center" alignV="center" src="com.android.calendar.png" />
                    <DateTime x="96" y="110" size="46" align="center" alignV="center" color="#ffecf0f1" format="dd" fontFamily="miui-light"/>
                </Icon>
            """.trimIndent()
            val calBgBytes = bitmapToPngBytes(renderCalendarBasePlate(spec, bg0, border))
            for (calPkg in listOf("com.android.calendar", "com.google.android.calendar")) {
                writeZipEntry(zos, "fancy_icons/$calPkg/manifest.xml", calendarManifest.toByteArray(Charsets.UTF_8))
                writeZipEntry(zos, "fancy_icons/$calPkg/com.android.calendar.png", calBgBytes)
            }
        }
        return baos.toByteArray()
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun createShapePath(style: ShapeStyle, size: Float = 192f, radius: Float = 84f): Path {
        val path = Path()
        val cx = size / 2f
        val cy = size / 2f
        when (style) {
            ShapeStyle.PRECISION_CIRCLE -> {
                path.addCircle(cx, cy, radius, Path.Direction.CW)
            }
            ShapeStyle.HYPER_SQUIRCLE -> {
                val left = cx - radius
                val top = cy - radius
                val right = cx + radius
                val bottom = cy + radius
                val corner = radius * 0.56f
                path.addRoundRect(RectF(left, top, right, bottom), corner, corner, Path.Direction.CW)
            }
            ShapeStyle.HEX_CHAMFER -> {
                val left = cx - radius
                val top = cy - radius
                val right = cx + radius
                val bottom = cy + radius
                val cut = radius * 0.45f
                path.moveTo(left + cut, top)
                path.lineTo(right - cut, top)
                path.lineTo(right, top + cut)
                path.lineTo(right, bottom - cut)
                path.lineTo(right - cut, bottom)
                path.lineTo(left + cut, bottom)
                path.lineTo(left, bottom - cut)
                path.lineTo(left, top + cut)
                path.close()
            }
        }
        return path
    }

    /**
     * Renders `icon_background.png` at 192x192 with radius 84f matching `Midnight+Blue.mtz`.
     */
    private fun renderIconBackgroundPlate(spec: BuiltInMtzSpec, variant: Int): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (variant == 0) spec.bgStartHex else spec.bgEndHex)
            style = Paint.Style.FILL
        }
        val shapePath = createShapePath(spec.shapeStyle, size.toFloat(), radius = 84f)
        canvas.drawPath(shapePath, paint)
        return bmp
    }

    /**
     * Renders `icon_mask.png` at 192x192 with radius 84f matching `Midnight+Blue.mtz`.
     */
    private fun renderIconMaskShape(spec: BuiltInMtzSpec): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val shapePath = createShapePath(spec.shapeStyle, size.toFloat(), radius = 84f)
        canvas.drawPath(shapePath, paint)
        return bmp
    }

    /**
     * Renders `icon_border.png` at 192x192 with thick outer ring (`r=76..90`, stroke=14f at r=83f)
     * matching `Midnight+Blue.mtz`.
     */
    private fun renderIconBorderOverlay(spec: BuiltInMtzSpec): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val shapePath = createShapePath(spec.shapeStyle, size.toFloat(), radius = 83f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = Color.parseColor(spec.borderRingHex)
        }
        canvas.drawPath(shapePath, paint)
        return bmp
    }

    private enum class GlyphType {
        SETTINGS_TOGGLE,
        BROWSER_COMPASS,
        STORE_BAG,
        PHONE_HANDSET,
        CONTACTS_PERSON,
        MESSAGES_BUBBLE,
        CAMERA_LENS,
        CALCULATOR_PAD,
        FILES_FOLDER,
        HYPER_CUBE
    }

    /**
     * Renders flat, solid-filled MIUI glyphs matching the visual language of `Midnight+Blue.mtz`.
     */
    private fun renderDirectThemedAppIcon(
        spec: BuiltInMtzSpec,
        bgPlate: Bitmap,
        borderOverlay: Bitmap,
        glyph: GlyphType
    ): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(bgPlate, 0f, 0f, null)

        val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(spec.secondaryAccentHex)
            style = Paint.Style.FILL
        }
        val accentFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(spec.accentHex)
            style = Paint.Style.FILL
        }
        val plateCutoutFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(spec.bgStartHex)
            style = Paint.Style.FILL
        }
        val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(spec.secondaryAccentHex)
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        when (glyph) {
            GlyphType.SETTINGS_TOGGLE -> {
                // MIUI flat toggle switch matching Midnight+Blue.mtz Settings icon
                canvas.drawRoundRect(RectF(52f, 70f, 140f, 122f), 26f, 26f, accentFill)
                canvas.drawCircle(114f, 96f, 20f, whiteFill)
            }
            GlyphType.BROWSER_COMPASS -> {
                // MIUI flat compass circle + arrow matching Midnight+Blue.mtz Browser/Chrome icon
                canvas.drawCircle(96f, 96f, 44f, accentFill)
                val arrow = Path().apply {
                    moveTo(120f, 72f)
                    lineTo(104f, 104f)
                    lineTo(72f, 120f)
                    lineTo(88f, 88f)
                    close()
                }
                canvas.drawPath(arrow, whiteFill)
            }
            GlyphType.STORE_BAG -> {
                canvas.drawArc(RectF(76f, 48f, 116f, 88f), 180f, 180f, false, whiteStroke)
                canvas.drawRoundRect(RectF(56f, 68f, 136f, 138f), 14f, 14f, whiteFill)
                val playPath = Path().apply {
                    moveTo(86f, 88f)
                    lineTo(114f, 103f)
                    lineTo(86f, 118f)
                    close()
                }
                canvas.drawPath(playPath, accentFill)
            }
            GlyphType.PHONE_HANDSET -> {
                val path = Path().apply {
                    moveTo(66f, 58f)
                    lineTo(82f, 58f)
                    lineTo(90f, 78f)
                    lineTo(80f, 88f)
                    quadTo(92f, 106f, 104f, 112f)
                    lineTo(114f, 102f)
                    lineTo(134f, 110f)
                    lineTo(134f, 126f)
                    quadTo(126f, 138f, 108f, 134f)
                    quadTo(66f, 122f, 58f, 84f)
                    quadTo(54f, 66f, 66f, 58f)
                    close()
                }
                canvas.drawPath(path, whiteFill)
            }
            GlyphType.CONTACTS_PERSON -> {
                canvas.drawCircle(96f, 74f, 20f, whiteFill)
                canvas.drawArc(RectF(56f, 100f, 136f, 156f), 180f, 180f, true, whiteFill)
            }
            GlyphType.MESSAGES_BUBBLE -> {
                // Solid white speech bubble matching Midnight+Blue.mtz Messages icon
                canvas.drawOval(RectF(50f, 58f, 142f, 126f), whiteFill)
                val tail = Path().apply {
                    moveTo(68f, 114f)
                    lineTo(54f, 134f)
                    lineTo(88f, 122f)
                    close()
                }
                canvas.drawPath(tail, whiteFill)
            }
            GlyphType.CAMERA_LENS -> {
                // Concentric lens circles matching Midnight+Blue.mtz Camera icon
                canvas.drawCircle(96f, 96f, 44f, accentFill)
                canvas.drawCircle(96f, 96f, 24f, plateCutoutFill)
                canvas.drawCircle(96f, 96f, 10f, whiteFill)
            }
            GlyphType.CALCULATOR_PAD -> {
                // Solid rounded calculator with button grid matching Midnight+Blue.mtz Calculator icon
                canvas.drawRoundRect(RectF(62f, 52f, 130f, 140f), 10f, 10f, whiteFill)
                canvas.drawRoundRect(RectF(70f, 60f, 122f, 78f), 4f, 4f, plateCutoutFill)
                for (r in 0..2) {
                    for (c in 0..2) {
                        val cx = 77f + c * 19f
                        val cy = 90f + r * 18f
                        canvas.drawRoundRect(RectF(cx - 5f, cy - 5f, cx + 5f, cy + 5f), 2f, 2f, plateCutoutFill)
                    }
                }
            }
            GlyphType.FILES_FOLDER -> {
                // Solid flat folder matching Midnight+Blue.mtz Files icon
                val folderPath = Path().apply {
                    moveTo(52f, 64f)
                    lineTo(84f, 64f)
                    lineTo(94f, 74f)
                    lineTo(140f, 74f)
                    lineTo(140f, 132f)
                    lineTo(52f, 132f)
                    close()
                }
                canvas.drawPath(folderPath, whiteFill)
            }
            GlyphType.HYPER_CUBE -> {
                canvas.drawRoundRect(RectF(58f, 58f, 90f, 90f), 6f, 6f, accentFill)
                canvas.drawRoundRect(RectF(102f, 58f, 134f, 90f), 6f, 6f, whiteFill)
                canvas.drawRoundRect(RectF(58f, 102f, 90f, 134f), 6f, 6f, whiteFill)
                canvas.drawRoundRect(RectF(102f, 102f, 134f, 134f), 6f, 6f, accentFill)
            }
        }

        canvas.drawBitmap(borderOverlay, 0f, 0f, null)
        return bmp
    }

    private fun renderClockDial(spec: BuiltInMtzSpec, bgPlate: Bitmap, borderOverlay: Bitmap): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(bgPlate, 0f, 0f, null)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(spec.secondaryAccentHex)
            style = Paint.Style.STROKE
            strokeWidth = 9f
        }
        canvas.drawCircle(96f, 96f, 44f, ringPaint)
        canvas.drawBitmap(borderOverlay, 0f, 0f, null)
        return bmp
    }

    private fun renderClockHand(colorHex: String, length: Float, stroke: Float): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(96f, 96f, 96f, 96f - length, paint)
        canvas.drawCircle(96f, 96f, stroke * 0.9f, paint)
        return bmp
    }

    private fun renderCalendarBasePlate(spec: BuiltInMtzSpec, bgPlate: Bitmap, borderOverlay: Bitmap): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(bgPlate, 0f, 0f, null)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(spec.secondaryAccentHex)
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawRoundRect(RectF(54f, 60f, 138f, 140f), 10f, 10f, strokePaint)
        canvas.drawLine(54f, 82f, 138f, 82f, strokePaint)
        canvas.drawLine(74f, 50f, 74f, 66f, strokePaint)
        canvas.drawLine(118f, 50f, 118f, 66f, strokePaint)

        canvas.drawBitmap(borderOverlay, 0f, 0f, null)
        return bmp
    }

    private fun renderThemeWallpaper(spec: BuiltInMtzSpec): Bitmap {
        val w = 320
        val h = 560
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                w.toFloat(),
                h.toFloat(),
                Color.parseColor("#111921"),
                Color.parseColor(spec.bgStartHex),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(spec.accentHex)
            alpha = 32
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        for (r in 1..5) {
            canvas.drawCircle(w * 0.8f, h * 0.22f, r * 45f, linePaint)
        }
        return bmp
    }

    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        return out.toByteArray()
    }
}
