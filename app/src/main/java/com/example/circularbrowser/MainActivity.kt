package com.example.circularbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CircularBrowserApp()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CircularBrowserApp() {
    val focusRequester = remember { FocusRequester() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("https://www.youtube.com") }

    // Hardware back button: Exits fullscreen video first, then navigates web history
    BackHandler(enabled = customVideoView != null || webViewRef?.canGoBack() == true) {
        if (customVideoView != null) {
            customViewCallback?.onCustomViewHidden()
            customVideoView = null
        } else {
            webViewRef?.goBack()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        timeText = { if (customVideoView == null) TimeText() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onRotaryScrollEvent { event ->
                    val scrollAmount = (event.verticalScrollPixels * 1.5).toInt()
                    webViewRef?.scrollBy(0, scrollAmount)
                    true
                }
        ) {
            // Standard Web View
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
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
                                customVideoView = view
                                customViewCallback = callback
                            }

                            override fun onHideCustomView() {
                                customVideoView = null
                                customViewCallback = null
                            }
                        }

                        loadUrl(currentUrl)
                    }
                },
                update = { webView ->
                    if (webView.url != currentUrl) {
                        webView.loadUrl(currentUrl)
                    }
                }
            )

            // Native Fullscreen Video Overlay
            customVideoView?.let { videoView ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        FrameLayout(it).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            addView(videoView)
                        }
                    }
                )
            }

            // Top Search Bar (Hidden during video playback)
            if (customVideoView == null) {
                CompactChip(
                    onClick = { showSearchDialog = true },
                    label = {
                        Text(
                            text = "🔍 Search / URL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xCC1E1E1E)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 22.dp)
                )
            }

            // Search Dialog Modal
            if (showSearchDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.94f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Enter Search or URL", color = Color.LightGray, fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .background(Color(0xFF2C2C2E), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                                            currentUrl = resolveTargetUrl(searchQuery)
                                            showSearchDialog = false
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactButton(
                                onClick = { showSearchDialog = false },
                                colors = ButtonDefaults.secondaryButtonColors()
                            ) {
                                Text("✕", color = Color.White, fontSize = 12.sp)
                            }
                            CompactButton(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        currentUrl = resolveTargetUrl(searchQuery)
                                        showSearchDialog = false
                                    }
                                },
                                colors = ButtonDefaults.primaryButtonColors()
                            ) {
                                Text("Go", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
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
            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

            var style = document.createElement('style');
            style.innerHTML = `
                * {
                    max-width: 100vw !important;
                    box-sizing: border-box !important;
                }
                body {
                    padding: 20% 12% 20% 12% !important;
                    margin: 0 !important;
                    word-wrap: break-word !important;
                    overflow-x: hidden !important;
                    background-color: #000 !important;
                }
                header, nav, [class*="fixed"], [class*="sticky"], [id*="header"], [id*="cookie"] {
                    position: static !important;
                }
                
                /* Adaptive Smart Video Reflow */
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

                /* Container optimization for embedded web players (YouTube, Vimeo) */
                .html5-video-player, .video-stream, .player-container {
                    background: transparent !important;
                    border-radius: 50% !important;
                    overflow: hidden !important;
                }
            `;
            document.head.appendChild(style);

            // Double-tap video to toggle between Smart Zoom (1.22x) and Exact Fit (1.0x)
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
                    e.preventDefault();
                }
                lastTap = currentTime;
            });
        })();
    """.trimIndent()

    view?.evaluateJavascript(jsCode, null)
}
