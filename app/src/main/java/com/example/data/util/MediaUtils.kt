package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import kotlin.math.min

object RikzenIdGenerator {
    private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(): String {
        val code = buildString {
            repeat(6) {
                append(CHARS[random.nextInt(CHARS.length)])
            }
        }
        return "RIKZEN-$code"
    }

    fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 7) "ph_$digits" else ""
    }
}

object MediaUtils {

    // 24 MB in-memory LRU cache of decoded ImageBitmaps to prevent main-thread Base64/JPEG decoding during scroll
    private val imageCache: LruCache<String, ImageBitmap> = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4).coerceAtLeast(1024)
        }
    }

    private fun cacheKey(base64: String, maxDim: Int): String =
        "${base64.length}:${base64.hashCode()}:$maxDim"

    fun getCachedImageBitmap(base64: String, maxDim: Int = 256): ImageBitmap? {
        if (base64.isBlank()) return null
        return imageCache.get(cacheKey(base64, maxDim))
    }

    suspend fun decodeBase64ToImageBitmapAsync(base64: String, maxDim: Int = 256): ImageBitmap? {
        if (base64.isBlank()) return null
        val key = cacheKey(base64, maxDim)
        imageCache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            imageCache.get(key)?.let { return@withContext it }
            val bmp = decodeBase64ToDownsampledBitmap(base64, maxDim) ?: return@withContext null
            val imgBmp = bmp.asImageBitmap()
            imageCache.put(key, imgBmp)
            imgBmp
        }
    }

    private fun decodeBase64ToDownsampledBitmap(base64: String, maxDim: Int): Bitmap? {
        return try {
            val decoded = Base64.decode(base64, Base64.DEFAULT)
            if (maxDim > 0) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size, bounds)
                val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                var sampleSize = 1
                while (largest / (sampleSize * 2) >= maxDim) {
                    sampleSize *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size, opts)
            } else {
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createTempCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 512): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val largest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (largest / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun decodeBitmapFromUriAsync(context: Context, uri: Uri, maxDim: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) {
            decodeBitmapFromUri(context, uri, maxDim)
        }

    fun cropSquareAndScale(
        bitmap: Bitmap,
        zoom: Float = 1f,
        offsetXFrac: Float = 0f,
        offsetYFrac: Float = 0f,
        outSize: Int = 256
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val baseSide = min(w, h).toFloat()
        val cropSide = (baseSide / zoom.coerceIn(1f, 3f)).toInt().coerceAtLeast(16)
        val maxShiftX = ((w - cropSide) / 2f).coerceAtLeast(0f)
        val maxShiftY = ((h - cropSide) / 2f).coerceAtLeast(0f)
        val centerX = w / 2f - offsetXFrac.coerceIn(-1f, 1f) * maxShiftX
        val centerY = h / 2f - offsetYFrac.coerceIn(-1f, 1f) * maxShiftY
        val left = (centerX - cropSide / 2f).toInt().coerceIn(0, (w - cropSide).coerceAtLeast(0))
        val top = (centerY - cropSide / 2f).toInt().coerceIn(0, (h - cropSide).coerceAtLeast(0))
        val cropped = Bitmap.createBitmap(bitmap, left, top, cropSide, cropSide)
        val scaled = Bitmap.createScaledBitmap(cropped, outSize, outSize, true)
        if (cropped != bitmap && cropped != scaled) {
            cropped.recycle()
        }
        return scaled
    }

    fun bitmapToBase64(bitmap: Bitmap, maxBytes: Int = 120_000, maxDim: Int = 480): String {
        val ratio = min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height).coerceAtMost(1f)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        var quality = 82
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 12
        } while (bytes.size > maxBytes && quality >= 24)
        if (scaled != bitmap) {
            scaled.recycle()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun bitmapToBase64Async(bitmap: Bitmap, maxBytes: Int = 120_000, maxDim: Int = 480): String =
        withContext(Dispatchers.Default) {
            bitmapToBase64(bitmap, maxBytes, maxDim)
        }

    suspend fun cropAndEncodeAsync(
        bitmap: Bitmap,
        zoom: Float,
        offsetXFrac: Float,
        offsetYFrac: Float,
        outSize: Int = 256,
        maxBytes: Int = 85_000
    ): String = withContext(Dispatchers.Default) {
        val cropped = cropSquareAndScale(bitmap, zoom, offsetXFrac, offsetYFrac, outSize)
        val encoded = bitmapToBase64(cropped, maxBytes = maxBytes, maxDim = outSize)
        if (cropped != bitmap) {
            cropped.recycle()
        }
        encoded
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return decodeBase64ToDownsampledBitmap(base64, maxDim = 0)
    }

    fun readFileAsBase64(context: Context, uri: Uri, maxBytes: Int = 280_000): Triple<String, String, String>? {
        return try {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            var name = "attachment"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(idx) ?: name
                }
            }
            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (raw.size > maxBytes) {
                return null
            }
            Triple(Base64.encodeToString(raw, Base64.NO_WRAP), name, mime)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun readFileAsBase64Async(
        context: Context,
        uri: Uri,
        maxBytes: Int = 280_000
    ): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        readFileAsBase64(context, uri, maxBytes)
    }

    fun encodeBytesToBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeBase64ToBytes(base64: String): ByteArray? = try {
        Base64.decode(base64, Base64.DEFAULT)
    } catch (_: Exception) {
        null
    }

    fun calculateDirectorySizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    suspend fun calculateDirectorySizeBytesAsync(dir: File?): Long = withContext(Dispatchers.IO) {
        calculateDirectorySizeBytes(dir)
    }

    suspend fun clearCacheDirAsync(dir: File?): Long = withContext(Dispatchers.IO) {
        dir?.listFiles()?.forEach { it.deleteRecursively() }
        calculateDirectorySizeBytes(dir)
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.2f MB".format(mb)
    }
}
