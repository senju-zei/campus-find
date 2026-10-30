package ph.edu.bulsu.campusfind.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.ItemRowBinding
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus

/** Bordered item rows: picture, title, "category · location · time", status chip. */
class ItemAdapter(
    /** Staff Settled tab only: long-press selection. */
    private val selection: Selection? = null,
    private val onSelect: ((Item) -> Unit)? = null,
    private val onClick: (Item) -> Unit,
) : ListAdapter<Item, ItemAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            val context = binding.root.context
            binding.title.text = item.title
            binding.meta.text = listOfNotNull(
                ItemUi.categoryLabel(context, item.category),
                ItemUi.locationLabel(context, item.location),
                item.createdAt.takeIf { it > 0 }?.let { ItemUi.relativeTime(context, it) },
                item.heldAt?.takeIf { item.status == ItemStatus.OPEN }
                    ?.let { context.getString(R.string.label_now_at, it) },
            ).joinToString(" · ")
            ItemUi.bindStatus(binding.status, item.status)
            ItemUi.loadPicture(binding.picture, item, R.dimen.badge_size_row)
            val selected = selection?.contains(item.id) == true
            binding.root.setBackgroundResource(if (selected) R.drawable.bg_row_selected else R.drawable.bg_row)
            binding.root.setOnClickListener {
                if (selection?.isActive == true) onSelect?.invoke(item) else onClick(item)
            }
            binding.root.setOnLongClickListener {
                onSelect?.invoke(item)
                onSelect != null
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }
}
