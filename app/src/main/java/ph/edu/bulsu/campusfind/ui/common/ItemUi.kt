package ph.edu.bulsu.campusfind.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import coil.dispose
import coil.load
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.Constants
import ph.edu.bulsu.campusfind.domain.BadgeUrlBuilder
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.model.Item
import ph.edu.bulsu.campusfind.model.ItemStatus
import ph.edu.bulsu.campusfind.model.ItemType

/** Chips, labels and pictures shared by Feed, Item detail and later screens. */
object ItemUi {

    private data class ChipStyle(
        @param:StringRes val text: Int,
        @param:DrawableRes val background: Int,
        @param:ColorRes val textColor: Int,
    )

    /** Status chip. The label always names the status, so color is never the only signal. */
    fun bindStatus(view: TextView, status: ItemStatus) = view.applyChip(
        when (status) {
            ItemStatus.OPEN -> ChipStyle(R.string.status_open, R.drawable.bg_chip_neutral, R.color.chip_neutral_text)
            ItemStatus.CLOSED -> ChipStyle(R.string.status_closed, R.drawable.bg_chip_neutral, R.color.chip_neutral_text)
            ItemStatus.TURNED_IN -> ChipStyle(R.string.status_turned_in, R.drawable.bg_chip_pending, R.color.chip_pending_text)
            ItemStatus.CLAIM_PENDING -> ChipStyle(R.string.status_claim_pending, R.drawable.bg_chip_pending, R.color.chip_pending_text)
            ItemStatus.RETURNED -> ChipStyle(R.string.status_returned, R.drawable.bg_chip_success, R.color.chip_success_text)
        },
    )

    fun bindClaimStatus(view: TextView, status: ClaimStatus) = view.applyChip(
        when (status) {
            ClaimStatus.PENDING -> ChipStyle(R.string.claim_status_pending, R.drawable.bg_chip_pending, R.color.chip_pending_text)
            ClaimStatus.APPROVED -> ChipStyle(R.string.claim_status_approved, R.drawable.bg_chip_success, R.color.chip_success_text)
            ClaimStatus.REJECTED -> ChipStyle(R.string.claim_status_rejected, R.drawable.bg_chip_error, R.color.chip_error_text)
        },
    )

    fun bindType(view: TextView, type: ItemType) = view.applyChip(
        ChipStyle(
            if (type == ItemType.FOUND) R.string.type_found else R.string.type_lost,
            R.drawable.bg_chip_type,
            R.color.accent,
        ),
    )

    private fun TextView.applyChip(style: ChipStyle) {
        setText(style.text)
        setBackgroundResource(style.background)
        setTextColor(ContextCompat.getColor(context, style.textColor))
    }

    /**
     * "5 minutes ago", or "Just now" within a minute either way: the server timestamp can be
     * slightly ahead of the phone clock, which would otherwise read "In 0 minutes".
     */
    fun relativeTime(context: Context, millis: Long): String {
        val now = System.currentTimeMillis()
        if (kotlin.math.abs(now - millis) < DateUtils.MINUTE_IN_MILLIS) return context.getString(R.string.label_just_now)
        return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }

    /** Display label for a stored category, from R.array.categories. */
    fun categoryLabel(context: Context, stored: String?): String? =
        label(context, stored, Constants.CATEGORIES, R.array.categories)

    /** Display label for a stored location, from R.array.locations. */
    fun locationLabel(context: Context, stored: String?): String? =
        label(context, stored, Constants.LOCATIONS, R.array.locations)

    private fun label(context: Context, stored: String?, values: List<String>, arrayRes: Int): String? {
        if (stored == null) return null
        val index = values.indexOf(stored)
        val labels = context.resources.getStringArray(arrayRes)
        return if (index in labels.indices) labels[index] else stored
    }

    /**
     * Open Library cover when the item has one, otherwise the category icon in the item's color
     * (owner decision, replacing the DiceBear badge). A cover that fails to load (404 with
     * default=false) falls back to the icon.
     */
    fun loadPicture(view: ImageView, item: Item, @DimenRes sizeRes: Int) {
        val cover = item.coverUrl
        if (cover == null) {
            showCategoryIcon(view, item, sizeRes)
            return
        }
        view.imageTintList = null
        view.setPadding(0, 0, 0, 0)
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        view.load(cover) {
            crossfade(true)
            listener(onError = { _, _ -> showCategoryIcon(view, item, sizeRes) })
        }
    }

    private fun showCategoryIcon(view: ImageView, item: Item, @DimenRes sizeRes: Int) {
        view.dispose()
        val padding = view.resources.getDimensionPixelSize(sizeRes) / ICON_PADDING_DIVISOR
        view.setPadding(padding, padding, padding, padding)
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        view.setImageResource(categoryIcon(item.category))
        view.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(view.context, colorRes(item.color)))
    }

    @DrawableRes
    fun categoryIcon(category: String?): Int = when (category) {
        "Book" -> R.drawable.ic_cat_book
        "Bag" -> R.drawable.ic_cat_bag
        "ID or card" -> R.drawable.ic_cat_card
        "Electronics" -> R.drawable.ic_cat_electronics
        "Clothing" -> R.drawable.ic_cat_clothing
        "Keys" -> R.drawable.ic_cat_keys
        else -> R.drawable.ic_cat_other
    }

    /** Swatch for a stored color key; no color (or "other") uses the accent. */
    @ColorRes
    fun colorRes(key: String?): Int = when (key) {
        "black" -> R.color.item_black
        "white" -> R.color.item_white
        "gray" -> R.color.item_gray
        "blue" -> R.color.item_blue
        "red" -> R.color.item_red
        "green" -> R.color.item_green
        "yellow" -> R.color.item_yellow
        "brown" -> R.color.item_brown
        "pink" -> R.color.item_pink
        else -> R.color.accent
    }

    /** Display label for a stored color key, from R.array.item_colors. */
    fun colorLabel(context: Context, key: String?): String? =
        label(context, key, Constants.COLORS, R.array.item_colors)

    fun badgeUrl(view: ImageView, seed: String, @DimenRes sizeRes: Int): String {
        val context = view.context
        return BadgeUrlBuilder.build(
            seed = seed,
            rowColor = ContextCompat.getColor(context, R.color.accent),
            backgroundColor = ContextCompat.getColor(context, R.color.accent_tint),
            sizePx = context.resources.getDimensionPixelSize(sizeRes),
        )
    }

    private const val ICON_PADDING_DIVISOR = 5
}
