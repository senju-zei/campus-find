package ph.edu.bulsu.campusfind.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import ph.edu.bulsu.campusfind.core.Constants
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Profile pictures are stored in the Realtime Database as a small square JPEG in Base64
 * (users/{uid}/photo), so no Firebase Storage (paid plan) is needed. About 15 to 30 KB each.
 */
object ProfilePhoto {

    /** Reads the picked image, turns it upright, crops the center square, shrinks and encodes it. */
    fun encode(context: Context, uri: Uri): String {
        val bitmap = decodeUpright(context, uri)
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, Constants.PHOTO_SIZE_PX, Constants.PHOTO_SIZE_PX, true)
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            // The rules refuse anything longer; lower the quality until it fits.
            if (encoded.length <= Constants.PHOTO_MAX_CHARS || quality <= 40) return encoded
            quality -= 15
        }
    }

    /** Null if the stored text is not a readable image. */
    fun decode(encoded: String): Bitmap? = try {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun decodeUpright(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the camera's rotation itself.
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                val scale = max(1, longest / (Constants.PHOTO_SIZE_PX * 4))
                decoder.setTargetSize(info.size.width / scale, info.size.height / scale)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // Android 7 to 8: read the size first, decode a smaller copy, then rotate by the EXIF tag.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= Constants.PHOTO_SIZE_PX * 2) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not read the picture")
        val degrees = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees == 0f) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }
}
