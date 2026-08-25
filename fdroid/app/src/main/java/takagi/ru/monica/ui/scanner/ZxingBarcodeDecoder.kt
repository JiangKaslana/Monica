package takagi.ru.monica.ui.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

internal class ZxingBarcodeDecoder(formats: Collection<BarcodeFormat>) : AutoCloseable {
    private val reader = MultiFormatReader()

    init {
        val hintFormats = formats.toList().ifEmpty { DEFAULT_FORMATS }
        reader.setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to hintFormats,
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    fun decodeFrame(imageProxy: ImageProxy): List<String> {
        val data = readLuminancePlane(imageProxy) ?: return emptyList()
        val source = PlanarYUVLuminanceSource(
            data,
            imageProxy.width,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false
        )
        return decodeWithRotations(source, imageProxy.imageInfo.rotationDegrees)
    }

    fun decodeUri(context: Context, uri: Uri): List<String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return emptyList()

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return emptyList()

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        return decodeWithRotations(RGBLuminanceSource(width, height, pixels), 0)
    }

    private fun decodeWithRotations(source: LuminanceSource, rotationDegrees: Int): List<String> {
        val initialRotations = ((360 - (rotationDegrees % 360)) % 360) / 90
        var bitmap = BinaryBitmap(HybridBinarizer(source))
        repeat(initialRotations) { bitmap = bitmap.rotateCounterClockwise() }
        repeat(ROTATION_ATTEMPTS) {
            val result = runCatching { reader.decodeWithState(bitmap) }.getOrNull()
            if (result != null) {
                val text = result.text?.trim()?.takeIf(String::isNotBlank)
                reader.reset()
                return listOfNotNull(text)
            }
            bitmap = bitmap.rotateCounterClockwise()
        }
        reader.reset()
        return emptyList()
    }

    private fun readLuminancePlane(imageProxy: ImageProxy): ByteArray? {
        val mediaImage = imageProxy.image ?: return null
        if (mediaImage.format != android.graphics.ImageFormat.YUV_420_888) return null
        val plane = mediaImage.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val width = imageProxy.width
        val height = imageProxy.height

        return if (plane.pixelStride == 1 && plane.rowStride == width) {
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            data
        } else {
            val data = ByteArray(width * height)
            var offset = 0
            for (row in 0 until height) {
                buffer.position(row * plane.rowStride)
                val copyLength = minOf(width, buffer.remaining())
                if (copyLength <= 0) break
                buffer.get(data, offset, copyLength)
                offset += width
            }
            data
        }
    }

    override fun close() {
        runCatching { reader.reset() }
    }

    private companion object {
        val DEFAULT_FORMATS = listOf(BarcodeFormat.QR_CODE)
        private const val MAX_IMAGE_DIMENSION = 1536
        private const val ROTATION_ATTEMPTS = 4
    }
}
