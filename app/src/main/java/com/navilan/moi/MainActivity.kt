package com.navilan.moi

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val btRequest = 901
    private val webUrl = "https://navilanmoisoftware.blogspot.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestBtPermissions()
        setupWebView()
    }

    private fun requestBtPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val need = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), btRequest)
        }
    }

    private fun setupWebView() {
        web = findViewById(R.id.webView)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(PrinterBridge(), "AndroidPrinter")
        web.loadUrl(webUrl)
    }

    inner class PrinterBridge {
        @JavascriptInterface
        fun printReceipt(receiptHtml: String, copies: Int) {
            runOnUiThread {
                renderAndPrint(receiptHtml, max(1, min(copies, 20)))
            }
        }
    }

    private fun renderAndPrint(html: String, copies: Int) {
        val render = WebView(this)
        render.settings.javaScriptEnabled = true
        render.settings.domStorageEnabled = true
        render.setBackgroundColor(Color.WHITE)

        val targetWidth = 576
        val pxPerMm = targetWidth / 80f
        val heightPx = 900
        render.layout(0, 0, targetWidth, heightPx)

        val wrapper = """
            <html><head><meta name="viewport" content="width=$targetWidth">
            <style>
            *{box-sizing:border-box}
            html,body{margin:0;padding:0;background:#fff;color:#000;width:${targetWidth}px}
            body{font-family:'Noto Sans Tamil','Noto Sans',sans-serif}
            img{max-width:100%}
            </style></head><body>$html</body></html>
        """.trimIndent()

        render.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                handler.postDelayed({
                    val contentHeight = max(120, (render.contentHeight * render.scale).toInt() + 30)
                    render.layout(0, 0, targetWidth, contentHeight)
                    render.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(targetWidth, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(contentHeight, android.view.View.MeasureSpec.EXACTLY)
                    )
                    render.layout(0, 0, targetWidth, contentHeight)
                    val bmp = Bitmap.createBitmap(targetWidth, contentHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    render.draw(canvas)
                    sendBitmapToPrinter(bmp, copies)
                }, 500)
            }
        }
        render.loadDataWithBaseURL(null, wrapper, "text/html", "UTF-8", null)
    }

    private fun findPrinter(): BluetoothDevice? {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return null
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val bonded = adapter.bondedDevices ?: emptySet()
        return bonded.firstOrNull {
            val n = it.name?.lowercase() ?: ""
            n.contains("xp-80") || n.contains("xp80") || n.contains("xprinter") || n.contains("pos-80") || n.contains("pos80")
        } ?: bonded.firstOrNull()
    }

    private fun sendBitmapToPrinter(bitmap: Bitmap, copies: Int) {
        Thread {
            try {
                val device = findPrinter() ?: throw Exception("XP-80 Bluetooth printer is not paired")
                if (android.os.Build.VERSION.SDK_INT >= 31 &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    throw Exception("Bluetooth permission denied")
                }
                val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
                adapter.cancelDiscovery()
                val socket = device.createRfcommSocketToServiceRecord(spp)
                socket.connect()
                socket.outputStream.use { out ->
                    out.write(byteArrayOf(0x1B, 0x40))
                    repeat(copies) {
                        out.write(toEscPosRaster(bitmap))
                        out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                    }
                    out.write(byteArrayOf(0x1D, 0x56, 0x00))
                    out.flush()
                }
                socket.close()
                runOnUiThread { Toast.makeText(this, "XP-80 Print முடிந்தது", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Print பிழை: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ESC/POS GS v 0 monochrome raster. 576 dots = typical 80mm / 203dpi head.
    private fun toEscPosRaster(src: Bitmap): ByteArray {
        val width = 576
        val scale = min(1f, width.toFloat() / src.width)
        val h = max(1, ceil(src.height * scale).toInt())
        val b = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val dstW = min(width, (src.width * scale).toInt())
        val dstH = min(h, (src.height * scale).toInt())
        val left = (width - dstW) / 2
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(src, null, android.graphics.Rect(left, 0, left + dstW, dstH), paint)

        val widthBytes = (width + 7) / 8
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00,
            (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
            (h and 0xFF).toByte(), ((h shr 8) and 0xFF).toByte()))

        for (y in 0 until h) {
            for (xb in 0 until widthBytes) {
                var value = 0
                for (bit in 0..7) {
                    val x = xb * 8 + bit
                    if (x < width) {
                        val p = b.getPixel(x, y)
                        val r = Color.red(p); val g = Color.green(p); val bl = Color.blue(p)
                        val gray = (r * 299 + g * 587 + bl * 114) / 1000
                        if (gray < 160) value = value or (0x80 shr bit)
                    }
                }
                out.write(value)
            }
        }
        b.recycle()
        return out.toByteArray()
    }
}
