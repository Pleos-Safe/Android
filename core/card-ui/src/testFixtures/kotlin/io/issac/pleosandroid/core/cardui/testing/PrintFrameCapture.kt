package io.issac.pleosandroid.core.cardui.testing

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Capture the app's own hardware surface. System map windows can overlap it on Pleos split screens. */
fun captureForgePrint(window: Window, rect: Rect): Bitmap {
    val image = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
    val handler = Handler(Looper.getMainLooper())
    var status = PixelCopy.ERROR_UNKNOWN
    repeat(3) {
        val ready = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val decor = window.decorView
            decor.postInvalidateOnAnimation()
            decor.postOnAnimation {
                PixelCopy.request(window, Rect(rect), image, { result ->
                    status = result
                    ready.countDown()
                }, handler)
            }
        }
        check(ready.await(8, TimeUnit.SECONDS)) { "App window did not produce a frame" }
        if (status == PixelCopy.SUCCESS) return image
        check(status == PixelCopy.ERROR_TIMEOUT || status == PixelCopy.ERROR_SOURCE_NO_DATA) {
            "Window capture failed ($status)"
        }
    }
    image.recycle()
    error("App surface did not supply a buffer ($status)")
}

fun writeForgeFrame(window: Window, rect: Rect, folder: String, index: Int) {
    val image = captureForgePrint(window, rect)
    try {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, folder).apply { mkdirs() }
        File(dir, "%03d.png".format(Locale.ROOT, index)).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    } finally {
        image.recycle()
    }
}
