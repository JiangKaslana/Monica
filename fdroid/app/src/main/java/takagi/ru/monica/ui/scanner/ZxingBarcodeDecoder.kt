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
        // 关键：不能使用 BinaryBitmap.rotateCounterClockwise()——PlanarYUVLuminanceSource
        // 不支持旋转（isRotateSupported=false），会抛 UnsupportedOperationException 导致
        // 相机竖屏（rotation=90/270）时每一帧都静默失败。
        // 改为手动旋转亮度矩阵后重建 source（已由 JVM 测试台验证全部场景通过）。
        var data = source.matrix
        var width = source.width
        var height = source.height
        val initialRotations = ((360 - (rotationDegrees % 360)) % 360) / 90
        repeat(initialRotations) {
            data = rotateLumaCounterClockwise(data, width, height)
            val swap = width
            width = height
            height = swap
        }
        repeat(ROTATION_ATTEMPTS) {
            val bitmap = BinaryBitmap(
                HybridBinarizer(
                    PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
                )
            )
            val result = runCatching { reader.decode(bitmap) }.getOrNull()
            if (result != null) {
                val text = result.text?.trim()?.takeIf(String::isNotBlank)
                reader.reset()
                return listOfNotNull(text)
            }
            runCatching { reader.reset() }
            data = rotateLumaCounterClockwise(data, width, height)
            val swap = width
            width = height
            height = swap
        }
        runCatching { reader.reset() }
        return emptyList()
    }

    /** 逆时针旋转 90°：new[x, h-1-y] = old[y, x]，返回新数据（调用方需交换宽高）。 */
    private fun rotateLumaCounterClockwise(src: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                out[x * height + (height - 1 - y)] = src[rowOffset + x]
            }
        }
        return out
    }

    private fun readLuminancePlane(imageProxy: ImageProxy): ByteArray? {
        val width = imageProxy.width
        val height = imageProxy.height
        // 优先用 mediaImage 的 Y 平面，失败则回退到 ImageProxy 自身的 plane（兼容部分设备 image 为空的情况）
        val yPlaneBuffer: java.nio.ByteBuffer
        val yPixelStride: Int
        val yRowStride: Int
        val mediaImage = imageProxy.image
        if (mediaImage != null && mediaImage.format == android.graphics.ImageFormat.YUV_420_888) {
            val plane = mediaImage.planes.firstOrNull() ?: return null
            yPlaneBuffer = plane.buffer
            yPixelStride = plane.pixelStride
            yRowStride = plane.rowStride
        } else {
            // 回退：ImageProxy 暴露的 plane（YUV_420_888 时 planes[0] 为 Y）
            if (imageProxy.planes.isEmpty()) return null
            val plane = imageProxy.planes[0]
            yPlaneBuffer = plane.buffer
            yPixelStride = plane.pixelStride
            yRowStride = plane.rowStride
        }
        // 确保从头读取
        yPlaneBuffer.rewind()
        return if (yPixelStride == 1 && yRowStride == width) {
            val data = ByteArray(width * height)
            // 此时 buffer.remaining() 可能 >= width*height（含末尾 padding），只取有效像素
            val toRead = minOf(data.size, yPlaneBuffer.remaining())
            yPlaneBuffer.get(data, 0, toRead)
            // 若 toRead < data.size（极少数设备），剩余保持 0
            data
        } else {
            val data = ByteArray(width * height)
            var offset = 0
            for (row in 0 until height) {
                yPlaneBuffer.position(row * yRowStride)
                val copyLength = minOf(width, yPlaneBuffer.remaining())
                if (copyLength <= 0) break
                yPlaneBuffer.get(data, offset, copyLength)
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
