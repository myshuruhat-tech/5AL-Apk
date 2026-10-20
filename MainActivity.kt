package com.livescreen.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Loads the live Netlify site when there is internet, and serves the bundled copy
 * of the site (assets/web) UNDER THE SAME ORIGIN when there is none. Same origin
 * means localStorage / IndexedDB (history, accounts, offline downloads) are shared
 * between online and offline, so saved videos are visible without internet.
 */
class MainActivity : Activity() {

    companion object {
        const val HOST = "elegant-empanada-94e121.netlify.app"
        const val START_URL = "https://$HOST/"
        private const val FORCE_OFFLINE_MS = 30_000L
    }

    private lateinit var web: WebView
    private var lastBackAt = 0L
    private var forceOfflineUntil = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString + " LiveScreenTV/2"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (request.method != "GET" || uri.host != HOST) return null
                if (!isOffline()) return null
                var path = uri.path ?: "/"
                if (path.startsWith("/.netlify/")) return null          // API functions need internet
                if (path.isEmpty() || path == "/") path = "/index.html"
                var name = path.removePrefix("/")
                var stream = try { assets.open("web/$name") } catch (e: Exception) { null }
                if (stream == null && request.isForMainFrame) {
                    name = "index.html"
                    stream = try { assets.open("web/index.html") } catch (e: Exception) { null }
                }
                if (stream == null) return null
                val ext = MimeTypeMap.getFileExtensionFromUrl(name)
                val mime = when (ext) {
                    "html", "htm" -> "text/html"
                    "js" -> "application/javascript"
                    "json" -> "application/json"
                    "css" -> "text/css"
                    else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                }
                return WebResourceResponse(mime, "utf-8", stream)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // Android says online, but the site could not be reached: retry from the bundled copy.
                if (request.isForMainFrame && request.url.host == HOST && SystemClock.uptimeMillis() >= forceOfflineUntil) {
                    forceOfflineUntil = SystemClock.uptimeMillis() + FORCE_OFFLINE_MS
                    view.loadUrl(START_URL)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(START_URL)
    }

    private fun isOffline(): Boolean {
        if (SystemClock.uptimeMillis() < forceOfflineUntil) return true
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= 23) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected != true
        }
    }

    /** Back goes to the page first (closes menus / player / returns to Home); a double press on Home exits. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.repeatCount == 0) handleBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleBack() {
        web.evaluateJavascript(
            "(function(){try{return (typeof state!=='undefined'&&state.screen)||''}catch(e){return ''}})()"
        ) { raw ->
            val screen = raw?.trim('"') ?: ""
            val now = SystemClock.uptimeMillis()
            if (screen == "discover") {
                if (now - lastBackAt < 2000) { finish(); return@evaluateJavascript }
                lastBackAt = now
                Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
            }
            web.evaluateJavascript(
                "document.dispatchEvent(new KeyboardEvent('keydown',{key:'GoBack',bubbles:true,cancelable:true}))", null
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush() }
    override fun onResume() { super.onResume(); web.onResume() }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
