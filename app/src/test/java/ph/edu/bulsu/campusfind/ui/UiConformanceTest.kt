package ph.edu.bulsu.campusfind.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** T1-8: the section 7 rules that can be checked by reading files. */
class UiConformanceTest {

    @Test fun `strings have no exclamation marks, emoji or filler`() {
        val bad = ResourceFiles.strings().filter { (_, text) ->
            '!' in text ||
                text.codePoints().anyMatch { it >= 0x1F000 || it in 0x2600..0x27BF } ||
                Regex("\\b(oops|awesome)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
        assertTrue("Strings break the copy rules: $bad", bad.isEmpty())
    }

    @Test fun `layouts use no FAB, bottom navigation or cards`() {
        val banned = listOf("FloatingActionButton", "BottomNavigationView", "MaterialCardView")
        val bad = ResourceFiles.layouts().flatMap { file -> banned.filter { it in file.readText() }.map { "${file.name}: $it" } }
        assertTrue("Banned widgets: $bad", bad.isEmpty())
    }

    @Test fun `no elevation above 0`() {
        val elevation = Regex("elevation\"?\\s*[=>]\\s*\"?(\\d+(\\.\\d+)?)dp")
        val files = ResourceFiles.layouts() + ResourceFiles.valuesXml()
        val bad = files.flatMap { file ->
            elevation.findAll(file.readText()).filter { it.groupValues[1].toDouble() > 0 }.map { "${file.name}: ${it.value}" }.toList()
        }
        assertTrue("Elevation above 0: $bad", bad.isEmpty())
    }

    @Test fun `layouts have no hardcoded colors`() {
        val hex = Regex("\"#[0-9A-Fa-f]{3,8}\"")
        val bad = ResourceFiles.layouts().filter { hex.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("Hardcoded colors in: $bad", bad.isEmpty())
    }

    @Test fun `text sizes are only 12, 14, 16 and 20sp`() {
        val sp = Regex("(\\d+(\\.\\d+)?)sp")
        val allowed = setOf(12.0, 14.0, 16.0, 20.0)
        val files = ResourceFiles.layouts() + ResourceFiles.valuesXml()
        val bad = files.flatMap { file ->
            sp.findAll(file.readText()).filter { it.groupValues[1].toDouble() !in allowed }.map { "${file.name}: ${it.value}" }.toList()
        }
        assertTrue("Text sizes outside the scale: $bad", bad.isEmpty())
    }

    @Test fun `no all caps text`() {
        val files = ResourceFiles.layouts() + ResourceFiles.valuesXml()
        val bad = files.filter { Regex("textAllCaps\"?\\s*[=>]\\s*\"?true").containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("textAllCaps true in: $bad", bad.isEmpty())
    }

    @Test fun `touch target size is at least 48dp`() {
        val dimens = File(ResourceFiles.res, "values/dimens.xml").readText()
        val value = Regex("name=\"touch_target\">(\\d+)dp").find(dimens)?.groupValues?.get(1)?.toInt()
        assertTrue("touch_target is $value", value != null && value >= 48)
        // Buttons and rows take their minimum height from it.
        val styles = File(ResourceFiles.res, "values/styles.xml").readText()
        val minHeights = Regex("android:minHeight\">([^<]+)<").findAll(styles).map { it.groupValues[1] }.toList()
        assertTrue("Every style minHeight uses touch_target: $minHeights", minHeights.all { it == "@dimen/touch_target" })
    }

    @Test fun `light and dark color files define the same names`() {
        assertEquals(ResourceFiles.colors("values").keys, ResourceFiles.colors("values-night").keys)
    }

    @Test fun `DynamicColors is not used`() {
        val bad = ResourceFiles.java.walkTopDown().filter { it.isFile && "DynamicColors" in it.readText() }.map { it.name }.toList()
        assertTrue("DynamicColors referenced in: $bad", bad.isEmpty())
    }
}
