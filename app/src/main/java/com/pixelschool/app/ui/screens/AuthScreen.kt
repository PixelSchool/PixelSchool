package com.pixelschool.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

@Composable
fun AuthScreen(
    onLoginDemo: () -> Unit,
    onLoginWithSession: (tokenOrVToken: String?, cookies: String?, userName: String?) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    var showWebViewDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "PixelSchool",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Школьный дневник Республики Крым в выразительном Material 3 дизайне",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                Button(
                    onClick = { showWebViewDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Войти через Госуслуги",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onLoginDemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "Попробовать демо-режим",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showWebViewDialog) {
        GosuslugiAuthDialog(
            onDismiss = { showWebViewDialog = false },
            onAuthCompleted = { token, cookies, userName ->
                showWebViewDialog = false
                onLoginWithSession(token, cookies, userName)
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GosuslugiAuthDialog(
    onDismiss: () -> Unit,
    onAuthCompleted: (tokenOrVToken: String?, cookies: String?, userName: String?) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertAlignment,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Закрыть")
                    }

                    Text(
                        text = "Вход через Госуслуги",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Manual confirmation button if user sees diary already loaded in WebView
                    FilledTonalButton(
                        onClick = {
                            val cookies = CookieManager.getInstance().getCookie("https://edu.rk.gov.ru")
                            onAuthCompleted("web_session", cookies, null)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Готово", style = MaterialTheme.typography.labelMedium)
                    }
                }

                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            fun checkAndCapture(url: String?) {
                                if (url == null) return

                                val isTargetDiary = url.contains("/journal-app") ||
                                        url.contains("/journal-student") ||
                                        url.contains("/journal-") ||
                                        (url.contains("edu.rk.gov.ru") && !url.contains("/authorize") && !url.contains("/journal-esia-action") && !url.contains("esia.gosuslugi.ru"))

                                val hasVToken = url.contains("v_token=")

                                if (isTargetDiary || hasVToken) {
                                    val vToken = if (hasVToken) url.substringAfter("v_token=").substringBefore("&") else null
                                    val cookies = cookieManager.getCookie("https://edu.rk.gov.ru")

                                    // Extract any token or user name from page JS
                                    evaluateJavascript(
                                        """
                                        (function() {
                                            var t = '';
                                            try {
                                                t = localStorage.getItem('token') || localStorage.getItem('auth_token') || sessionStorage.getItem('token') || '';
                                            } catch(e) {}
                                            var u = '';
                                            try {
                                                var el = document.querySelector('.user-name, .b-dropdown__user, .header-user__name, .m-user__name');
                                                if (el) u = el.innerText.trim();
                                            } catch(e) {}
                                            return JSON.stringify({ token: t, user: u });
                                        })()
                                        """.trimIndent()
                                    ) { jsResult ->
                                        var jsToken: String? = null
                                        var jsUser: String? = null
                                        try {
                                            if (!jsResult.isNullOrBlank() && jsResult != "null") {
                                                val clean = if (jsResult.startsWith("\"") && jsResult.endsWith("\"")) {
                                                    JSONObject(JSONObject("{\"d\":$jsResult}").getString("d"))
                                                } else {
                                                    JSONObject(jsResult)
                                                }
                                                jsToken = clean.optString("token").ifEmpty { null }
                                                jsUser = clean.optString("user").ifEmpty { null }
                                            }
                                        } catch (_: Exception) {}

                                        val resolvedToken = jsToken ?: vToken ?: "web_session"
                                        onAuthCompleted(resolvedToken, cookies, jsUser)
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val u = request?.url?.toString()
                                    checkAndCapture(u)
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    checkAndCapture(url)
                                }
                            }

                            loadUrl("https://edu.rk.gov.ru/journal-esia-action/")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
