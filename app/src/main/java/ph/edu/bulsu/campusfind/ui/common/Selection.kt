package ph.edu.bulsu.campusfind.ui.common

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import ph.edu.bulsu.campusfind.R

/** Keys of the rows picked in long-press selection mode. */
class Selection(private val onChanged: () -> Unit) {
    private val keys = linkedSetOf<String>()

    val size: Int get() = keys.size
    val isActive: Boolean get() = keys.isNotEmpty()

    operator fun contains(key: String) = key in keys

    fun toggle(key: String) {
        if (!keys.remove(key)) keys.add(key)
        onChanged()
    }

    fun clear() {
        if (keys.isEmpty()) return
        keys.clear()
        onChanged()
    }

    fun snapshot(): List<String> = keys.toList()
}

/**
 * Email-style selection bar: "3 selected" with a Delete icon over the top bar. Back or the arrow
 * ends it and clears the selection.
 */
class SelectionBar(
    private val fragment: Fragment,
    private val selection: Selection,
    private val onDelete: () -> Unit,
) {
    private var mode: ActionMode? = null

    private val callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != R.id.action_delete_selected) return false
            onDelete()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            this@SelectionBar.mode = null
            selection.clear()
        }
    }

    /** Call after every selection change. */
    fun update() {
        if (!selection.isActive) {
            mode?.finish()
            return
        }
        val current = mode ?: (fragment.requireActivity() as AppCompatActivity)
            .startSupportActionMode(callback).also { mode = it }
        current?.title = fragment.resources.getQuantityString(R.plurals.selection_count, selection.size, selection.size)
    }

    fun finish() {
        mode?.finish()
    }
}
