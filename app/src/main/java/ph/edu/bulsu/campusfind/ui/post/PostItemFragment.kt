package ph.edu.bulsu.campusfind.ui.post

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.annotation.ArrayRes
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.Constants
import ph.edu.bulsu.campusfind.databinding.FragmentPostItemBinding
import ph.edu.bulsu.campusfind.domain.Isbn
import ph.edu.bulsu.campusfind.domain.TextRules
import ph.edu.bulsu.campusfind.model.ItemDraft
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.ui.common.ItemUi
import ph.edu.bulsu.campusfind.ui.common.NavArgs
import ph.edu.bulsu.campusfind.ui.common.UiMessage
import ph.edu.bulsu.campusfind.ui.common.check
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.popWithMessage
import ph.edu.bulsu.campusfind.ui.common.showMessage
import ph.edu.bulsu.campusfind.ui.common.text
import ph.edu.bulsu.campusfind.ui.common.visibleIf

class PostItemFragment : Fragment() {

    private var _binding: FragmentPostItemBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PostItemViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPostItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            val start = arguments?.getString(NavArgs.TYPE)
            binding.typeGroup.check(if (start == ItemType.LOST.name) R.id.typeLost else R.id.typeFound)
        }
        binding.typeGroup.addOnButtonCheckedListener { _, _, _ -> updateSections() }
        binding.categoryInput.doAfterTextChanged {
            binding.categoryLayout.error = null
            updateSections()
        }
        binding.locationInput.doAfterTextChanged { binding.locationLayout.error = null }
        clearErrorOnEdit(
            binding.titleLayout, binding.roomLayout, binding.descriptionLayout,
            binding.hiddenLayout, binding.isbnLayout, binding.authorsLayout, binding.brandLayout,
        )
        binding.isbnInput.doAfterTextChanged { viewModel.onIsbnChanged(it?.toString().orEmpty()) }
        binding.isbnInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                lookUp()
                true
            } else {
                false
            }
        }
        binding.lookUpButton.setOnClickListener { lookUp() }
        binding.postButton.setOnClickListener { submit() }
        setUpColors()
        updateSections()

        collectWhenStarted(viewModel.state) { state ->
            val busy = state.posting || state.lookingUp
            binding.progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
            binding.postButton.isEnabled = !busy
            binding.lookUpButton.isEnabled = !busy
            binding.coverPreview.visibleIf(state.coverUrl != null)
            if (state.coverUrl != null) {
                binding.coverPreview.load(state.coverUrl) {
                    crossfade(true)
                    listener(onError = { _, _ -> binding.coverPreview.visibleIf(false) })
                }
            }
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is PostEvent.BookFound -> {
                    binding.titleInput.setText(event.title.take(Constants.TITLE_MAX))
                    binding.authorsInput.setText(event.authors.orEmpty().take(Constants.BOOK_AUTHORS_MAX))
                    showMessage(UiMessage(R.string.book_filled))
                }
                is PostEvent.Show -> if (event.offerRetry) {
                    Snackbar.make(binding.root, event.message.text, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_retry) { lookUp() }
                        .show()
                } else {
                    showMessage(event.message)
                }
                PostEvent.Posted -> popWithMessage(UiMessage(R.string.msg_item_posted))
            }
        }
    }

    private fun selectedType(): ItemType =
        if (binding.typeGroup.checkedButtonId == R.id.typeLost) ItemType.LOST else ItemType.FOUND

    /** Hidden detail for FOUND only; book fields for the Book category only. */
    private fun updateSections() {
        binding.hiddenLayout.visibleIf(selectedType() == ItemType.FOUND)
        val isBook = storedValue(binding.categoryLayout, R.array.categories, Constants.CATEGORIES) ==
            Constants.CATEGORY_BOOK
        binding.bookSection.visibleIf(isBook)
    }

    private fun lookUp() {
        val raw = binding.isbnLayout.text()
        // Checksum first: an invalid ISBN never reaches the network.
        if (!binding.isbnLayout.check(if (Isbn.isValid(raw)) null else R.string.error_isbn_invalid)) return
        viewModel.lookUp(Isbn.normalize(raw))
    }

    private fun submit() {
        val type = selectedType()
        val category = storedValue(binding.categoryLayout, R.array.categories, Constants.CATEGORIES)
        val location = storedValue(binding.locationLayout, R.array.locations, Constants.LOCATIONS)
        val isBook = category == Constants.CATEGORY_BOOK
        val isbn = if (isBook) binding.isbnLayout.text().trim() else ""

        val checks = listOf(
            binding.titleLayout.check(
                if (TextRules.isValidTitle(binding.titleLayout.text())) null else R.string.error_title_length,
            ),
            binding.categoryLayout.check(if (category == null) R.string.error_select_option else null),
            binding.locationLayout.check(if (location == null) R.string.error_select_option else null),
            binding.roomLayout.check(
                if (TextRules.fitsMax(binding.roomLayout.text(), Constants.SHORT_FIELD_MAX)) null else R.string.error_room_length,
            ),
            binding.descriptionLayout.check(
                if (TextRules.fitsMax(binding.descriptionLayout.text(), Constants.PUBLIC_DESCRIPTION_MAX)) {
                    null
                } else {
                    R.string.error_description_length
                },
            ),
            binding.hiddenLayout.check(
                if (type == ItemType.LOST || TextRules.isValidHiddenDetail(binding.hiddenLayout.text())) {
                    null
                } else {
                    R.string.error_hidden_detail_length
                },
            ),
            binding.isbnLayout.check(if (isbn.isEmpty() || Isbn.isValid(isbn)) null else R.string.error_isbn_invalid),
            binding.authorsLayout.check(
                if (TextRules.fitsMax(binding.authorsLayout.text(), Constants.BOOK_AUTHORS_MAX)) null else R.string.error_optional_long,
            ),
            binding.brandLayout.check(
                if (TextRules.fitsMax(binding.brandLayout.text(), Constants.BRAND_MAX)) null else R.string.error_room_length,
            ),
        )
        if (checks.any { !it }) return

        viewModel.post(
            ItemDraft(
                type = type,
                title = binding.titleLayout.text(),
                category = category,
                location = location,
                room = binding.roomLayout.text(),
                publicDescription = binding.descriptionLayout.text(),
                hiddenDetail = if (type == ItemType.FOUND) binding.hiddenLayout.text() else null,
                isbn = isbn.ifEmpty { null },
                bookAuthors = if (isBook) binding.authorsLayout.text() else null,
                coverUrl = null, // The view model adds the looked-up cover.
                color = viewModel.color,
                brand = binding.brandLayout.text(),
            ),
        )
    }

    /** Optional color chips; the chosen color tints the item's category icon everywhere. */
    private fun setUpColors() {
        val context = requireContext()
        val labels = resources.getStringArray(R.array.item_colors)
        Constants.COLORS.forEachIndexed { index, key ->
            val chip = Chip(context).apply {
                text = labels[index]
                isCheckable = true
                isCheckedIconVisible = false
                chipIcon = ContextCompat.getDrawable(context, R.drawable.dot_color)?.mutate()
                chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, ItemUi.colorRes(key)))
                isChipIconVisible = key != Constants.COLOR_OTHER
                shapeAppearanceModel = shapeAppearanceModel.withCornerSize(resources.getDimension(R.dimen.radius_control))
                chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.toggle_background)
                chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.border))
                chipStrokeWidth = resources.getDimension(R.dimen.border_width)
                setTextColor(ContextCompat.getColor(context, R.color.text))
                isChecked = key == viewModel.color
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        viewModel.color = key
                    } else if (viewModel.color == key) {
                        viewModel.color = null
                    }
                }
            }
            binding.colorGroup.addView(chip)
        }
    }

    /** Maps the chosen dropdown label back to the value stored in the database. */
    private fun storedValue(layout: TextInputLayout, @ArrayRes labelsRes: Int, values: List<String>): String? {
        val index = resources.getStringArray(labelsRes).indexOf(layout.text())
        return values.getOrNull(index)
    }

    private fun clearErrorOnEdit(vararg layouts: TextInputLayout) {
        layouts.forEach { layout -> layout.editText?.doAfterTextChanged { layout.error = null } }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
