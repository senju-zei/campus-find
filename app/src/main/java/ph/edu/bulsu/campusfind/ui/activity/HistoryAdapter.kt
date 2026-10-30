package ph.edu.bulsu.campusfind.ui.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.ClaimRowBinding
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.ui.common.ItemUi
import ph.edu.bulsu.campusfind.ui.common.Selection
import ph.edu.bulsu.campusfind.ui.common.visibleIf

/** Staff History: claims this staff member approved or rejected, newest first. */
class HistoryAdapter(
    private val selection: Selection,
    private val onOpen: (HistoryRow) -> Unit,
    private val onSelect: (HistoryRow) -> Unit,
) : ListAdapter<HistoryRow, HistoryAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ClaimRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ClaimRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: HistoryRow) {
            val context = binding.root.context
            val claim = row.claim
            binding.title.text = row.item?.title ?: context.getString(R.string.detail_value_none)
            binding.meta.text = ItemUi.relativeTime(context, claim.createdAt)
            row.item?.let { ItemUi.loadPicture(binding.picture, it, R.dimen.badge_size_row) }
            ItemUi.bindClaimStatus(binding.status, claim.status)
            binding.note.text = if (claim.status == ClaimStatus.APPROVED) {
                // Claims approved before pickup codes existed have none.
                claim.pickupCode?.let { context.getString(R.string.label_history_code, claim.claimantName, it) }
                    ?: context.getString(R.string.label_history_approved, claim.claimantName)
            } else {
                context.getString(R.string.label_history_reason, claim.claimantName, claim.rejectReason.orEmpty())
            }
            binding.reason.visibleIf(false)
            binding.claimAgainButton.visibleIf(false)

            val selected = row.key in selection
            binding.root.setBackgroundResource(if (selected) R.drawable.bg_row_selected else R.drawable.bg_row)
            binding.root.setOnClickListener { if (selection.isActive) onSelect(row) else onOpen(row) }
            binding.root.setOnLongClickListener {
                onSelect(row)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HistoryRow>() {
        override fun areItemsTheSame(oldItem: HistoryRow, newItem: HistoryRow) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: HistoryRow, newItem: HistoryRow) = oldItem == newItem
    }
}
