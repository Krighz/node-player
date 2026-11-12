package com.nodeplayer.music.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nodeplayer.common.logging.NodeLog
import com.nodeplayer.music.auth.YouTubeAccountManager
import com.nodeplayer.network.innertube.YouTubeAuth
import com.nodeplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch

private const val TAG = "YouTubeLoginSheet"
private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/**
 * Fullscreen glass dialog embedding a WebView for official Google/YouTube sign-in.
 * Intercepts cookies automatically when user completes sign-in to music.youtube.com (AUTH-01).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginSheet(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isCapturing by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1424))
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "Sign in to YouTube",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reload",
                            tint = Color.White.copy(alpha = 0.70f)
                        )
                    }
                }

                loginError?.let { message ->
                    Text(message, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }

                // Loading progress bar
                if (pageProgress > 0f && pageProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { pageProgress },
                        color = accent,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // WebView Container
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageProgress = newProgress / 100f
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        checkAndCaptureCookies(url)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        return false
                                    }

                                    private fun checkAndCaptureCookies(url: String?) {
                                        if (url == null || isCapturing) return
                                        val host = try { android.net.Uri.parse(url).host } catch (_: Exception) { null }
                                        if (host == "music.youtube.com") {
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            val sapisid = YouTubeAuth.extractSapisid(cookies)
                                            if (!sapisid.isNullOrBlank() && cookies != null) {
                                                isCapturing = true
                                                loginError = null
                                                NodeLog.i(TAG, "Captured authenticated session cookie: ${YouTubeAuth.maskCookieForLog(cookies)}")
                                                coroutineScope.launch {
                                                    try {
                                                        YouTubeAccountManager.onLoginSuccess(cookies)
                                                        onLoginSuccess()
                                                        onDismiss()
                                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                        throw cancelled
                                                    } catch (t: Exception) {
                                                        NodeLog.e(TAG, "Error finalizing YouTube login", t)
                                                        loginError = "Couldn't finish signing in. Reload to try again."
                                                        isCapturing = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                loadUrl(LOGIN_URL)
                                webViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isCapturing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F1424).copy(alpha = 0.90f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = accent)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Signing in to YouTube…",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
