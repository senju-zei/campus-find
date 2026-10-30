package ph.edu.bulsu.campusfind.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.ItemRowBinding
import ph.edu.bulsu.campusfind.model.Item

/** Bordered item rows: picture, title, "category · location · time", status chip. */
class ItemAdapter(
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
            ).joinToString(" · ")
            ItemUi.bindStatus(binding.status, item.status)
            ItemUi.loadPicture(binding.picture, item, R.dimen.badge_size_row)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }
}
