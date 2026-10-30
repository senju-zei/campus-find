package ph.edu.bulsu.campusfind.ui.staff

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.StaffClaimRowBinding
import ph.edu.bulsu.campusfind.ui.common.ItemUi

class StaffClaimAdapter(
    private val onReview: (StaffClaimRow) -> Unit,
) : ListAdapter<StaffClaimRow, StaffClaimAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(StaffClaimRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: StaffClaimRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: StaffClaimRow) {
            val context = binding.root.context
            val none = context.getString(R.string.detail_value_none)
            binding.title.text = row.item?.title ?: none
            binding.meta.text = context.getString(
                R.string.label_claimed_by,
                row.claim.claimantName,
                ItemUi.relativeTime(context, row.claim.createdAt),
            )
            row.item?.let { ItemUi.loadPicture(binding.picture, it, R.dimen.badge_size_row) }
            binding.hiddenDetail.text = row.hiddenDetail ?: none
            binding.claimText.text = row.claim.secretDetails
            binding.root.setOnClickListener { onReview(row) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<StaffClaimRow>() {
        override fun areItemsTheSame(oldItem: StaffClaimRow, newItem: StaffClaimRow) =
            oldItem.claim.itemId == newItem.claim.itemId && oldItem.claim.claimant == newItem.claim.claimant
        override fun areContentsTheSame(oldItem: StaffClaimRow, newItem: StaffClaimRow) = oldItem == newItem
    }
}
