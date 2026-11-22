package com.arflix.tv.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    fun generate(content: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cornerRadius = size * 0.06f
        val bgRect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val (left, top, width, height) = bitMatrix.enclosingRectangle
        val moduleSize = moduleSize(bitMatrix, left, top)
        val moduleRadius = moduleSize * 0.3f
        var x = left
        while (x < left + width) {
            var y = top
            while (y < top + height) {
                if (bitMatrix[x, y]) {
                    val rect = RectF(
                        x.toFloat(), y.toFloat(),
                        (x + moduleSize).toFloat().coerceAtMost(size.toFloat()),
                        (y + moduleSize).toFloat().coerceAtMost(size.toFloat())
                    )
                    canvas.drawRoundRect(rect, moduleRadius, moduleRadius, fgPaint)
                }
                y += moduleSize
            }
            x += moduleSize
        }

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val outputCanvas = Canvas(output)
        val clipPath = Path().apply {
            addRoundRect(bgRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        outputCanvas.clipPath(clipPath)
        outputCanvas.drawBitmap(bitmap, 0f, 0f, null)

        return output
    }

    // The top-left finder pattern starts with a run of seven black modules.
    internal fun moduleSize(matrix: BitMatrix, left: Int, top: Int): Int {
        var end = left
        while (end < matrix.width && matrix[end, top]) end++
        return ((end - left) / 7).coerceAtLeast(1)
    }
}
