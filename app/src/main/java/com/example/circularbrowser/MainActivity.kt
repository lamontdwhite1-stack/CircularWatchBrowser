package com.example.circularbrowser

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private var activeWebView: WebView? = null
    private var customVideoView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CircularBrowserScreen(
                    onWebViewCreated = { webView ->
                        activeWebView = webView
                    },
                    onShowCustomView = { view, callback ->
                        customVideoView = view
                        customViewCallback = callback
                    },
                    onHideCustomView = {
                        customVideoView = null
                        customViewCallback = null
                    }
                )
            }
        }
    }

    // Hardware Rotating Bezel Controller (Crash-proof native implementation)
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null && event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 160f
            activeWebView?.scrollBy(0, delta.toInt())
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    // Hardware Back Button Controller
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customVideoView != null) {
            customViewCallback?.onCustomViewHidden()
            customVideoView = null
        } else if (activeWebView?.canGoBack() == true) {
            activeWebView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CircularBrowserScreen(
    onWebViewCreated: (WebView) -> Unit,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit
) {
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var targetUrl by remember { mutableStateOf("https://www.google.com") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Native Web Renderer
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT

                        // Multi-touch Pinch to Zoom
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }

                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            injectCircularStyles(view)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view != null && callback != null) {
                                onShowCustomView(view, callback)
                            }
                        }

                        override fun onHideCustomView() {
                            onHideCustomView()
                        }
                    }

                    loadUrl(targetUrl)
                    onWebViewCreated(this)
                }
            }
        )

        // Top Floating Search Button
        CompactChip(
            onClick = { showSearchDialog = true },
            label = {
                Text(
                    text = "🔍 Search",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            colors = ChipDefaults.chipColors(backgroundColor = Color(0xDD202020)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        )

        // Search Modal Overlay
        if (showSearchDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Enter Search or URL",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.90f)
                            .background(Color(0xFF2C2C2E), RoundedCornerShape(18.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(Color.Cyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        targetUrl = resolveTargetUrl(searchQuery)
                                        showSearchDialog = false
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompactButton(
                            onClick = { showSearchDialog = false },
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) {
                            Text("✕", color = Color.White, fontSize = 12.sp)
                        }
                        CompactButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    targetUrl = resolveTargetUrl(searchQuery)
                                    showSearchDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007AFF))
                        ) {
                            Text("Go", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun resolveTargetUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> {
            val encodedQuery = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
            "https://www.google.com/search?q=$encodedQuery"
        }
    }
}

private fun injectCircularStyles(view: WebView?) {
    val jsCode = """
        (function() {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
            }
            meta.content = 'width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=4.0, user-scalable=yes';

            var style = document.createElement('style');
            style.innerHTML = `
                * {
                    box-sizing: border-box !important;
                }
                body {
                    padding: 22% 12% 22% 12% !important;
                    margin: 0 !important;
                    word-wrap: break-word !important;
                    background-color: #000 !important;
                }
                header, nav, [class*="fixed"], [class*="sticky"], [id*="header"], [id*="cookie"] {
                    position: static !important;
                }
                video {
                    width: 100% !important;
                    height: auto !important;
                    max-height: 100vh !important;
                    object-fit: contain !important;
                    transform: scale(1.22) !important;
                    transform-origin: center center !important;
                    border-radius: 50% !important;
                    transition: transform 0.25s ease-out !important;
                }
                .html5-video-player, .video-stream, .player-container {
                    background: transparent !important;
                    border-radius: 50% !important;
                    overflow: hidden !important;
                }
            `;
            document.head.appendChild(style);

            var lastTap = 0;
            document.addEventListener('touchend', function(e) {
                var currentTime = new Date().getTime();
                var tapLength = currentTime - lastTap;
                if (tapLength < 300 && tapLength > 0) {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(v) {
                        if (v.style.transform === 'scale(1)') {
                            v.style.transform = 'scale(1.22)';
                        } else {
                            v.style.transform = 'scale(1)';
                        }
                    });
                }
                lastTap = currentTime;
            });
        })();
    """.trimIndent()

    view?.evaluateJavascript(jsCode, null)
}
