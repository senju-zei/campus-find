package ph.edu.bulsu.campusfind.ui.common

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import ph.edu.bulsu.campusfind.R

/** A row of item photos. Tap opens a photo full width; [onRemove] adds a remove button (Post item). */
object PhotoStrip {

    fun bind(container: LinearLayout, photos: List<String>, onRemove: ((Int) -> Unit)? = null) {
        val context = container.context
        val res = context.resources
        val height = res.getDimensionPixelSize(R.dimen.photo_strip_height)
        val gap = res.getDimensionPixelSize(R.dimen.space_2)
        container.removeAllViews()
        photos.forEachIndexed { index, encoded ->
            val bitmap = ProfilePhoto.decode(encoded) ?: return@forEachIndexed
            val width = (height * bitmap.width / bitmap.height.toFloat()).toInt()
            val image = ShapeableImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(width, height)
                scaleType = ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(res.getDimension(R.dimen.radius_control)).build()
                strokeColor = ContextCompat.getColorStateList(context, R.color.border)
                strokeWidth = res.getDimension(R.dimen.border_width)
                setImageBitmap(bitmap)
                contentDescription = res.getString(R.string.cd_item_photo, index + 1)
                setOnClickListener { showFull(this) }
            }
            val frame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(width, height).apply { if (index > 0) marginStart = gap }
                addView(image)
            }
            if (onRemove != null) {
                val size = res.getDimensionPixelSize(R.dimen.touch_target)
                val pad = res.getDimensionPixelSize(R.dimen.space_3)
                frame.addView(
                    ImageButton(context).apply {
                        layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END)
                        setImageResource(R.drawable.ic_close)
                        background = null
                        setPadding(pad, pad, pad, pad)
                        contentDescription = res.getString(R.string.cd_remove_photo, index + 1)
                        setOnClickListener { onRemove(index) }
                    },
                )
            }
            container.addView(frame)
        }
    }

    private fun showFull(source: ImageView) {
        val context = source.context
        val full = ImageView(context).apply {
            adjustViewBounds = true
            setImageDrawable(source.drawable)
            contentDescription = source.contentDescription
        }
        MaterialAlertDialogBuilder(context)
            .setView(full)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }
}
