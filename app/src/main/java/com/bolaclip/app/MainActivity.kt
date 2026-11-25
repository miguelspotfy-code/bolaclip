package com.bolaclip.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingAction: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        stopOverlayService()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(NativeOverlayBridge(), "AndroidOverlay")
            addJavascriptInterface(NativeOverlayBridge(), "AndroidBridge")
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/bolaclip.html")
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startOverlayAction(action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            pendingAction = action
            Toast.makeText(this, "Primeiro permita 'Aparecer sobre outros apps'.", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, FloatingBubbleOverlayService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopOverlayService() {
        try {
            stopService(Intent(this, FloatingBubbleOverlayService::class.java))
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        val act = pendingAction
        if (act != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
            pendingAction = null
            startOverlayAction(act)
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    inner class NativeOverlayBridge {
        @JavascriptInterface
        fun startOverlay(jsonPayload: String? = null) {
            runOnUiThread {
                startOverlayAction(FloatingBubbleOverlayService.ACTION_SHOW_BUBBLES)
            }
        }

        @JavascriptInterface
        fun showOverlay(jsonPayload: String? = null) {
            runOnUiThread {
                startOverlayAction(FloatingBubbleOverlayService.ACTION_SHOW_BUBBLES)
            }
        }

        @JavascriptInterface
        fun hideOverlay(jsonPayload: String? = null) {
            runOnUiThread {
                stopOverlayService()
            }
        }

        @JavascriptInterface
        fun stopOverlay(jsonPayload: String? = null) {
            runOnUiThread {
                stopOverlayService()
            }
        }

        @JavascriptInterface
        fun postMessage(rawJson: String?) {
            if (rawJson.isNullOrBlank()) return
            runOnUiThread {
                when {
                    rawJson.contains("startOverlay") || rawJson.contains("showOverlay") ->
                        startOverlayAction(FloatingBubbleOverlayService.ACTION_SHOW_BUBBLES)
                    rawJson.contains("hideOverlay") || rawJson.contains("stopOverlay") ->
                        stopOverlayService()
                }
            }
        }
    }
}
