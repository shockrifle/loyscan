package com.bdaniel.loyscan.ui.scanner

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakeImageProxy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.Executor

class ScanAnalyzerTest {

    private val pending = mutableListOf<Runnable>()
    private val callbackExecutor = Executor { pending += it }
    private val scanned = mutableListOf<ScannedBarcode>()

    private fun analyzer(reader: FrameReader) = ScanAnalyzer(reader, callbackExecutor) { scanned += it }

    @Test
    fun aBarcode_isHandedOnThroughTheCallbackExecutor() {
        val barcode = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)
        val frame = FakeImageProxy()

        analyzer { barcode }.analyze(frame)

        assertThat(scanned).isEmpty()
        pending.forEach(Runnable::run)
        assertThat(scanned).containsExactly(barcode)
        assertThat(frame.closed).isTrue()
    }

    @Test
    fun aFrameWithoutABarcode_handsOnNothing() {
        val frame = FakeImageProxy()

        analyzer { null }.analyze(frame)

        assertThat(pending).isEmpty()
        assertThat(frame.closed).isTrue()
    }

    @Test
    fun aFrameTheReaderRejects_isSkippedAndClosed() {
        val frame = FakeImageProxy()

        analyzer { error("unsupported image format") }.analyze(frame)

        assertThat(pending).isEmpty()
        assertThat(frame.closed).isTrue()
    }
}
