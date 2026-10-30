package ph.edu.bulsu.campusfind.ui.staff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.FragmentReviewClaimBinding
import ph.edu.bulsu.campusfind.domain.TextRules
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.ui.common.ItemUi
import ph.edu.bulsu.campusfind.ui.common.check
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.popWithMessage
import ph.edu.bulsu.campusfind.ui.common.showMessage
import ph.edu.bulsu.campusfind.ui.common.text
import ph.edu.bulsu.campusfind.ui.common.visibleIf

class ReviewClaimFragment : Fragment() {

    private var _binding: FragmentReviewClaimBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReviewClaimViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReviewClaimBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.reasonInput.doAfterTextChanged { binding.reasonLayout.error = null }
        binding.approveButton.setOnClickListener { viewModel.approve() }
        binding.rejectButton.setOnClickListener { reject() }

        collectWhenStarted(viewModel.state) { state ->
            val context = requireContext()
            val none = getString(R.string.detail_value_none)
            val item = state.item
            val claim = state.claim
            binding.itemTitle.text = item?.title ?: none
            if (item != null) {
                ItemUi.loadPicture(binding.picture, item, R.dimen.badge_size_row)
                ItemUi.bindStatus(binding.itemStatus, item.status)
            }
            binding.hiddenDetail.text = state.hiddenDetail ?: none
            binding.claimant.text = claim?.let {
                getString(R.string.label_claimed_by, it.claimantName, ItemUi.relativeTime(context, it.createdAt))
            }
            claim?.let { ItemUi.bindClaimStatus(binding.claimStatus, it.status) }
            binding.claimText.text = claim?.secretDetails ?: none
            binding.whenWhere.text = claim?.whenWhere?.takeIf { it.isNotBlank() } ?: none
            binding.contact.text = claim?.contactNote?.takeIf { it.isNotBlank() } ?: none
            // A claim already reviewed (here or on another phone) cannot be reviewed again.
            binding.actions.visibleIf(claim?.status == ClaimStatus.PENDING)
        }
        collectWhenStarted(viewModel.busy) { busy ->
            binding.approveButton.isEnabled = !busy
            binding.rejectButton.isEnabled = !busy
            binding.progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is ReviewEvent.Done -> popWithMessage(event.message)
                is ReviewEvent.Show -> showMessage(event.message)
            }
        }
    }

    /** First tap opens the reason field; the next tap rejects once a reason is entered. */
    private fun reject() {
        if (binding.reasonLayout.visibility != View.VISIBLE) {
            binding.reasonLayout.visibleIf(true)
            binding.reasonInput.requestFocus()
            return
        }
        val reason = binding.reasonLayout.text()
        if (!binding.reasonLayout.check(if (TextRules.isValidRejectReason(reason)) null else R.string.error_reject_reason)) return
        viewModel.reject(reason)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
