package ph.edu.bulsu.campusfind.ui.staff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.FragmentReviewClaimBinding
import ph.edu.bulsu.campusfind.domain.TextRules
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.ui.common.PhotoStrip
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
        binding.approveButton.setOnClickListener { confirmApprove() }
        binding.rejectButton.setOnClickListener { reject() }

        collectWhenStarted(viewModel.photos) { photos ->
            binding.photosSection.visibleIf(photos.isNotEmpty())
            PhotoStrip.bind(binding.photosRow, photos)
        }
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
            val pendingCount = state.allClaims.count { it.status == ClaimStatus.PENDING }
            binding.claimant.text = claim?.let {
                val who = getString(R.string.label_claimed_by, it.claimantName, ItemUi.relativeTime(context, it.createdAt))
                if (pendingCount > 1) who + "\n" + getString(R.string.label_other_claims, pendingCount) else who
            }
            claim?.let { ItemUi.bindClaimStatus(binding.claimStatus, it.status) }
            binding.claimText.text = claim?.secretDetails ?: none
            binding.whenWhere.text = claim?.whenWhere?.takeIf { it.isNotBlank() } ?: none
            binding.contact.text = claim?.contactNote?.takeIf { it.isNotBlank() } ?: none
            // A claim already reviewed (here or on another phone) cannot be reviewed again, and
            // staff never review an item they claimed themselves (the rules refuse it too).
            val ownClaim = state.allClaims.any { it.claimant == viewModel.reviewerUid && it.status == ClaimStatus.PENDING }
            binding.actions.visibleIf(claim?.status == ClaimStatus.PENDING && !ownClaim)
            binding.ownClaimNote.visibleIf(ownClaim)
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

    /**
     * Approving sets the item Returned and rejects every other pending claim at once, so it asks
     * first, like the web dashboard.
     */
    private fun confirmApprove() {
        val state = viewModel.state.value
        val claim = state.claim ?: return
        val others = state.allClaims.count { it.status == ClaimStatus.PENDING && it.claimant != claim.claimant }
        val body = buildString {
            append(getString(R.string.dialog_approve_body, claim.claimantName))
            if (others > 0) append("\n\n").append(resources.getQuantityString(R.plurals.dialog_approve_others, others, others))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_approve_title)
            .setMessage(body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_approve_claim) { _, _ ->
                viewModel.approve(getString(R.string.reason_other_claim_approved))
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
