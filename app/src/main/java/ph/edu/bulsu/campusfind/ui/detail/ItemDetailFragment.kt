package ph.edu.bulsu.campusfind.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.FragmentItemDetailBinding
import ph.edu.bulsu.campusfind.model.Claim
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.ui.common.ItemUi
import ph.edu.bulsu.campusfind.ui.common.NavArgs
import ph.edu.bulsu.campusfind.ui.common.UiMessage
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.showMessage
import ph.edu.bulsu.campusfind.ui.common.showResultMessages
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
        binding.claimButton.setOnClickListener {
            val itemId = requireArguments().getString(NavArgs.ITEM_ID)
            findNavController().navigate(R.id.action_detail_to_claim, bundleOf(NavArgs.ITEM_ID to itemId))
        }
        binding.closeReportButton.setOnClickListener { viewModel.closeReport() }
        binding.markTurnedInButton.setOnClickListener { viewModel.markTurnedIn() }
        binding.changeStatusButton.setOnClickListener { showChangeStatus() }

        collectWhenStarted(viewModel.state) { state ->
            when (state) {
                DetailState.Loading -> Unit
                DetailState.Missing -> {
                    showMessage(UiMessage(R.string.error_generic))
                    findNavController().popBackStack()
                }
                is DetailState.Shown -> {
                    bindItem(state.item)
                    bindMyClaim(state.myClaim)
                    // Staff checking a pickup: who it was approved for and their code.
                    state.approvedForStaff?.let { approved ->
                        binding.claimBox.visibleIf(true)
                        ItemUi.bindClaimStatus(binding.claimStatusChip, approved.status)
                        binding.claimNote.text = getString(
                            R.string.label_staff_pickup, approved.claimantName, approved.pickupCode.orEmpty(),
                        )
                        binding.claimReason.visibleIf(false)
                    }
                    binding.claimButton.visibleIf(state.canClaim)
                    binding.claimButton.setText(
                        if (state.myClaim?.status == ClaimStatus.REJECTED) R.string.action_claim_again else R.string.action_claim_item,
                    )
                    binding.closeReportButton.visibleIf(state.canClose)
                    binding.markTurnedInButton.visibleIf(state.canMarkTurnedIn)
                    binding.changeStatusButton.visibleIf(state.canOverride)
                }
            }
        }
        collectWhenStarted(viewModel.busy) { busy ->
            binding.closeReportButton.isEnabled = !busy
            binding.markTurnedInButton.isEnabled = !busy
            binding.changeStatusButton.isEnabled = !busy
        }
        collectWhenStarted(viewModel.messages) { showMessage(it) }
        // The Claim screen hands back its confirmation here.
        showResultMessages()
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

    /** Pending: under review. Approved: pick-up instructions. Rejected: the reason. */
    private fun bindMyClaim(claim: Claim?) {
        binding.claimBox.visibleIf(claim != null)
        if (claim == null) return
        ItemUi.bindClaimStatus(binding.claimStatusChip, claim.status)
        binding.claimNote.setText(
            when (claim.status) {
                ClaimStatus.PENDING -> R.string.claim_pending_note
                ClaimStatus.APPROVED -> R.string.label_pickup
                ClaimStatus.REJECTED -> R.string.claim_rejected_note
            },
        )
        val extra = when (claim.status) {
            ClaimStatus.REJECTED -> claim.rejectReason?.let { getString(R.string.label_reject_reason, it) }
            ClaimStatus.APPROVED -> claim.pickupCode?.let { getString(R.string.label_pickup_code, it) }
            ClaimStatus.PENDING -> null
        }
        binding.claimReason.visibleIf(extra != null)
        binding.claimReason.text = extra
    }

    /** Faculty override: pick any of the five statuses. */
    private fun showChangeStatus() {
        val statuses = ItemStatus.entries
        val labels = statuses.map { status ->
            getString(
                when (status) {
                    ItemStatus.OPEN -> R.string.status_open
                    ItemStatus.TURNED_IN -> R.string.status_turned_in
                    ItemStatus.CLAIM_PENDING -> R.string.status_claim_pending
                    ItemStatus.RETURNED -> R.string.status_returned
                    ItemStatus.CLOSED -> R.string.status_closed
                },
            )
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_change_status_title)
            .setItems(labels) { _, which -> viewModel.overrideStatus(statuses[which]) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
