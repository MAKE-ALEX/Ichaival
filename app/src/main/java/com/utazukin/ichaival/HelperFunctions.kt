/*
 * Ichaival - Android client for LANraragi https://github.com/Utazukin/Ichaival/
 * Copyright (C) 2026 Utazukin
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.utazukin.ichaival

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlResizeFilter
import com.awxkee.jxlcoder.PreferredColorConfig
import com.awxkee.jxlcoder.ScaleMode
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.hippo.image.BitmapDecoder
import com.hippo.image.ImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Stack
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import kotlin.math.max
import kotlin.math.min

fun getDpWidth(pxWidth: Int) : Int {
    val metrics = Resources.getSystem().displayMetrics
    return (pxWidth / metrics.density).toInt()
}

fun getDpAdjusted(pxSize: Int) : Int {
    val metrics = Resources.getSystem().displayMetrics
    return (pxSize * metrics.density).toInt()
}

@Suppress("DEPRECATION")
fun Activity.getWindowWidth() : Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        windowManager.currentWindowMetrics.bounds.width()
    else {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        metrics.widthPixels
    }
}

data class TermInfo(val term: String, val exact: Boolean, val negative: Boolean)

fun parseTermsInfo(query: CharSequence) : List<TermInfo> {
    val terms = mutableListOf<TermInfo>()
    val term = StringBuilder()
    val stack = Stack<Char>()
    var exact = false
    var negative = false
    for (c in query) {
        when {
            c == '\\' && stack.peekOrNull() != c -> stack.push(c)
            c == '\'' && stack.peekOrNull() == '"' -> term.append(c)
            c == '"' && stack.peekOrNull() == '\'' -> term.append(c)
            c == '\'' && stack.peekOrNull() == '\\' -> {
                stack.pop()
                term.append(c)
            }
            c == '"' && stack.peekOrNull() == '\\' -> {
                stack.pop()
                term.append(c)
            }
            (c == '\'' || c == '"') && stack.peekOrNull() == c -> {
                stack.pop()
                exact = stack.empty()
            }
            c == '\'' || c== '"' -> stack.push(c)
            c == ' ' && stack.empty() -> {
                terms.add(TermInfo(term.toString(), exact, negative))
                term.clear()
                exact = false
                negative = false
            }
            c == '_' && stack.empty() -> term.append(' ')
            c == '-' && stack.empty() && term.isEmpty() -> negative = true
            else -> term.append(c)
        }
    }

    while (!stack.empty())
        term.append(stack.pop())

    if (term.isNotEmpty())
        terms.add(TermInfo(term.toString(), exact, negative))

    return terms
}

fun parseTerms(query: CharSequence) = parseTermsInfo(query).map { it.term }

private fun <T> Stack<T>.peekOrNull() = if (empty()) null else peek()

fun SharedPreferences.castStringPrefToInt(pref: String, defaultValue: Int = 0) : Int {
    val stringPref = getString(pref, null)
    return if (stringPref.isNullOrBlank()) defaultValue else stringPref.toInt()
}

fun SharedPreferences.castStringPrefToLong(pref: String, defaultValue: Long = 0) : Long {
    val stringPref = getString(pref, null)
    return if (stringPref.isNullOrBlank()) defaultValue else stringPref.toLong()
}

fun SharedPreferences.castStringPrefToFloat(pref: String, defaultValue: Float = 0f) : Float {
    val stringPref = getString(pref, null)
    return if (stringPref.isNullOrBlank()) defaultValue else stringPref.toFloat()
}

fun Context.getCustomTheme() : String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    return prefs.getString(getString(R.string.theme_pref), getString(R.string.dark_theme)).toString()
}

fun JsonObject.getOrNull(memberName: String) : JsonElement? {
    val member = get(memberName)
    return if (member?.isJsonNull != false) null else member
}

fun <T> MutableList<T>.removeRange(start: Int, count: Int) {
    var i = min(start + count, size - 1)
    while (i >= 0 && i >= start)
        removeAt(i--)
}

fun parseDateAdded(tags: String): Long {
    val timeStampIndex = tags.indexOf("date_added:")
    return if (timeStampIndex < 0) 0L
    else {
        val tagStart = tags.indexOf(':', timeStampIndex) + 1
        var tagEnd = tags.indexOf(',', tagStart)
        if (tagEnd < 0)
            tagEnd = tags.length

        val dateTag = tags.substring(tagStart, tagEnd)
        dateTag.toLongOrNull() ?: 0L
    }
}

val BitmapFactory.Options.outSize: Size
    get() = Size(outWidth, outHeight)

fun getImageFormat(imageFile: File) : ImageFormat? {
    val info = ImageInfo()
    return imageFile.inputStream().use {
        if (BitmapDecoder.decode(it, info))
            ImageFormat.fromInt(info.format)
        else
            null
    }
}

private fun ByteArray.isAscii(offset: Int, value: String) : Boolean {
    if (size < offset + value.length)
        return false

    for (index in value.indices) {
        if (this[offset + index].toInt().toChar() != value[index])
            return false
    }
    return true
}

fun isSupportedAnimatedWebp(imageFile: File) : Boolean {
    val header = ByteArray(32)
    val bytesRead = imageFile.inputStream().use { it.read(header) }
    if (bytesRead < header.size)
        return false

    if (!header.isAscii(0, "RIFF") || !header.isAscii(8, "WEBP") || !header.isAscii(12, "VP8X"))
        return false

    return (header[20].toInt() and 0x02) != 0
}

fun isAnimatedImage(imageFile: File) : Boolean {
    return getImageFormat(imageFile) == ImageFormat.GIF || isSupportedAnimatedWebp(imageFile)
}

fun isTankId(id: CharSequence?) = id?.startsWith("TANK_") == true

fun SubsamplingScaleImageView.setDefaultScale() {
    if (!isReady)
        return

    val avgScale = (width + height) / 2f
    val imgScale = (sWidth + sHeight) / 2f
    val ratio = avgScale / imgScale
    if (maxScale < ratio) {
        maxScale = ratio
        setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM)
        resetScaleAndCenter()
    }
}

fun isLocalFile(path: String) = path.startsWith("/data")

fun ImageLoader.createGifLoader() : ImageLoader {
    return newBuilder().components {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            add(AnimatedImageDecoder.Factory())
        else {
            add(GifDecoder.Factory())
            add(AnimatedWebPDecoder.Factory())
        }
    }.build()
}

val ImageLoader.diskCacheSize get() = diskCache?.size ?: 0

fun ImageLoader.clearDiskCache() = diskCache?.clear()

suspend fun ImageLoader.cacheOrGet(request: ImageRequest, transcodeJxl: Boolean = true) : File? {
    return withContext(Dispatchers.IO) {
        val cache = diskCache?.openSnapshot(request.data as String)?.use { it.data.toFile() }
        val file = if (cache != null)
            cache
        else {
            execute(request)
            diskCache?.openSnapshot(request.data as String)?.use { it.data.toFile() }
        }

        if (transcodeJxl) file?.let { transcodeJxlPage(it) } else file
    }
}

// The reader hands pages to SubsamplingScaleImageView, which decodes them with BitmapRegionDecoder
// and therefore cannot open JPEG XL. Some archives simply contain .jxl pages, so decode those once
// with libjxl and keep a JPEG copy that the reader can actually load.
fun transcodeJxlPage(page: File) : File? {
    if (!isJxlFile(page))
        return page

    val key = page.absolutePath.hashCode().toString(16)
    val target = File(File(App.context.cacheDir, "jxl-pages"), "$key-${page.nameWithoutExtension}.jpg")
    if (target.isFile && target.lastModified() >= page.lastModified())
        return target

    val bitmap = tryOrNull { decodeJxlPage(page) }
    if (bitmap == null) {
        Log.e("HelperFunctions", "Could not decode JPEG XL page ${page.name}")
        return null
    }

    val success = tryOrNull {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    } ?: false
    bitmap.recycle()

    if (!success || !target.isFile) {
        Log.e("HelperFunctions", "Could not write decoded page ${target.name}")
        return null
    }

    return target
}

fun isJxlFile(file: File) : Boolean {
    val header = tryOrNull {
        file.inputStream().use { stream -> ByteArray(12).also { stream.read(it) } }
    } ?: return false

    // ISO BMFF container: 00 00 00 0C 'JXL ' 0D 0A 87 0A
    if (header.size >= 12 &&
        header[4] == 'J'.code.toByte() && header[5] == 'X'.code.toByte() &&
        header[6] == 'L'.code.toByte() && header[7] == ' '.code.toByte())
        return true

    // A bare codestream starts with FF 0A
    return header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0x0A.toByte()
}
private fun decodeJxlPage(page: File) : Bitmap? {
    val bytes = page.readBytes()
    val size = JxlCoder.getSize(bytes) ?: return null
    val longest = max(size.width, size.height)
    val bitmap = if (longest <= MAX_PAGE_DIMENSION)
        JxlCoder.decode(bytes, PreferredColorConfig.DEFAULT, ScaleMode.FIT)
    else {
        // Very tall pages would blow up the heap if decoded at full size; keep them readable instead.
        val scale = MAX_PAGE_DIMENSION.toFloat() / longest
        val width = (size.width * scale).toInt().coerceAtLeast(1)
        val height = (size.height * scale).toInt().coerceAtLeast(1)
        JxlCoder.decodeSampled(bytes, width, height,
            PreferredColorConfig.DEFAULT, ScaleMode.RESIZE, JxlResizeFilter.BICUBIC)
    }
    return bitmap
}

private const val MAX_PAGE_DIMENSION = 4096

// Decoding is the expensive part of opening a JPEG XL page, so keep the latest bitmaps around and
// hand them straight to the reader. This is what makes turning pages feel instant.
private val pageBitmapCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(16 * 1024 * 1024)) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private fun bitmapCacheKey(page: File) = "${page.absolutePath}:${page.length()}:${page.lastModified()}"

fun cachedJxlBitmap(page: File) : Bitmap? = pageBitmapCache.get(bitmapCacheKey(page))?.takeUnless { it.isRecycled }

fun cacheJxlBitmap(page: File, bitmap: Bitmap) {
    pageBitmapCache.put(bitmapCacheKey(page), bitmap)
}

/** Decodes a JPEG XL page, reusing the in-memory cache. Call from a background thread. */
fun loadJxlBitmap(page: File) : Bitmap? {
    cachedJxlBitmap(page)?.let { return it }

    val bitmap = tryOrNull { decodeJxlPage(page) } ?: return null
    cacheJxlBitmap(page, bitmap)
    return bitmap
}

fun downloadCoilImageWithProgress(context: Context, imagePath: String, uiProgressListener: (Int) -> Unit) : ImageRequest {
    return downloadCoilImageWithProgress(context, imagePath, object: UIProgressListener {
        override fun update(progress: Int) {
            uiProgressListener(progress)
        }
    })
}

fun ImageRequest.Builder.addAuthHeader() : ImageRequest.Builder {
    val headers = NetworkHeaders.Builder()
    if (WebHandler.apiKey.isNotEmpty())
        headers["Authorization"] = WebHandler.apiKey

    for ((name, value) in WebHandler.customHeaders) {
        headers[name] = value
    }

    return this.httpHeaders(headers.build())
}

private fun downloadCoilImageWithProgress(context: Context, imagePath: String, uiProgressListener: UIProgressListener) : ImageRequest {
    return ImageRequest.Builder(context).apply {
        addAuthHeader()
        data(imagePath)
        memoryCachePolicy(CachePolicy.DISABLED)
        listener(
                onStart = { ResponseProgressListener.expect(imagePath, uiProgressListener) },
                onCancel = { ResponseProgressListener.forget(imagePath) },
                onError = { _, _ -> ResponseProgressListener.forget(imagePath) }
        )
    }.build()
}

fun Size.toRect() = Rect(0, 0, width, height)

inline fun <T> tryOrNull(body: () -> T) : T? {
    return try {
        body()
    }
    catch (e: Exception) {
        null
    }
}

private var mMaxTextureSize = -1
//Converted to Kotlin from
//https://stackoverflow.com/questions/7428996/hw-accelerated-activity-how-to-get-opengl-texture-size-limit/26823288#26823288
fun getMaxTextureSize() : Int {
    if (mMaxTextureSize > 0)
        return mMaxTextureSize

    val IMAGE_MAX_BITMAP_DIMENSION = 2048
    val egl: EGL10 = EGLContext.getEGL() as EGL10
    val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)

    val version = IntArray(2)
    egl.eglInitialize(display, version)

    val totalConfigurations = IntArray(1)
    egl.eglGetConfigs(display, null, 0, totalConfigurations)

    val configurationsList = Array<EGLConfig?>(totalConfigurations[0]) { null }
    egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations)

    val textureSize = IntArray(1)
    var maxTextureSize = 0

    for (configuration in configurationsList) {
        egl.eglGetConfigAttrib(display, configuration, EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize)

        if (maxTextureSize < textureSize[0])
            maxTextureSize = textureSize[0]
    }

    egl.eglTerminate(display)
    mMaxTextureSize = max(maxTextureSize, IMAGE_MAX_BITMAP_DIMENSION)
    return mMaxTextureSize
}