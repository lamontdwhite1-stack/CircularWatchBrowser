package com.example.circularbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CircularBrowserScreen(initialUrl = "https://en.wikipedia.org/wiki/Main_Page")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CircularBrowserScreen(initialUrl: String) {
    val focusRequester = remember { FocusRequester() }
    var webViewRef: WebView? = null

    Scaffold(
        timeText = { TimeText() }
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

                        loadUrl(initialUrl)
                    }
                }
            )
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
                    padding: 16% 12% 20% 12% !important;
                    margin: 0 !important;
                    word-wrap: break-word !important;
                    overflow-x: hidden !important;
                }
                header, nav, [class*="fixed"], [class*="sticky"], [id*="header"], [id*="cookie"] {
                    position: static !important;
                }
                img, video, iframe {
                    height: auto !important;
                    border-radius: 8px !important;
                }
            `;
            document.head.appendChild(style);
        })();
    """.trimIndent()

    view?.evaluateJavascript(jsCode, null)
}
