package com.example.mtz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.example.domain.model.IconPipelineBreakdown
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MamlFancyIconSpec
import com.example.domain.model.MtzThemeMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Implements the Xiaomi MIUI / HyperOS Icon Theme Rendering API (`<IconTransform>`).
 *
 * Resolution Pipeline Order:
 * 1. Manual Per-App User Override (`icon_overrides` table)
 * 2. Xiaomi MAML `fancy_icons/<package>/manifest.xml` Dynamic Renderer
 * 3. Direct Package / Component / MIUI Alias PNG in `res/drawable-xxhdpi/`
 * 4. Xiaomi `<IconTransform>` Dynamic Pipeline:
 *    - `icon_background.png` + `<Filter name="GrayScale"><Param name="BlackColor" value="#..."/></Filter>`
 *    - `<PointsMapping>` (`(0,0)-(90,90) -> (10,10)-(80,80)`)
 *    - `<Filter name="Levels"><Param name="InputMin" value="95"/></Filter>` + Theme Duotone / Emerald Shader
 *    - `icon_mask.png` (`PorterDuff.Mode.DST_IN`)
 *    - `icon_border.png` (`PorterDuff.Mode.SRC_OVER`)
 * 5. Fallback Unmodified PackageManager Drawable
 */
class XiaomiIconRenderEngine {

    companion object {
        const val TARGET_ICON_PX = 160
        private const val SHADER_WORK_PX = 64

        private val XIAOMI_PACKAGE_ALIASES: Map<String, List<String>> = mapOf(
            "com.google.android.dialer" to listOf(
                "com.android.contacts.activities.TwelveKeyDialer",
                "com.android.dialer",
                "com.android.incallui",
                "com.android.providers.telephony"
            ),
            "com.android.dialer" to listOf(
                "com.android.contacts.activities.TwelveKeyDialer",
                "com.google.android.dialer",
                "com.android.providers.telephony"
            ),
            "com.google.android.contacts" to listOf(
                "com.android.contacts",
                "com.android.contacts.activities.PeopleActivity",
                "com.android.providers.contacts"
            ),
            "com.google.android.apps.messaging" to listOf(
                "com.android.mms",
                "com.android.mms.ui.MmsTabActivity"
            ),
            "com.google.android.deskclock" to listOf(
                "com.android.deskclock",
                "com.android.deskclock.DeskClockTabActivity"
            ),
            "com.android.deskclock" to listOf(
                "com.google.android.deskclock",
                "com.android.deskclock.DeskClockTabActivity"
            ),
            "com.google.android.calendar" to listOf(
                "com.android.calendar",
                "com.miui.calendar",
                "com.android.providers.calendar"
            ),
            "com.android.calendar" to listOf(
                "com.google.android.calendar",
                "com.miui.calendar",
                "com.android.providers.calendar"
            ),
            "com.google.android.calculator" to listOf(
                "com.android.calculator2",
                "com.miui.calculator"
            ),
            "com.android.chrome" to listOf(
                "com.android.browser",
                "com.mi.globalbrowser",
                "com.google.android.apps.chrome"
            ),
            "com.google.android.documentsui" to listOf(
                "com.android.fileexplorer",
                "com.android.documentsui",
                "com.mi.android.globalFileexplorer",
                "com.android.providers.downloads.ui"
            ),
            "com.android.documentsui" to listOf(
                "com.android.fileexplorer",
                "com.google.android.documentsui",
                "com.android.providers.downloads.ui"
            ),
            "com.android.camera2" to listOf(
                "com.android.camera",
                "com.miui.camera"
            ),
            "com.android.vending" to listOf(
                "com.android.vending.AssetBrowserActivity",
                "com.xiaomi.market",
                "com.miui.supermarket"
            )
        )
    }

    private val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val resolvedIconCache = java.util.concurrent.ConcurrentHashMap<String, ResolvedIconResult>()

    fun clearBitmapCache() {
        bitmapCache.clear()
        resolvedIconCache.clear()
    }

    data class ResolvedIconResult(
        val bitmap: Bitmap,
        val source: IconResolutionSource,
        val matchedAssetName: String,
        val renderKey: String
    )

    fun resolveAppIcon(
        packageName: String,
        activityName: String,
        rawAppBitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        customOverrideAssetKey: String?,
        calendar: Calendar = Calendar.getInstance()
    ): ResolvedIconResult {
        val effectiveScale = if (config.useCustomScaleOverride) config.customScaleValue else theme.iconScale
        val scaleRounded = (effectiveScale * 100).toInt()
        val isMamlCandidate = config.enableFancyMamlIcons && findFancyIconSpec(packageName, activityName, theme) != null
        val mamlMinuteBucket = if (isMamlCandidate) calendar.get(Calendar.MINUTE) else 0

        val cacheKey = buildString(112) {
            append(theme.id).append('|')
            append(theme.filterBlackColorHex).append('|')
            append(if (theme.useEmeraldThirdPartyShader) 'E' else 'D').append('|')
            append(packageName).append('|')
            append(scaleRounded).append('|')
            append(if (config.enableDynamicMasking) '1' else '0')
            append(if (config.enableThirdPartyColorFilter) '1' else '0')
            append(if (config.enableFancyMamlIcons) '1' else '0')
            append(if (config.enableIconBorderOverlay) '1' else '0')
            append(if (config.enableMultiBgHashSelection) '1' else '0')
            append('|').append(customOverrideAssetKey.orEmpty())
            append('|').append(mamlMinuteBucket)
        }

        resolvedIconCache[cacheKey]?.let { return it }

        val computed = computeAppIconInternal(
            packageName = packageName,
            activityName = activityName,
            rawAppBitmap = rawAppBitmap,
            theme = theme,
            config = config,
            customOverrideAssetKey = customOverrideAssetKey,
            calendar = calendar,
            renderKey = cacheKey
        )

        resolvedIconCache[cacheKey] = computed
        return computed
    }

    private fun computeAppIconInternal(
        packageName: String,
        activityName: String,
        rawAppBitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        customOverrideAssetKey: String?,
        calendar: Calendar,
        renderKey: String
    ): ResolvedIconResult {
        // Stage 1: Check Per-App Manual Override
        if (!customOverrideAssetKey.isNullOrBlank()) {
            val overridePath = theme.directIconsMap[customOverrideAssetKey]
            val overrideBmp = overridePath?.let { decodeBitmapCached(it) }
            if (overrideBmp != null) {
                return ResolvedIconResult(
                    bitmap = scaleToTarget(overrideBmp),
                    source = IconResolutionSource.MANUAL_OVERRIDE,
                    matchedAssetName = "$customOverrideAssetKey.png",
                    renderKey = renderKey
                )
            }
        }

        // Stage 2: Check Xiaomi MAML fancy_icons/<package>/manifest.xml
        if (config.enableFancyMamlIcons) {
            val fancySpec = findFancyIconSpec(packageName, activityName, theme)
            if (fancySpec != null) {
                val renderedFancy = renderMamlFancyIcon(fancySpec, calendar)
                if (renderedFancy != null) {
                    return ResolvedIconResult(
                        bitmap = renderedFancy,
                        source = IconResolutionSource.MTZ_FANCY_MAML,
                        matchedAssetName = "fancy_icons/${fancySpec.targetPackage}/manifest.xml",
                        renderKey = renderKey
                    )
                }
            }
        }

        // Stage 3: Direct Package / Component / MIUI Alias PNG Match in res/drawable-xxhdpi/
        val directMatch = findDirectIconPath(packageName, activityName, theme)
        if (directMatch != null) {
            val directBmp = decodeBitmapCached(directMatch.second)
            if (directBmp != null) {
                return ResolvedIconResult(
                    bitmap = scaleToTarget(directBmp),
                    source = IconResolutionSource.MTZ_DIRECT_MAP,
                    matchedAssetName = "res/drawable-xxhdpi/${directMatch.first}.png",
                    renderKey = renderKey
                )
            }
        }

        // Stage 4: Xiaomi <IconTransform> Dynamic Pipeline (icon_background + PointsMapping + IconFilters + icon_mask + icon_border)
        if (config.enableDynamicMasking) {
            val composite = compositeDynamicMaskedIconFast(
                packageName = packageName,
                rawAppBitmap = rawAppBitmap,
                theme = theme,
                config = config
            )
            val filterLabel = if (config.enableThirdPartyColorFilter) {
                "IconTransform(${theme.filterBlackColorHex}) + "
            } else {
                ""
            }
            return ResolvedIconResult(
                bitmap = composite,
                source = IconResolutionSource.MTZ_DYNAMIC_MASK,
                matchedAssetName = "${filterLabel}icon_mask.png + icon_border.png",
                renderKey = renderKey
            )
        }

        // Stage 5: Raw Unmodified System Icon
        val rawOut = scaleToTarget(rawAppBitmap)
        return ResolvedIconResult(
            bitmap = rawOut,
            source = IconResolutionSource.SYSTEM_RAW,
            matchedAssetName = "PackageManager.getActivityIcon()",
            renderKey = renderKey
        )
    }

    /**
     * Renders the theme's background plate at `TARGET_ICON_PX x TARGET_ICON_PX`.
     * In MIUI themes like `Midnight+Blue.mtz`, `icon_background.png` is combined with
     * `<Filter name="GrayScale"><Param name="BlackColor" value="#465967"/></Filter>` and
     * clipped by `icon_mask.png` (`DST_IN`) so the inner circular plate is solid `#465967`.
     */
    private fun renderThemedBackgroundPlate(
        packageName: String,
        theme: MtzThemeMetadata,
        enableMultiBgHash: Boolean,
        maskBitmap: Bitmap?
    ): Bitmap {
        val plate = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(plate)
        val smoothPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val blackColorInt = parseColorSafe(theme.filterBlackColorHex, Color.parseColor("#465967"))
        val bgPath = selectBgPath(packageName, theme, enableMultiBgHash)
        val rawBgBitmap = bgPath?.let { decodeBitmapCached(it) }

        if (rawBgBitmap != null) {
            // Fill with theme BlackColor first, then draw icon_background.png tinted/blended
            // so semi-transparent gray plates (like Midnight+Blue.mtz's #808080 alpha=204)
            // render as a solid theme-colored plate (#465967)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = blackColorInt
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, TARGET_ICON_PX.toFloat(), TARGET_ICON_PX.toFloat(), fillPaint)

            if (!isNeutralGrayPlate(rawBgBitmap)) {
                val bgMatrix = Matrix().apply {
                    postScale(
                        TARGET_ICON_PX.toFloat() / rawBgBitmap.width.coerceAtLeast(1),
                        TARGET_ICON_PX.toFloat() / rawBgBitmap.height.coerceAtLeast(1)
                    )
                }
                canvas.drawBitmap(rawBgBitmap, bgMatrix, smoothPaint)
            }
        } else {
            // Fallback circular plate in BlackColor if theme only provides transform_config.xml
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = blackColorInt
                style = Paint.Style.FILL
            }
            val r = TARGET_ICON_PX * (84f / 192f)
            canvas.drawCircle(TARGET_ICON_PX / 2f, TARGET_ICON_PX / 2f, r, fillPaint)
        }

        // Clip plate to icon_mask.png (or icon_background.png alpha channel)
        val clipMask = maskBitmap ?: rawBgBitmap
        if (clipMask != null) {
            applyAlphaMaskInPlace(plate, clipMask)
        }

        return plate
    }

    private fun applyAlphaMaskInPlace(target: Bitmap, mask: Bitmap) {
        val scaledMask = scaleToTarget(mask)
        val n = TARGET_ICON_PX * TARGET_ICON_PX
        val targetPixels = IntArray(n)
        val maskPixels = IntArray(n)
        target.getPixels(targetPixels, 0, TARGET_ICON_PX, 0, 0, TARGET_ICON_PX, TARGET_ICON_PX)
        scaledMask.getPixels(maskPixels, 0, TARGET_ICON_PX, 0, 0, TARGET_ICON_PX, TARGET_ICON_PX)
        for (i in 0 until n) {
            val t = targetPixels[i]
            val tA = t ushr 24
            if (tA == 0) continue
            val mA = maskPixels[i] ushr 24
            if (mA == 0) {
                targetPixels[i] = 0
            } else if (mA < 255) {
                val outA = (tA * mA) / 255
                targetPixels[i] = (t and 0x00FFFFFF) or (outA shl 24)
            }
        }
        target.setPixels(targetPixels, 0, TARGET_ICON_PX, 0, 0, TARGET_ICON_PX, TARGET_ICON_PX)
    }

    private fun isNeutralGrayPlate(bitmap: Bitmap): Boolean {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        if (cx <= 0 || cy <= 0) return false
        val c = bitmap.getPixel(cx, cy)
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val delta = max(r, max(g, b)) - min(r, min(g, b))
        return delta <= 8
    }

    /**
     * Fast-path `<IconTransform>` compositor for Workspace & App Drawer rendering.
     */
    private fun compositeDynamicMaskedIconFast(
        packageName: String,
        rawAppBitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig
    ): Bitmap {
        val effectiveScale = if (config.useCustomScaleOverride) {
            config.customScaleValue.coerceIn(0.45f, 1.0f)
        } else {
            theme.iconScale.coerceIn(0.45f, 1.0f)
        }

        val maskBitmap = theme.iconMaskPath?.let { decodeBitmapCached(it) }
        val borderBitmap = if (config.enableIconBorderOverlay) {
            theme.iconBorderPath?.let { decodeBitmapCached(it) }
        } else {
            null
        }

        val bgPlateBitmap = renderThemedBackgroundPlate(
            packageName = packageName,
            theme = theme,
            enableMultiBgHash = config.enableMultiBgHashSelection,
            maskBitmap = maskBitmap
        )

        val filteredInput = if (config.enableThirdPartyColorFilter) {
            applyThemeIconTransformFilter(rawAppBitmap, theme)
        } else {
            ensureSoftwareBitmap(rawAppBitmap)
        }

        val smoothPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Scale and mask the foreground layer according to <PointsMapping> (effectiveScale)
        val maskedLayer = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskedLayer)
        val offset = TARGET_ICON_PX * (1f - effectiveScale) / 2f
        val scaleX = (TARGET_ICON_PX.toFloat() / filteredInput.width.coerceAtLeast(1)) * effectiveScale
        val scaleY = (TARGET_ICON_PX.toFloat() / filteredInput.height.coerceAtLeast(1)) * effectiveScale
        val matrix = Matrix().apply {
            postScale(scaleX, scaleY)
            postTranslate(offset, offset)
        }
        maskCanvas.drawBitmap(filteredInput, matrix, smoothPaint)

        if (maskBitmap != null) {
            applyAlphaMaskInPlace(maskedLayer, maskBitmap)
        }

        val output = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(output)
        outCanvas.drawBitmap(bgPlateBitmap, 0f, 0f, smoothPaint)
        outCanvas.drawBitmap(maskedLayer, 0f, 0f, smoothPaint)

        if (borderBitmap != null) {
            val bdrMatrix = Matrix().apply {
                postScale(
                    TARGET_ICON_PX.toFloat() / borderBitmap.width.coerceAtLeast(1),
                    TARGET_ICON_PX.toFloat() / borderBitmap.height.coerceAtLeast(1)
                )
            }
            outCanvas.drawBitmap(borderBitmap, bdrMatrix, smoothPaint)
        } else if (config.enableIconBorderOverlay) {
            // Fallback circular border ring matching MIUI 192x192 r=68..80 geometry if theme omitted icon_border.png
            val blackColorInt = parseColorSafe(theme.filterBlackColorHex, Color.parseColor("#465967"))
            val ringColor = darkenColor(blackColorInt, 0.85f)
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColor
                style = Paint.Style.STROKE
                strokeWidth = TARGET_ICON_PX * (12f / 192f)
            }
            val ringRadius = TARGET_ICON_PX * (74f / 192f)
            outCanvas.drawCircle(TARGET_ICON_PX / 2f, TARGET_ICON_PX / 2f, ringRadius, ringPaint)
        }

        return output
    }

    /**
     * Generates a complete step-by-step visual and XML breakdown of how the Xiaomi Icon Theme API
     * transforms a specific app's icon for the API Pipeline Inspector tab.
     */
    fun buildPipelineBreakdown(
        appLabel: String,
        packageName: String,
        activityName: String,
        rawAppBitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        customOverrideAssetKey: String?,
        calendar: Calendar = Calendar.getInstance()
    ): IconPipelineBreakdown {
        val normalizedRaw = scaleToTarget(rawAppBitmap)
        val effectiveScale = if (config.useCustomScaleOverride) {
            config.customScaleValue.coerceIn(0.45f, 1.0f)
        } else {
            theme.iconScale.coerceIn(0.45f, 1.0f)
        }

        val maskBitmap = theme.iconMaskPath?.let { decodeBitmapCached(it)?.let(::scaleToTarget) }
        val bgBitmap = renderThemedBackgroundPlate(
            packageName = packageName,
            theme = theme,
            enableMultiBgHash = config.enableMultiBgHashSelection,
            maskBitmap = maskBitmap
        )
        val borderBitmap = if (config.enableIconBorderOverlay) {
            theme.iconBorderPath?.let { decodeBitmapCached(it)?.let(::scaleToTarget) }
        } else {
            null
        }

        val colorFilteredRaw = if (config.enableThirdPartyColorFilter) {
            scaleToTarget(applyThemeIconTransformFilter(normalizedRaw, theme))
        } else {
            normalizedRaw
        }

        val scaledInputBitmap = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val scaledCanvas = Canvas(scaledInputBitmap)
        val matrix = Matrix().apply {
            val offset = TARGET_ICON_PX * (1f - effectiveScale) / 2f
            val scaleFactor = (TARGET_ICON_PX.toFloat() / colorFilteredRaw.width.coerceAtLeast(1)) * effectiveScale
            postScale(scaleFactor, scaleFactor)
            postTranslate(offset, offset)
        }
        val smoothPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        scaledCanvas.drawBitmap(colorFilteredRaw, matrix, smoothPaint)

        val maskedLayerBitmap = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskedLayerBitmap)
        maskCanvas.drawBitmap(scaledInputBitmap, 0f, 0f, smoothPaint)
        if (maskBitmap != null) {
            applyAlphaMaskInPlace(maskedLayerBitmap, maskBitmap)
        }

        val dynamicComposite = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(dynamicComposite)
        finalCanvas.drawBitmap(bgBitmap, 0f, 0f, smoothPaint)
        finalCanvas.drawBitmap(maskedLayerBitmap, 0f, 0f, smoothPaint)
        if (borderBitmap != null) {
            finalCanvas.drawBitmap(borderBitmap, 0f, 0f, smoothPaint)
        }

        val directMatch = findDirectIconPath(packageName, activityName, theme)
        val fancySpec = if (config.enableFancyMamlIcons) findFancyIconSpec(packageName, activityName, theme) else null

        val (finalBitmap, source, matchedEntry) = when {
            !customOverrideAssetKey.isNullOrBlank() && theme.directIconsMap.containsKey(customOverrideAssetKey) -> {
                val bmp = decodeBitmapCached(theme.directIconsMap.getValue(customOverrideAssetKey))?.let(::scaleToTarget) ?: dynamicComposite
                Triple(bmp, IconResolutionSource.MANUAL_OVERRIDE, "override -> $customOverrideAssetKey.png")
            }
            fancySpec != null -> {
                val bmp = renderMamlFancyIcon(fancySpec, calendar) ?: dynamicComposite
                Triple(bmp, IconResolutionSource.MTZ_FANCY_MAML, "fancy_icons/${fancySpec.targetPackage}/manifest.xml")
            }
            directMatch != null -> {
                val bmp = decodeBitmapCached(directMatch.second)?.let(::scaleToTarget) ?: dynamicComposite
                Triple(bmp, IconResolutionSource.MTZ_DIRECT_MAP, "res/drawable-xxhdpi/${directMatch.first}.png")
            }
            config.enableDynamicMasking -> {
                val filterDesc = if (config.enableThirdPartyColorFilter) "IconTransform(${theme.filterBlackColorHex}) + " else ""
                Triple(dynamicComposite, IconResolutionSource.MTZ_DYNAMIC_MASK, "${filterDesc}icon_mask.png + icon_border.png")
            }
            else -> {
                Triple(normalizedRaw, IconResolutionSource.SYSTEM_RAW, "PackageManager.getActivityIcon()")
            }
        }

        val xmlSnippet = buildDiagnosticXmlSnippet(
            packageName = packageName,
            source = source,
            theme = theme,
            effectiveScale = effectiveScale,
            enableColorFilter = config.enableThirdPartyColorFilter,
            matchedEntry = matchedEntry,
            fancySpec = fancySpec
        )

        return IconPipelineBreakdown(
            appLabel = appLabel,
            packageName = packageName,
            activityName = activityName,
            resolutionSource = source,
            appliedScale = effectiveScale,
            matchedEntryPath = matchedEntry,
            rawAppBitmap = normalizedRaw,
            backgroundPlateBitmap = bgBitmap,
            scaledInputBitmap = scaledInputBitmap,
            maskTemplateBitmap = maskBitmap,
            maskedLayerBitmap = maskedLayerBitmap,
            borderOverlayBitmap = borderBitmap,
            finalOutputBitmap = finalBitmap,
            xmlDiagnosticSnippet = xmlSnippet
        )
    }

    /**
     * Theme-driven `<IconTransform>` filter supporting both:
     * 1. Standard MIUI `<IconTransform>` (`GrayScale` `BlackColor="#465967"` + `Levels` `InputMin="95"`,
     *    as used in `Midnight+Blue.mtz`) where 3rd-party icons (`Maps`, `Photos`, `YT Music`, `Drive`, etc.)
     *    are rendered in crisp `#ECF0F1` off-white & two-tone light slate (`#B8C5CE` / `#90A3B2`)
     *    over the theme's `BlackColor` circular plate.
     * 2. Emerald & White `<ThirdPartyColorShader>` (`useEmeraldThirdPartyShader = true`, as used in
     *    `CrimsonToWhite_Emerald.mtz`) where red shades become crisp white (`#ECF0F1`) and other
     *    chromatic facets become a 3-tone Emerald/Mint palette (`#A7F3D0` / `#34D399` / `#10B981`).
     *
     * Also strips outer white adaptive icon plates (`isOuterWhitePlate`) and normalizes the remaining
     * foreground glyph bounding box so adaptive icons have the exact same visual symbol proportion
     * as native MIUI direct icons (`Messages`, `Chrome`, `Settings`, `Files`).
     */
    fun applyThemeIconTransformFilter(
        source: Bitmap,
        theme: MtzThemeMetadata
    ): Bitmap {
        return try {
            val softwareBmp = ensureSoftwareBitmap(source)
            val workBmp = if (softwareBmp.width != SHADER_WORK_PX || softwareBmp.height != SHADER_WORK_PX) {
                Bitmap.createScaledBitmap(softwareBmp, SHADER_WORK_PX, SHADER_WORK_PX, true)
                    .let(::ensureSoftwareBitmap)
            } else {
                softwareBmp
            }

            val w = workBmp.width
            val h = workBmp.height
            val total = w * h
            val pixels = IntArray(total)
            workBmp.getPixels(pixels, 0, w, 0, 0, w, h)

            val isWhiteLike = BooleanArray(total)
            val isRedShade = BooleanArray(total)

            for (i in 0 until total) {
                val c = pixels[i]
                val a = c ushr 24
                if (a < 20) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val delta = maxC - minC

                if (maxC >= 198 && delta <= 40) {
                    isWhiteLike[i] = true
                } else if (r >= 115 && delta >= 32 && r == maxC && (r - g) >= 35 && (r - b) >= 28 && g <= (r * 3) / 4) {
                    isRedShade[i] = true
                }
            }

            // Perimeter BFS flood-fill to strip outer white adaptive icon plates (e.g. Maps, Photos, YT Music, Drive)
            val isOuterWhitePlate = BooleanArray(total)
            val queue = IntArray(total)
            var head = 0
            var tail = 0

            fun enqueueIfSeed(idx: Int) {
                val a = pixels[idx] ushr 24
                if (!isOuterWhitePlate[idx] && (a < 20 || isWhiteLike[idx])) {
                    isOuterWhitePlate[idx] = true
                    queue[tail++] = idx
                }
            }

            for (x in 0 until w) {
                enqueueIfSeed(x)
                enqueueIfSeed((h - 1) * w + x)
            }
            for (y in 0 until h) {
                enqueueIfSeed(y * w)
                enqueueIfSeed(y * w + (w - 1))
            }

            while (head < tail) {
                val curr = queue[head++]
                val cx = curr % w
                val cy = curr / w

                if (cx > 0) {
                    val left = curr - 1
                    if (!isOuterWhitePlate[left] && isWhiteLike[left]) {
                        isOuterWhitePlate[left] = true
                        queue[tail++] = left
                    }
                }
                if (cx < w - 1) {
                    val right = curr + 1
                    if (!isOuterWhitePlate[right] && isWhiteLike[right]) {
                        isOuterWhitePlate[right] = true
                        queue[tail++] = right
                    }
                }
                if (cy > 0) {
                    val up = curr - w
                    if (!isOuterWhitePlate[up] && isWhiteLike[up]) {
                        isOuterWhitePlate[up] = true
                        queue[tail++] = up
                    }
                }
                if (cy < h - 1) {
                    val down = curr + w
                    if (!isOuterWhitePlate[down] && isWhiteLike[down]) {
                        isOuterWhitePlate[down] = true
                        queue[tail++] = down
                    }
                }
            }

            var outerWhitePixels = 0
            var innerWhitePixels = 0
            var redCount = 0
            for (i in 0 until total) {
                val a = pixels[i] ushr 24
                if (a >= 20 && isWhiteLike[i]) {
                    if (isOuterWhitePlate[i]) {
                        outerWhitePixels++
                    } else {
                        innerWhitePixels++
                    }
                }
                if (isRedShade[i]) redCount++
            }
            val hadWhiteAdaptivePlate = outerWhitePixels > (total / 10)
            val hasLargeRedCore = redCount > (total / 14)
            val hasInnerWhiteSymbol = innerWhitePixels > (total / 120)

            val blackColorInt = parseColorSafe(theme.filterBlackColorHex, Color.parseColor("#465967"))
            val bR = (blackColorInt shr 16) and 0xFF
            val bG = (blackColorInt shr 8) and 0xFF
            val bB = blackColorInt and 0xFF

            // Off-white glyph target matching Midnight+Blue.mtz (#ECF0F1)
            val wR = 236
            val wG = 240
            val wB = 241

            fun blendTowardWhite(t: Float, alpha: Int): Int {
                val clamped = t.coerceIn(0f, 1f)
                val r = (bR + ((wR - bR) * clamped).toInt()).coerceIn(0, 255)
                val g = (bG + ((wG - bG) * clamped).toInt()).coerceIn(0, 255)
                val b = (bB + ((wB - bB) * clamped).toInt()).coerceIn(0, 255)
                return (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }

            val useEmeraldPalette = theme.useEmeraldThirdPartyShader
            val inputMin = theme.filterLevelsInputMin.coerceIn(20, 200)

            val outPixels = IntArray(total)
            var minFgX = w
            var minFgY = h
            var maxFgX = -1
            var maxFgY = -1

            for (i in 0 until total) {
                val c = pixels[i]
                val a = c ushr 24
                if (a < 20 || (isOuterWhitePlate[i] && isWhiteLike[i])) {
                    outPixels[i] = 0
                    continue
                }

                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val delta = maxC - minC
                val luma = (r * 77 + g * 150 + b * 29) shr 8

                if (!useEmeraldPalette) {
                    // Authentic MIUI <IconTransform> Duotone (GrayScale BlackColor + Levels InputMin)
                    // as used in Midnight+Blue.mtz
                    if (isWhiteLike[i]) {
                        if (hadWhiteAdaptivePlate && !hasLargeRedCore) {
                            // Inner pinhole cutout inside a colored adaptive glyph (e.g. Maps center dot)
                            // -> transparent so the BlackColor plate (#465967) shows through
                            outPixels[i] = 0
                            continue
                        } else {
                            // Inner white symbol (e.g. YT Music play triangle & concentric ring) -> Crisp #ECF0F1
                            outPixels[i] = blendTowardWhite(1.0f, a)
                        }
                    } else if (hasLargeRedCore && hasInnerWhiteSymbol && isRedShade[i]) {
                        // In MIUI Levels(InputMin=95), red badge backgrounds (luma ~76 <= 95, e.g. YT Music / YouTube)
                        // crush to 0.0 -> BlackColor (#465967), leaving the crisp #ECF0F1 inner ring & play triangle
                        outPixels[i] = 0
                        continue
                    } else if (isRedShade[i] || (r >= g && r >= b && delta >= 32)) {
                        // Warm red/orange/yellow glyph facet (e.g. Maps top-left, Photos top/right petals, Drive top)
                        // -> Crisp off-white (#ECF0F1)
                        outPixels[i] = blendTowardWhite(0.98f, a)
                    } else if (g >= r && g >= b && delta >= 28) {
                        // Green/teal glyph facet (e.g. Maps middle, Photos bottom petal, Drive left ribbon)
                        // -> Soft ice-slate (#BCC8D1)
                        outPixels[i] = blendTowardWhite(0.68f, a)
                    } else if (b >= r && b >= g && delta >= 28) {
                        // Blue/indigo glyph facet (e.g. Maps tip, Photos left petal, Drive bottom ribbon)
                        // -> Medium light-slate (#92A5B4)
                        outPixels[i] = blendTowardWhite(0.45f, a)
                    } else {
                        // Apply MIUI Levels(InputMin) + GrayScale(BlackColor) ramp
                        val normalizedLevels = if (luma <= inputMin) {
                            0.38f
                        } else {
                            0.45f + 0.55f * ((luma - inputMin).toFloat() / (255 - inputMin).coerceAtLeast(1))
                        }
                        outPixels[i] = blendTowardWhite(normalizedLevels, a)
                    }
                } else {
                    // Red-to-White & Green Palette (<ThirdPartyColorShader> for CrimsonToWhite_Emerald.mtz)
                    if (isRedShade[i]) {
                        // Red shades -> Crisp White (#ECF0F1)
                        outPixels[i] = (a shl 24) or (236 shl 16) or (242 shl 8) or 246
                    } else if (isWhiteLike[i]) {
                        if (hasLargeRedCore) {
                            // Inner white symbol inside a red badge (e.g. YT Music play triangle) -> Bright Emerald (#34D399)
                            outPixels[i] = (a shl 24) or (52 shl 16) or (211 shl 8) or 153
                        } else if (hadWhiteAdaptivePlate) {
                            // Inner pinhole cutout -> transparent so dark plate shows through
                            outPixels[i] = 0
                            continue
                        } else {
                            outPixels[i] = (a shl 24) or (209 shl 16) or (250 shl 8) or 229
                        }
                    } else if (r >= b && g >= b && delta >= 28) {
                        // Warm yellow/orange -> Pale Mint (#A7F3D0)
                        outPixels[i] = (a shl 24) or (167 shl 16) or (243 shl 8) or 208
                    } else if (g >= r && g >= b && delta >= 28) {
                        // Green/teal -> Primary Emerald (#34D399)
                        outPixels[i] = (a shl 24) or (52 shl 16) or (211 shl 8) or 153
                    } else {
                        // Blue/purple/neutral -> Deep Emerald Jade (#10B981)
                        outPixels[i] = (a shl 24) or (16 shl 16) or (185 shl 8) or 129
                    }
                }

                // Track foreground glyph bounding box for non-transparent output pixels
                val px = i % w
                val py = i / w
                if (px < minFgX) minFgX = px
                if (py < minFgY) minFgY = py
                if (px > maxFgX) maxFgX = px
                if (py > maxFgY) maxFgY = py
            }

            val rawFilteredBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            rawFilteredBmp.setPixels(outPixels, 0, w, 0, 0, w, h)

            // If we stripped an outer white adaptive plate (as in Maps, Photos, YT Music, Drive),
            // normalize the remaining glyph's bounding box so its visual proportion matches
            // native MIUI direct icons (like Messages, Chrome, Settings, Files).
            val fgW = maxFgX - minFgX + 1
            val fgH = maxFgY - minFgY + 1
            val fgSpan = max(fgW, fgH)
            if (hadWhiteAdaptivePlate && fgSpan in 10..(w - 10)) {
                val normalizedBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val normCanvas = Canvas(normalizedBmp)
                val targetSpan = w * 0.66f // After 0.78x PointsMapping -> 0.515x of full icon (matches Messages/Chrome)
                val scale = targetSpan / fgSpan.toFloat()
                val drawW = fgW * scale
                val drawH = fgH * scale
                val dstLeft = (w - drawW) / 2f
                val dstTop = (h - drawH) / 2f
                val srcRect = Rect(minFgX, minFgY, maxFgX + 1, maxFgY + 1)
                val dstRect = RectF(dstLeft, dstTop, dstLeft + drawW, dstTop + drawH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                normCanvas.drawBitmap(rawFilteredBmp, srcRect, dstRect, paint)
                return normalizedBmp
            }

            rawFilteredBmp
        } catch (_: Exception) {
            ensureSoftwareBitmap(source)
        }
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (((color shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.isRecycled) {
            return bitmap
        }
        return try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } catch (_: Exception) {
            val fallback = Bitmap.createBitmap(
                bitmap.width.coerceAtLeast(1),
                bitmap.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(fallback)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            fallback
        }
    }

    private fun renderMamlFancyIcon(spec: MamlFancyIconSpec, calendar: Calendar): Bitmap? {
        val output = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val scaleX = TARGET_ICON_PX.toFloat() / spec.width.coerceAtLeast(1)
        val scaleY = TARGET_ICON_PX.toFloat() / spec.height.coerceAtLeast(1)
        canvas.save()
        canvas.scale(scaleX, scaleY)

        for (layer in spec.layers) {
            if (layer.imageFileName != null) {
                val imgFile = File(spec.directoryPath, layer.imageFileName)
                val layerBmp = decodeBitmapCached(imgFile.absolutePath) ?: continue
                val angle = evaluateMamlRotationExpression(layer.rotationExpression, calendar)

                canvas.save()
                if (angle != 0f) {
                    canvas.rotate(angle, layer.pivotX, layer.pivotY)
                }
                val drawLeft = layer.centerX - (layerBmp.width / 2f)
                val drawTop = layer.centerY - (layerBmp.height / 2f)
                canvas.drawBitmap(layerBmp, drawLeft, drawTop, paint)
                canvas.restore()
            } else if (layer.textExpression != null) {
                val textValue = evaluateMamlTextExpression(layer.textExpression, calendar)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = parseColorSafe(layer.textColorHex, Color.WHITE)
                    textSize = layer.textSizePx
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                }
                // Respect alignV="center" (as used in Midnight+Blue.mtz fancy_icons/com.android.calendar/manifest.xml)
                val baselineY = if (layer.alignVCenter) {
                    layer.centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
                } else {
                    layer.centerY
                }
                canvas.drawText(textValue, layer.centerX, baselineY, textPaint)
            }
        }

        canvas.restore()
        return output
    }

    private fun evaluateMamlRotationExpression(expr: String?, calendar: Calendar): Float {
        if (expr.isNullOrBlank()) return 0f
        val hour12 = (calendar.get(Calendar.HOUR) % 12).toFloat()
        val minute = calendar.get(Calendar.MINUTE).toFloat()
        val second = calendar.get(Calendar.SECOND).toFloat()

        return when {
            expr.contains("#hour12") -> (hour12 * 30f) + (minute * 0.5f)
            expr.contains("#minute") -> (minute * 6f) + (second * 0.1f)
            expr.contains("#second") -> second * 6f
            else -> expr.toFloatOrNull() ?: 0f
        }
    }

    private fun evaluateMamlTextExpression(expr: String, calendar: Calendar): String {
        return when {
            expr.equals("dd", ignoreCase = true) -> {
                String.format(Locale.US, "%02d", calendar.get(Calendar.DAY_OF_MONTH))
            }
            expr.contains("#date") || expr.contains("#day") -> {
                calendar.get(Calendar.DAY_OF_MONTH).toString()
            }
            expr.contains("#month_short") -> {
                SimpleDateFormat("MMM", Locale.US).format(calendar.time).uppercase(Locale.US)
            }
            expr.contains("#dow") -> {
                SimpleDateFormat("EEE", Locale.US).format(calendar.time).uppercase(Locale.US)
            }
            else -> calendar.get(Calendar.DAY_OF_MONTH).toString()
        }
    }

    private fun findFancyIconSpec(
        packageName: String,
        activityName: String,
        theme: MtzThemeMetadata
    ): MamlFancyIconSpec? {
        theme.fancyIconSpecs[packageName]?.let { return it }
        theme.fancyIconSpecs[activityName]?.let { return it }
        val aliases = XIAOMI_PACKAGE_ALIASES[packageName].orEmpty()
        for (alias in aliases) {
            theme.fancyIconSpecs[alias]?.let { return it }
        }
        return null
    }

    private fun findDirectIconPath(
        packageName: String,
        activityName: String,
        theme: MtzThemeMetadata
    ): Pair<String, String>? {
        val candidates = mutableListOf(
            packageName,
            activityName,
            "$packageName.$activityName"
        )
        XIAOMI_PACKAGE_ALIASES[packageName]?.let { candidates.addAll(it) }

        for (candidate in candidates) {
            val path = theme.directIconsMap[candidate]
            if (path != null) {
                return candidate to path
            }
        }
        return null
    }

    private fun selectBgPath(
        packageName: String,
        theme: MtzThemeMetadata,
        enableMultiBgHash: Boolean
    ): String? {
        if (theme.iconBgPaths.isEmpty()) return null
        if (!enableMultiBgHash || theme.iconBgPaths.size == 1) {
            return theme.iconBgPaths.first()
        }
        val idx = abs(packageName.hashCode()) % theme.iconBgPaths.size
        return theme.iconBgPaths[idx]
    }

    private fun buildDiagnosticXmlSnippet(
        packageName: String,
        source: IconResolutionSource,
        theme: MtzThemeMetadata,
        effectiveScale: Float,
        enableColorFilter: Boolean,
        matchedEntry: String,
        fancySpec: MamlFancyIconSpec?
    ): String {
        if (source == IconResolutionSource.MTZ_FANCY_MAML && fancySpec != null) {
            return fancySpec.rawManifestXml
        }
        val inset = ((1f - effectiveScale) * 45f).toInt()
        val maxCoord = 90 - inset
        return """
            <!-- Xiaomi MIUI / HyperOS <IconTransform> Trace ($matchedEntry) -->
            <IconTransform package="$packageName" stage="${source.code}">
                <PointsMapping scale="$effectiveScale">
                    <Point fromX="0.0" fromY="0.0" toX="$inset.0" toY="$inset.0" />
                    <Point fromX="90.0" fromY="90.0" toX="$maxCoord.0" toY="$maxCoord.0" />
                </PointsMapping>
                <IconFilters enabled="$enableColorFilter">
                    <Filter name="GrayScale">
                        <Param name="BlackColor" value="${theme.filterBlackColorHex}" />
                    </Filter>
                    <Filter name="Levels">
                        <Param name="InputMin" value="${theme.filterLevelsInputMin}" />
                    </Filter>
                </IconFilters>
                <LayerComposite>
                    <Layer order="1" src="icon_background.png" fill="${theme.filterBlackColorHex}" />
                    <Layer order="2" src="filtered_glyph_normalized" scale="$effectiveScale" />
                    <Layer order="3" src="icon_mask.png" mode="DST_IN" />
                    <Layer order="4" src="icon_border.png" mode="SRC_OVER" />
                </LayerComposite>
            </IconTransform>
        """.trimIndent()
    }

    private fun decodeBitmapCached(path: String): Bitmap? {
        bitmapCache[path]?.let { return it }
        val file = File(path)
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, opts)?.let(::ensureSoftwareBitmap) ?: return null
        bitmapCache[path] = decoded
        return decoded
    }

    private fun scaleToTarget(bitmap: Bitmap): Bitmap {
        val soft = ensureSoftwareBitmap(bitmap)
        if (soft.width == TARGET_ICON_PX && soft.height == TARGET_ICON_PX) {
            return soft
        }
        return ensureSoftwareBitmap(Bitmap.createScaledBitmap(soft, TARGET_ICON_PX, TARGET_ICON_PX, true))
    }
}
