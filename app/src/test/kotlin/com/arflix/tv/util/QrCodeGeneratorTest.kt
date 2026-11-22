package com.arflix.tv.util

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Test

class QrCodeGeneratorTest {
    @Test
    fun finderPatternDeterminesModuleSizeDespiteQuietZone() {
        val matrix = QRCodeWriter().encode("https://example.org/pair", BarcodeFormat.QR_CODE, 512, 512)
        val (left, top) = matrix.enclosingRectangle
        val moduleSize = QrCodeGenerator.moduleSize(matrix, left, top)
        assertThat(moduleSize).isGreaterThan(1)
        assertThat(matrix[left + 7 * moduleSize - 1, top]).isTrue()
        assertThat(matrix[left + 7 * moduleSize, top]).isFalse()
    }
}
