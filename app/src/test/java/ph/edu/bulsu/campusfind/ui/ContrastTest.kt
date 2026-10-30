package ph.edu.bulsu.campusfind.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** T1-7: WCAG AA (4.5:1) for every text pair in section 7, in light and dark. */
class ContrastTest {

    private val pairs = listOf(
        "text" to "background",
        "text" to "surface",
        "text_muted" to "background",
        "text_muted" to "surface",
        "accent" to "surface",
        "accent" to "background",
        "on_accent" to "accent",
        "accent" to "accent_tint",
        "chip_neutral_text" to "chip_neutral_bg",
        "chip_pending_text" to "chip_pending_bg",
        "chip_success_text" to "chip_success_bg",
        "chip_error_text" to "chip_error_bg",
    )

    @Test fun `T1-7 light and dark pairs are at least 4,5 to 1`() {
        val failures = mutableListOf<String>()
        for (dir in listOf("values", "values-night")) {
            val colors = ResourceFiles.colors(dir)
            for ((fg, bg) in pairs) {
                val ratio = ratio(resolve(colors, fg), resolve(colors, bg))
                println("$dir: $fg on $bg = ${"%.2f".format(ratio)}")
                if (ratio < 4.5) failures += "$dir: $fg on $bg is ${"%.2f".format(ratio)}"
            }
        }
        assertTrue("Below 4.5:1: $failures", failures.isEmpty())
    }

    private fun resolve(colors: Map<String, String>, name: String): Int {
        var value = colors[name] ?: error("Missing color $name")
        while (value.startsWith("@color/")) value = colors[value.removePrefix("@color/")] ?: error("Missing $value")
        return value.removePrefix("#").takeLast(6).toInt(16)
    }

    private fun luminance(rgb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(rgb shr 16 and 0xFF) + 0.7152 * channel(rgb shr 8 and 0xFF) + 0.0722 * channel(rgb and 0xFF)
    }

    private fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
