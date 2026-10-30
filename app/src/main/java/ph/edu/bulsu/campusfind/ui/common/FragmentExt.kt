package ph.edu.bulsu.campusfind.ui.common

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Collects [flow] while the view is at least STARTED. */
fun <T> Fragment.collectWhenStarted(flow: Flow<T>, action: suspend (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

fun Fragment.showMessage(message: UiMessage) {
    val text = when {
        message.count != null -> resources.getQuantityString(message.text, message.count, message.count)
        message.arg != null -> getString(message.text, message.arg)
        else -> getString(message.text)
    }
    view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
}

/** Goes back one screen and shows [message] there. */
fun Fragment.popWithMessage(message: UiMessage) {
    val nav = findNavController()
    nav.previousBackStackEntry?.savedStateHandle?.set(NavArgs.RESULT_MESSAGE, message)
    nav.popBackStack()
}

/** Shows a message handed back by the screen above this one (see [popWithMessage]). */
fun Fragment.showResultMessages() {
    val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
    handle.getLiveData<UiMessage>(NavArgs.RESULT_MESSAGE).observe(viewLifecycleOwner) { message ->
        if (message != null) {
            handle.remove<UiMessage>(NavArgs.RESULT_MESSAGE)
            showMessage(message)
        }
    }
}

fun TextInputLayout.text(): String = editText?.text?.toString().orEmpty()

/** Shows [errorRes] on the field, or clears it when null. Returns true if the field is valid. */
fun TextInputLayout.check(errorRes: Int?): Boolean {
    error = errorRes?.let { context.getString(it) }
    return errorRes == null
}

fun View.visibleIf(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}
