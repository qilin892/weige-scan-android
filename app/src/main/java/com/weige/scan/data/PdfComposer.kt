package com.weige.scan.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 多张照片合成一个 PDF。
 *
 * 这是「多页扫描」的关键一步：后端一个任务只吃一个文件，若把连拍的照片逐张提交，
 * 就会变成十几个互不相干的 Word。合成成一份多页 PDF 再上传，产物才是一整篇文档。
 *
 * ⚠️ 体积控制是这里最要紧的事（2026-10-30 实测）：
 * 手机原图直接塞进 PDF 时 2 页就有 8 MB；服务端转 Word 要向 MinerU 申请
 * `extra_formats:["docx"]`，超大图片页会让云端 docx 生成慢到 **10 分钟以上**
 * （同一份文件不带 docx 只要 25 秒），用户看到的就是「卡死」。所以这里做两级压制：
 *   1. 解码时按长边限幅（1600px ≈ A4 上 193 DPI，OCR 足够）；
 *   2. 再做一次 JPEG 往返抹掉高频噪点 —— PdfDocument 内嵌图片时会再编码一次，
 *      源图越「干净」，最终文件越小。
 * 最后还有体积预算兜底：超出就把边长降一档重来。
 */
object PdfComposer {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 18f

    /** 依次尝试的长边（像素），前一档超预算就退到下一档 */
    private val EDGES = intArrayOf(1600, 1280, 1080, 900)
    private const val JPEG_QUALITY = 80

    /** 每页体积预算：1600px 的一页扫描件通常 200~400KB，留足余量 */
    private const val BUDGET_PER_PAGE = 450L * 1024
    private const val BUDGET_BASE = 600L * 1024

    fun compose(images: List<File>, out: File): File {
        require(images.isNotEmpty()) { "没有可合成的图片" }
        val budget = BUDGET_BASE + BUDGET_PER_PAGE * images.size
        var last: File? = null
        for (edge in EDGES) {
            val f = writePdf(images, out, edge)
            last = f
            if (f.length() <= budget) return f
        }
        return last ?: out
    }

    /** 按指定长边合成一份 PDF（覆盖 out） */
    private fun writePdf(images: List<File>, out: File, edge: Int): File {
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var pages = 0
        try {
            for (file in images) {
                val bmp = decode(file, edge) ?: continue
                val page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pages).create()
                )
                val c = page.canvas
                c.drawColor(Color.WHITE)
                val avail = RectF(MARGIN, MARGIN, PAGE_W - MARGIN, PAGE_H - MARGIN)
                val scale = minOf(avail.width() / bmp.width, avail.height() / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val left = avail.left + (avail.width() - w) / 2f
                val top = avail.top + (avail.height() - h) / 2f
                c.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
                doc.finishPage(page)
                bmp.recycle()
            }
            check(pages > 0) { "图片读取失败，请重新拍摄" }
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
            return out
        } finally {
            doc.close()
        }
    }

    /** 解码 → 按 EXIF 摆正 → 限长边 → JPEG 往返压噪点 */
    private fun decode(file: File, edge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null

        bmp = rotateByExif(file, bmp)

        // inSampleSize 只能按 2 的幂降，可能仍比目标大，这里精确缩到长边
        val longest = maxOf(bmp.width, bmp.height)
        if (longest > edge) {
            val ratio = edge.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * ratio).toInt().coerceAtLeast(1),
                (bmp.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        return jpegRoundTrip(bmp) ?: bmp
    }

    private fun rotateByExif(file: File, src: Bitmap): Bitmap {
        val degrees = runCatching {
            when (
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return src
        val m = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (rotated !== src) src.recycle()
        return rotated
    }

    /**
     * 过一遍 JPEG：把照片的细碎噪点抹平，PdfDocument 再编码时体积明显变小。
     * 先铺白底，避免带透明通道的 PNG 被 JPEG 编成黑块。
     */
    private fun jpegRoundTrip(src: Bitmap): Bitmap? = runCatching {
        val flat = if (src.hasAlpha()) {
            Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also { b ->
                Canvas(b).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(src, 0f, 0f, null)
                }
            }
        } else src
        val bos = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos)
        if (flat !== src) flat.recycle()
        val bytes = bos.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
