package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.LauncherEngineConfig
import com.example.mtz.MtzArchiveParser
import com.example.mtz.MtzSampleGenerator
import com.example.mtz.XiaomiIconRenderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HyperMTZ", appName)
    }

    @Test
    fun `parse midnight blue and emerald mtz archives and verify icon transform pipeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = MtzSampleGenerator(context)
        val parser = MtzArchiveParser()
        val renderEngine = XiaomiIconRenderEngine()

        val archives = generator.ensureSampleMtzArchivesCreated()

        // 1. Verify Midnight Blue .mtz extraction & <IconTransform> parsing
        val midnightFile = archives["builtin_midnight_blue"]
        assertNotNull(midnightFile)
        assertTrue(midnightFile!!.exists() && midnightFile.length() > 0L)

        val extractMidnightDir = File(context.cacheDir, "test_extract_midnight")
        val midnightMeta = FileInputStream(midnightFile).use { fis ->
            parser.parseAndExtractMtzStream(
                inputStream = fis,
                themeId = "builtin_midnight_blue",
                archiveFileName = midnightFile.name,
                targetExtractDir = extractMidnightDir,
                isBuiltIn = true
            )
        }

        assertEquals("Midnight Blue", midnightMeta.title)
        assertEquals(0.7778f, midnightMeta.iconScale, 0.02f)
        assertEquals("#465967", midnightMeta.filterBlackColorHex)
        assertEquals(95, midnightMeta.filterLevelsInputMin)
        assertTrue(midnightMeta.iconBgPaths.isNotEmpty())
        assertTrue(midnightMeta.iconBgPaths.first().endsWith("icon_background.png"))
        assertNotNull(midnightMeta.iconMaskPath)
        assertNotNull(midnightMeta.iconBorderPath)
        assertTrue(midnightMeta.iconBorderPath!!.endsWith("icon_border.png"))
        assertTrue(midnightMeta.directIconsMap.containsKey("com.android.settings"))
        assertTrue(midnightMeta.fancyIconSpecs.containsKey("com.android.calendar"))

        // 2. Simulate an Android Adaptive 3rd-party icon (like Maps / Photos: outer white circle + colored glyph)
        val adaptiveRaw = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val rawCanvas = Canvas(adaptiveRaw)
        val whitePlatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        rawCanvas.drawCircle(64f, 64f, 60f, whitePlatePaint)
        val redGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(229, 57, 53)
            style = Paint.Style.FILL
        }
        rawCanvas.drawCircle(64f, 64f, 24f, redGlyphPaint)

        val config = LauncherEngineConfig(activeThemeId = "builtin_midnight_blue")
        val midnightMasked = renderEngine.resolveAppIcon(
            packageName = "com.google.android.apps.maps",
            activityName = "com.google.android.maps.MapsActivity",
            rawAppBitmap = adaptiveRaw,
            theme = midnightMeta,
            config = config,
            customOverrideAssetKey = null
        )

        assertEquals(IconResolutionSource.MTZ_DYNAMIC_MASK, midnightMasked.source)
        assertEquals(XiaomiIconRenderEngine.TARGET_ICON_PX, midnightMasked.bitmap.width)

        // Check that the circular inner plate pixel (x=80, y=32) is opaque #465967 (NOT transparent!)
        val platePixel = midnightMasked.bitmap.getPixel(80, 32)
        assertTrue("Inner circular plate must be opaque", Color.alpha(platePixel) > 240)
        assertEquals(70.0, Color.red(platePixel).toDouble(), 6.0)
        assertEquals(89.0, Color.green(platePixel).toDouble(), 6.0)
        assertEquals(103.0, Color.blue(platePixel).toDouble(), 6.0)

        // Check that the outer circular border ring pixel (x=80, y=18, r=62px on 160x160) is opaque #43515E
        val borderPixel = midnightMasked.bitmap.getPixel(80, 18)
        assertTrue("Outer circular border ring must be opaque", Color.alpha(borderPixel) > 240)
        assertEquals(67.0, Color.red(borderPixel).toDouble(), 6.0)
        assertEquals(81.0, Color.green(borderPixel).toDouble(), 6.0)
        assertEquals(94.0, Color.blue(borderPixel).toDouble(), 6.0)

        // Check that the center glyph pixel (x=80, y=80) is crisp off-white (#ECF0F1)
        val centerPixel = midnightMasked.bitmap.getPixel(80, 80)
        assertTrue(Color.red(centerPixel) > 215 && Color.green(centerPixel) > 215 && Color.blue(centerPixel) > 215)

        // 3. Verify CrimsonToWhite_Emerald.mtz (<IconTransform> with EmeraldShader)
        val emeraldFile = archives["builtin_red_white_emerald"]
        assertNotNull(emeraldFile)
        val extractEmeraldDir = File(context.cacheDir, "test_extract_emerald")
        val emeraldMeta = FileInputStream(emeraldFile!!).use { fis ->
            parser.parseAndExtractMtzStream(
                inputStream = fis,
                themeId = "builtin_red_white_emerald",
                archiveFileName = emeraldFile.name,
                targetExtractDir = extractEmeraldDir,
                isBuiltIn = true
            )
        }
        assertEquals("#254038", emeraldMeta.filterBlackColorHex)
        assertTrue(emeraldMeta.useEmeraldThirdPartyShader)
        assertTrue(emeraldMeta.iconBgPaths.isNotEmpty())
        assertTrue(emeraldMeta.iconBorderPath!!.endsWith("icon_border.png"))

        // Verify Red->White and Blue->Emerald in EmeraldShader mode
        val testThirdParty = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 8 until 56) {
            for (x in 8 until 32) {
                testThirdParty.setPixel(x, y, Color.rgb(229, 57, 53))
            }
            for (x in 32 until 56) {
                testThirdParty.setPixel(x, y, Color.rgb(30, 136, 229))
            }
        }
        val recolored = renderEngine.applyThemeIconTransformFilter(testThirdParty, emeraldMeta)
        val redRemappedPixel = recolored.getPixel(20, 32)
        val blueRemappedPixel = recolored.getPixel(44, 32)
        assertTrue(Color.red(redRemappedPixel) > 215 && Color.green(redRemappedPixel) > 215 && Color.blue(redRemappedPixel) > 215)
        assertTrue(Color.green(blueRemappedPixel) > Color.red(blueRemappedPixel) && Color.green(blueRemappedPixel) >= Color.blue(blueRemappedPixel))
    }
}
