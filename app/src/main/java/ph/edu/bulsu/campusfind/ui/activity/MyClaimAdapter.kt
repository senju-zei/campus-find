package ph.edu.bulsu.campusfind.ui.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.ClaimRowBinding
import ph.edu.bulsu.campusfind.domain.StatusTransitions
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.ui.common.ItemUi
import ph.edu.bulsu.campusfind.ui.common.Selection
import ph.edu.bulsu.campusfind.ui.common.visibleIf

/** My claims: status chip, then the next step (wait, pick up, or the reason plus "Claim again"). */
class MyClaimAdapter(
    private val selection: Selection,
    private val onOpen: (MyClaimRow) -> Unit,
    private val onClaimAgain: (MyClaimRow) -> Unit,
    /** Long-press selection, to remove finished claims from the Profile. */
    private val onSelect: (MyClaimRow) -> Unit,
) : ListAdapter<MyClaimRow, MyClaimAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ClaimRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ClaimRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: MyClaimRow) {
            val context = binding.root.context
            val claim = row.claim
            val item = row.item
            binding.title.text = item?.title ?: context.getString(R.string.detail_value_none)
            binding.meta.text = ItemUi.relativeTime(context, claim.createdAt)
            if (item != null) ItemUi.loadPicture(binding.picture, item, R.dimen.badge_size_row)
            ItemUi.bindClaimStatus(binding.status, claim.status)

            binding.note.setText(
                when (claim.status) {
                    ClaimStatus.PENDING -> R.string.claim_pending_note
                    ClaimStatus.APPROVED -> R.string.label_pickup
                    ClaimStatus.REJECTED -> R.string.claim_rejected_note
                },
            )
            val extra = when (claim.status) {
                ClaimStatus.REJECTED -> claim.rejectReason?.let { context.getString(R.string.label_reject_reason, it) }
                ClaimStatus.APPROVED -> claim.pickupCode?.let { context.getString(R.string.label_pickup_code, it) }
                ClaimStatus.PENDING -> null
            }
            binding.reason.visibleIf(extra != null)
            binding.reason.text = extra

            // "Claim again" only when the item can be claimed again (back to OPEN or TURNED_IN).
            val canClaimAgain = claim.status == ClaimStatus.REJECTED && item != null &&
                StatusTransitions.canClaim(item.status, item.type, isPoster = false)
            binding.claimAgainButton.visibleIf(canClaimAgain)
            binding.claimAgainButton.setOnClickListener { onClaimAgain(row) }
            val selected = claim.itemId in selection
            binding.root.setBackgroundResource(if (selected) R.drawable.bg_row_selected else R.drawable.bg_row)
            binding.root.setOnClickListener { if (selection.isActive) onSelect(row) else onOpen(row) }
            binding.root.setOnLongClickListener {
                onSelect(row)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MyClaimRow>() {
        override fun areItemsTheSame(oldItem: MyClaimRow, newItem: MyClaimRow) =
            oldItem.claim.itemId == newItem.claim.itemId
        override fun areContentsTheSame(oldItem: MyClaimRow, newItem: MyClaimRow) = oldItem == newItem
    }
}
