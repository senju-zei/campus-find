package ph.edu.bulsu.campusfind.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.FragmentItemDetailBinding
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.ui.common.ItemUi
import ph.edu.bulsu.campusfind.ui.common.UiMessage
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.showMessage
import ph.edu.bulsu.campusfind.ui.common.visibleIf

class ItemDetailFragment : Fragment() {

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ItemDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.closeReportButton.setOnClickListener { viewModel.closeReport() }

        collectWhenStarted(viewModel.state) { state ->
            when (state) {
                DetailState.Loading -> Unit
                DetailState.Missing -> {
                    showMessage(UiMessage(R.string.error_generic))
                    findNavController().popBackStack()
                }
                is DetailState.Shown -> {
                    bindItem(state.item)
                    // Claim screen arrives in step 6; the button stays hidden until then.
                    binding.claimButton.visibleIf(false)
                    binding.closeReportButton.visibleIf(state.canClose)
                }
            }
        }
        collectWhenStarted(viewModel.busy) { busy -> binding.closeReportButton.isEnabled = !busy }
        collectWhenStarted(viewModel.messages) { showMessage(it) }
    }

    private fun bindItem(item: Item) {
        val context = requireContext()
        val none = getString(R.string.detail_value_none)
        binding.title.text = item.title
        ItemUi.bindType(binding.typeChip, item.type)
        ItemUi.bindStatus(binding.statusChip, item.status)
        ItemUi.loadPicture(binding.picture, item, R.dimen.badge_size_detail)

        binding.postedBy.text = listOf(
            getString(R.string.label_posted_by, item.postedByName),
            ItemUi.relativeTime(context, item.createdAt),
        ).joinToString(" · ")

        binding.category.text = ItemUi.categoryLabel(context, item.category) ?: none
        val place = ItemUi.locationLabel(context, item.location)
        binding.location.text = when {
            place != null && !item.room.isNullOrBlank() -> getString(R.string.label_location_room, place, item.room)
            place != null -> place
            else -> item.room ?: none
        }
        binding.description.text = item.publicDescription?.takeIf { it.isNotBlank() } ?: none

        val isBook = item.isbn != null || item.bookAuthors != null
        binding.bookFields.visibleIf(isBook)
        binding.authors.text = item.bookAuthors ?: none
        binding.isbn.text = item.isbn ?: none
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
