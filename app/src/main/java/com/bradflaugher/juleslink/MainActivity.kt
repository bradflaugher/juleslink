package com.bradflaugher.juleslink

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.bradflaugher.juleslink.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    companion object {
        const val JULES_URL = "https://jules.google.com/"
        private val ALLOWED_HOST_SUFFIXES = listOf(
            "jules.google.com",
            "jules.google",
            "google.com",
            "googleusercontent.com",
            "gstatic.com",
            "googleapis.com",
            "ggpht.com",
            "youtube.com",
            "ytimg.com",
            "gvt1.com",
            "recaptcha.net",
        )
    }

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var splashHidden = false
    private var popupWebView: WebView? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_JulesLink)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        setupToolbar()
        setupWebView()
        setupSwipeRefresh()
        setupBackHandler()

        binding.retryButton.setOnClickListener {
            hideError()
            loadJules(force = true)
        }

        val deepLink = intent?.data?.toString()
        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
            hideSplash()
        } else {
            loadJules(startUrl = deepLink)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { loadJules(startUrl = it, force = true) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        binding.webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        destroyPopup()
        binding.webView.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> {
                    binding.webView.reload()
                    true
                }
                R.id.action_home -> {
                    loadJules(force = true)
                    true
                }
                R.id.action_share -> {
                    shareCurrentPage()
                    true
                }
                R.id.action_open_browser -> {
                    openExternal(binding.webView.url ?: JULES_URL)
                    true
                }
                R.id.action_clear_session -> {
                    confirmClearSession()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.webView, true)
        }

        binding.webView.apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.jules_bg))
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                allowFileAccess = false
                allowContentAccess = true
                // Critical for Google login: remove WebView marker from UA
                userAgentString = sanitizeUserAgent(userAgentString)
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
            }

            webViewClient = JulesWebViewClient()
            webChromeClient = JulesChromeClient()
            setDownloadListener { url, _, _, _, _ ->
                openExternal(url)
            }
        }
    }


    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.jules_mint),
            ContextCompat.getColor(this, R.color.jules_cyan),
            ContextCompat.getColor(this, R.color.jules_violet),
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.jules_surface)
        )
        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    popupWebView?.canGoBack() == true -> popupWebView?.goBack()
                    popupWebView != null -> destroyPopup()
                    binding.webView.canGoBack() -> binding.webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun loadJules(startUrl: String? = null, force: Boolean = false) {
        if (!isOnline()) {
            showError(getString(R.string.error_message))
            hideSplash()
            return
        }
        hideError()
        val url = when {
            startUrl != null && isAllowedUrl(startUrl) -> startUrl
            else -> JULES_URL
        }
        if (force || binding.webView.url.isNullOrBlank() || binding.webView.url == "about:blank") {
            binding.progressBar.isVisible = true
            binding.webView.loadUrl(url)
        }
    }

    private fun sanitizeUserAgent(original: String): String {
        // Google often rejects WebViews that advertise "; wv)". Look like Chrome Mobile.
        val cleaned = original
            .replace("; wv)", ")")
            .replace("Version/4.0 ", "")
        return if (cleaned.contains("Chrome/")) {
            cleaned
        } else {
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"
        }
    }

    private fun isAllowedUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return ALLOWED_HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }

    private fun isGoogleAuthUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return host.contains("accounts.google.") ||
            host.contains("myaccount.google.") ||
            host.contains("oauth") ||
            host.endsWith("google.com") && (
                url.contains("/signin") ||
                    url.contains("/ServiceLogin") ||
                    url.contains("/v3/signin") ||
                    url.contains("oauthchooseaccount")
                )
    }

    private fun shouldOpenExternally(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme != "http" && scheme != "https") return true
        val host = uri.host?.lowercase() ?: return true
        // Keep Jules + Google identity graph in-app for proper login cookies
        if (isAllowedUrl(url)) return false
        // Everything else (mailto, app stores, random sites) goes external
        return true
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, "No app can open this link", Snackbar.LENGTH_SHORT).show()
        }
    }


    private fun shareCurrentPage() {
        val url = binding.webView.url ?: JULES_URL
        val title = binding.webView.title ?: getString(R.string.app_name)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun confirmClearSession() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sign out?")
            .setMessage("This clears cookies and cached data for Jules and Google sign-in in this app.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign out") { _, _ -> clearSession() }
            .show()
    }

    private fun clearSession() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        binding.webView.apply {
            clearCache(true)
            clearHistory()
            clearFormData()
        }
        Toast.makeText(this, R.string.session_cleared, Toast.LENGTH_SHORT).show()
        loadJules(force = true)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hideSplash() {
        if (splashHidden) return
        splashHidden = true
        binding.splashOverlay.animate()
            .alpha(0f)
            .setDuration(280)
            .withEndAction {
                binding.splashOverlay.isVisible = false
                binding.splashOverlay.alpha = 1f
            }
            .start()
    }

    private fun showError(message: String) {
        binding.errorDetail.text = message
        binding.errorView.isVisible = true
        binding.progressBar.isVisible = false
        binding.swipeRefresh.isRefreshing = false
    }

    private fun hideError() {
        binding.errorView.isVisible = false
    }

    private fun destroyPopup() {
        popupWebView?.let { popup ->
            (popup.parent as? ViewGroup)?.removeView(popup)
            popup.stopLoading()
            popup.destroy()
        }
        popupWebView = null
    }

    // ── WebViewClient ────────────────────────────────────────────────────────

    private inner class JulesWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return handleUrl(view, url)
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return handleUrl(view, url)
        }

        private fun handleUrl(view: WebView, url: String): Boolean {
            val scheme = Uri.parse(url).scheme?.lowercase()
            when (scheme) {
                "http", "https" -> {
                    if (shouldOpenExternally(url)) {
                        openExternal(url)
                        return true
                    }
                    // Stay in-app for Jules + Google auth so cookies stick
                    return false
                }
                "intent" -> {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        val fallback = intent.getStringExtra("browser_fallback_url")
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                        } else if (!fallback.isNullOrBlank()) {
                            view.loadUrl(fallback)
                        }
                    } catch (_: Exception) {
                        // ignore malformed intents
                    }
                    return true
                }
                "mailto", "tel", "sms" -> {
                    openExternal(url)
                    return true
                }
                else -> {
                    openExternal(url)
                    return true
                }
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            binding.progressBar.isVisible = true
            hideError()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            binding.progressBar.isVisible = false
            binding.swipeRefresh.isRefreshing = false
            hideSplash()
            CookieManager.getInstance().flush()
            // Ensure third-party cookies remain accepted after navigation (login flows)
            CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                hideSplash()
                showError(getString(R.string.error_message))
            }
        }
    }

    // ── WebChromeClient (progress, file picker, OAuth popups) ────────────────

    private inner class JulesChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            binding.progressBar.isVisible = newProgress in 0 until 100
            if (newProgress >= 100) {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            this@MainActivity.filePathCallback?.onReceiveValue(null)
            this@MainActivity.filePathCallback = filePathCallback
            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            return try {
                fileChooserLauncher.launch(intent)
                true
            } catch (_: Exception) {
                this@MainActivity.filePathCallback = null
                false
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            // Google sign-in sometimes opens a secondary window; keep it in-app
            // so session cookies land in the same CookieManager jar.
            val popup = WebView(this@MainActivity).apply {
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(true)
                    userAgentString = sanitizeUserAgent(userAgentString)
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        if (!isAllowedUrl(url) && !isGoogleAuthUrl(url)) {
                            openExternal(url)
                            return true
                        }
                        // When auth completes and returns to Jules, fold popup into main view
                        if (url.contains("jules.google")) {
                            binding.webView.loadUrl(url)
                            destroyPopup()
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(v: WebView?, url: String?) {
                        CookieManager.getInstance().flush()
                        if (url != null && url.contains("jules.google") &&
                            !url.contains("accounts.google")
                        ) {
                            binding.webView.loadUrl(url)
                            destroyPopup()
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        destroyPopup()
                    }
                }
            }
            destroyPopup()
            popupWebView = popup
            binding.swipeRefresh.addView(popup)
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            destroyPopup()
        }
    }
}
