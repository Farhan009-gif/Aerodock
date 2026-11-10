package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.mcp.JsSandboxExecutor
import com.example.ui.theme.CoralError

import com.example.ui.theme.EmeraldConnected
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay

/**
 * Prepared payload for [WebViewPreviewer] after validating size limits and assembling
 * the sandboxed HTML5 document from separate HTML, CSS, and JS strings.
 */
data class WebViewPreviewPayload(
    val fullHtmlDocument: String,
    val totalBytes: Int,
    val isMemoryLimitExceeded: Boolean,
    val errorMessage: String? = null
)

/**
 * Helper functions for preparing and securing content in [WebViewPreviewer].
 */
object WebViewPreviewerSecurity {

    /**
     * Combines [html], [css], and [js] into a single sandboxed HTML document and verifies
     * that neither the raw inputs nor the combined document exceed [maxMemoryBytes] (4 MB default).
     */
    fun preparePreviewPayload(
        html: String,
        css: String = "",
        js: String = "",
        maxMemoryBytes: Int = JsSandboxExecutor.MAX_MEMORY_BYTES
    ): WebViewPreviewPayload {
        val rawBytes = html.toByteArray(StandardCharsets.UTF_8).size +
            css.toByteArray(StandardCharsets.UTF_8).size +
            js.toByteArray(StandardCharsets.UTF_8).size
        if (rawBytes > maxMemoryBytes) {
            return WebViewPreviewPayload(
                fullHtmlDocument = "",
                totalBytes = rawBytes,
                isMemoryLimitExceeded = true,
                errorMessage = "Sandbox Error: Memory limit exceeded (${maxMemoryBytes / (1024 * 1024)} MB maximum)."
            )
        }

        val fullDoc = JsSandboxExecutor.buildFullHtmlDocument(
            html = html,
            css = css,
            js = js,
            maxMemoryBytes = maxMemoryBytes
        )
        val docBytes = fullDoc.toByteArray(StandardCharsets.UTF_8).size
        if (docBytes > maxMemoryBytes) {
            return WebViewPreviewPayload(
                fullHtmlDocument = "",
                totalBytes = docBytes,
                isMemoryLimitExceeded = true,
                errorMessage = "Sandbox Error: Memory limit exceeded (${maxMemoryBytes / (1024 * 1024)} MB maximum)."
            )
        }

        return WebViewPreviewPayload(
            fullHtmlDocument = fullDoc,
            totalBytes = docBytes,
            isMemoryLimitExceeded = false,
            errorMessage = null
        )
    }

    /**
     * Applies restrictive security settings to the given [WebView]:
     * - `blockNetworkLoads = true`
     * - `allowFileAccess = false`
     * - `allowContentAccess = false`
     * - `allowFileAccessFromFileURLs = false`
     * - `allowUniversalAccessFromFileURLs = false`
     * - `domStorageEnabled = false`
     * - `databaseEnabled = false`
     * - No JavaScript bridges (`addJavascriptInterface` is never called; default bridges removed)
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun applyRestrictiveSettings(
        webView: WebView,
        onPageStartedCallback: (() -> Unit)? = null,
        onPageFinishedCallback: (() -> Unit)? = null
    ) {
        JsSandboxExecutor.configureLockedDownWebView(webView)
        webView.settings.apply {
            javaScriptEnabled = true
            blockNetworkLoads = true
            blockNetworkImage = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            domStorageEnabled = false
            databaseEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            saveFormData = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            // Fit pages wider than the preview (boards, tables, fixed-width layouts)
            // to the available width instead of clipping them at the edge.
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onPageStartedCallback?.invoke()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinishedCallback?.invoke()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Block all navigation away from the inline sandboxed document
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()?.lowercase().orEmpty()
                if (url.isNotEmpty() && url != "about:blank" && !url.startsWith("data:")) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream("Blocked by sandbox".toByteArray(StandardCharsets.UTF_8))
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }
}

/**
 * Secured Android WebView component that takes HTML, CSS, and JS content as strings
 * and renders the combined document inside an isolated preview panel with restrictive settings:
 * - `blockNetworkLoads = true`
 * - `allowFileAccess = false`
 * - `allowContentAccess = false`
 * - No JavaScript bridges
 * - 5-second timeout (`timeoutMs`)
 * - 4 MB memory limit (`maxMemoryBytes`)
 */
@Composable
fun WebViewPreviewer(
    html: String,
    css: String = "",
    js: String = "",
    modifier: Modifier = Modifier,
    title: String = "Sandboxed HTML / CSS / JS Preview Panel",
    timeoutMs: Long = JsSandboxExecutor.MAX_EXECUTION_TIME_MS,
    maxMemoryBytes: Int = JsSandboxExecutor.MAX_MEMORY_BYTES,
    onTimeout: (() -> Unit)? = null,
    onMemoryLimitExceeded: (() -> Unit)? = null
) {
    val payload = remember(html, css, js, maxMemoryBytes) {
        WebViewPreviewerSecurity.preparePreviewPayload(
            html = html,
            css = css,
            js = js,
            maxMemoryBytes = maxMemoryBytes
        )
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember(payload.fullHtmlDocument) { mutableStateOf(!payload.isMemoryLimitExceeded) }
    var runtimeError by remember(payload.fullHtmlDocument) { mutableStateOf<String?>(payload.errorMessage) }

    LaunchedEffect(payload.isMemoryLimitExceeded) {
        if (payload.isMemoryLimitExceeded) {
            onMemoryLimitExceeded?.invoke()
        }
    }

    // Enforce the 5-second execution & render timeout and check in-page memory guard
    LaunchedEffect(payload.fullHtmlDocument, timeoutMs) {
        if (payload.isMemoryLimitExceeded || payload.fullHtmlDocument.isEmpty()) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(100L)
            val wv = webViewRef ?: continue
            wv.evaluateJavascript("(function(){ return Boolean(window.__sandboxMemoryExceeded); })();") { result ->
                if (result == "true" && runtimeError == null) {
                    runCatching {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                    }
                    runtimeError = "Sandbox Error: Memory limit exceeded (${maxMemoryBytes / (1024 * 1024)} MB maximum)."
                    onMemoryLimitExceeded?.invoke()
                }
            }
            if (!isPageLoading && runtimeError != null) {
                return@LaunchedEffect
            }
        }
        if (isPageLoading && runtimeError == null) {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.loadUrl("about:blank")
            }
            isPageLoading = false
            runtimeError = "Execution timed out after ${timeoutMs / 1000} seconds."
            onTimeout?.invoke()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.loadUrl("about:blank")
                webViewRef?.clearHistory()
                webViewRef?.removeAllViews()
                webViewRef?.destroy()
            }
            webViewRef = null
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("webview_previewer")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                BadgePill(
                    text = "NO NETWORK • NO BRIDGES",
                    color = EmeraldConnected
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            if (runtimeError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("webview_previewer_error"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = runtimeError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralError
                    )
                }
            } else {
                var lastLoadedDoc by remember { mutableStateOf<String?>(null) }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            WebViewPreviewerSecurity.applyRestrictiveSettings(
                                webView = this,
                                onPageStartedCallback = { isPageLoading = true },
                                onPageFinishedCallback = { isPageLoading = false }
                            )
                            webViewRef = this
                        }
                    },
                    update = { wv ->
                        if (lastLoadedDoc != payload.fullHtmlDocument) {
                            lastLoadedDoc = payload.fullHtmlDocument
                            isPageLoading = true
                            WebViewPreviewerSecurity.applyRestrictiveSettings(
                                webView = wv,
                                onPageStartedCallback = { isPageLoading = true },
                                onPageFinishedCallback = { isPageLoading = false }
                            )
                            wv.loadDataWithBaseURL(
                                "about:blank",
                                payload.fullHtmlDocument,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                        .testTag("webview_previewer_android_view")
                )
            }
        }
    }
}
