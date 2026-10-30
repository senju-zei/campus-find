package ph.edu.bulsu.campusfind.ui.common

import androidx.annotation.StringRes
import com.google.android.material.tabs.TabLayout
import ph.edu.bulsu.campusfind.R

/** Tab labels with a count, e.g. "Found (12)". A zero count shows the plain label. */
object TabCounts {
    fun set(tab: TabLayout.Tab?, @StringRes label: Int, count: Int) {
        val view = tab?.view ?: return
        val text = view.context.getString(label)
        tab.text = if (count > 0) view.context.getString(R.string.tab_with_count, text, count) else text
    }
}
