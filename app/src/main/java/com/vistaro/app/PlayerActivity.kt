package com.vistaro.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.vistaro.app.core.model.MediaType
import com.vistaro.app.data.playback.PlaybackInfo
import com.vistaro.app.data.playback.VidApiPlaybackProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The only place in the app where a WebView is created. */
class PlayerActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var appContainer: AppContainer
    private lateinit var playbackInfo: PlaybackInfo
    private lateinit var mediaType: MediaType
    private var contentId = 0
    private var season = 0
    private var episode = 0
    private var resumeAtSeconds = 0L
    private var positionMs = 0L
    private var durationMs = 0L
    private var lastPersistAt = 0L
    private var lastPersistPositionMs = -1L
    private var completed = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var errorActions: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        appContainer = (application as VistaroApplication).container

        val typeValue = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        mediaType = if (typeValue == MediaType.MOVIE.name) MediaType.MOVIE else MediaType.TV_SHOW
        contentId = intent.getIntExtra(EXTRA_TMDB_ID, 0)
        season = intent.getIntExtra(EXTRA_SEASON, 0)
        episode = intent.getIntExtra(EXTRA_EPISODE, 0)
        val imdbId = intent.getStringExtra(EXTRA_IMDB_ID)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (contentId <= 0 || (mediaType == MediaType.TV_SHOW && (season <= 0 || episode <= 0))) {
            showFatalError("Choose a valid movie or TV episode to play.")
            return
        }

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = buildWebView()
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            text = "Connecting to VidAPI…"
        }
        root.addView(webView, matchParent())
        root.addView(statusView, matchParent())
        setContentView(root)

        activityScope.launch {
            val saved = withContext(Dispatchers.IO) {
                appContainer.watchProgressRepository.get(contentId, mediaType, season, episode)
            }
            if (isFinishing || isDestroyed) return@launch
            resumeAtSeconds = saved?.takeIf { !it.completed && it.positionMs > 5_000 }?.positionMs?.div(1000) ?: 0L
            playbackInfo = if (mediaType == MediaType.MOVIE) {
                appContainer.playbackProvider.getMoviePlayback(contentId, imdbId, resumeAtSeconds)
            } else {
                appContainer.playbackProvider.getEpisodePlayback(0, contentId, season, episode, imdbId, resumeAtSeconds)
            }
            if (title.isNotBlank()) statusView.contentDescription = "Playing $title"
            webView.loadUrl(playbackInfo.url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        addJavascriptInterface(PlayerEventBridge(), BRIDGE_NAME)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val host = request.url.host.orEmpty()
                return request.url.scheme != "https" || !(host == PLAYER_HOST || host.endsWith(".$PLAYER_HOST"))
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectPlayerEventListener()
                if (statusView.parent != null) root.removeView(statusView)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) showPlaybackError("VidAPI could not load this player. Check your connection and retry.")
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                statusView.visibility = View.GONE
                root.addView(view, matchParent())
                hideSystemBars()
            }

            override fun onHideCustomView() {
                customView?.let(root::removeView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
                if (statusView.parent != null) statusView.visibility = View.VISIBLE
                hideSystemBars()
            }
        }
    }

    private fun injectPlayerEventListener() {
        val script = """(function(){if(window.__vistaroPlayerListener)return;window.__vistaroPlayerListener=true;window.addEventListener('message',function(e){try{if(e.data&&e.data.type==='PLAYER_EVENT'){${BRIDGE_NAME}.onPlayerEvent(JSON.stringify(e.data));}}catch(_){}});})();"""
        webView.evaluateJavascript(script, null)
    }

    private inner class PlayerEventBridge {
        @JavascriptInterface
        fun onPlayerEvent(payload: String) {
            runOnUiThread { consumePlayerEvent(payload) }
        }
    }

    private fun consumePlayerEvent(payload: String) {
        val event = runCatching { JSONObject(payload) }.getOrNull() ?: return
        if (event.optString("type") != "PLAYER_EVENT") return
        val data = event.optJSONObject("data") ?: return
        val status = data.optString("player_status")
        if (status !in PLAYER_STATUSES) return
        positionMs = (data.optDouble("player_progress", 0.0) * 1000).toLong().coerceAtLeast(0)
        durationMs = (data.optDouble("player_duration", 0.0) * 1000).toLong().coerceAtLeast(0)
        completed = status == "completed"
        val now = System.currentTimeMillis()
        val shouldPersist = status == "paused" || status == "seeked" || status == "completed" ||
            (status == "playing" && (now - lastPersistAt >= PROGRESS_SAVE_INTERVAL_MS || positionMs - lastPersistPositionMs >= PROGRESS_SAVE_INTERVAL_MS))
        if (shouldPersist && durationMs > 0) persistProgress()
    }

    private fun persistProgress() {
        val savedPosition = positionMs
        val savedDuration = durationMs
        val savedCompleted = completed
        lastPersistAt = System.currentTimeMillis()
        lastPersistPositionMs = savedPosition
        appContainer.ioScope.launch {
            appContainer.watchProgressRepository.save(contentId, mediaType, season, episode, savedPosition, savedDuration, savedCompleted)
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        if (durationMs > 0) persistProgress()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
        hideSystemBars()
    }

    override fun onBackPressed() {
        if (customView != null) {
            customView?.let(root::removeView)
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            webView.visibility = View.VISIBLE
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.onPause()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activityScope.cancel()
        super.onDestroy()
    }

    private fun showPlaybackError(message: String) {
        statusView.text = message
        if (statusView.parent == null) root.addView(statusView, matchParent())
        statusView.visibility = View.VISIBLE
        if (errorActions != null) return
        errorActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val retry = Button(context).apply {
                text = "Retry"
                setOnClickListener { statusView.text = "Connecting to VidAPI…"; webView.reload() }
            }
            val back = Button(context).apply { text = "Back"; setOnClickListener { finish() } }
            addView(retry)
            addView(back)
        }
        root.addView(errorActions, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))
    }

    private fun showFatalError(message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply { text = message; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER })
            addView(Button(context).apply { text = "Back"; setOnClickListener { finish() } })
        }
        setContentView(layout)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    private fun matchParent() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    companion object {
        const val EXTRA_TYPE = "com.vistaro.player.TYPE"
        const val EXTRA_TMDB_ID = "com.vistaro.player.TMDB_ID"
        const val EXTRA_IMDB_ID = "com.vistaro.player.IMDB_ID"
        const val EXTRA_SEASON = "com.vistaro.player.SEASON"
        const val EXTRA_EPISODE = "com.vistaro.player.EPISODE"
        const val EXTRA_TITLE = "com.vistaro.player.TITLE"
        private const val BRIDGE_NAME = "VistaroPlayer"
        private const val PLAYER_HOST = "vaplayer.ru"
        private const val PROGRESS_SAVE_INTERVAL_MS = 20_000L
        private val PLAYER_STATUSES = setOf("playing", "paused", "seeked", "completed")
    }
}
