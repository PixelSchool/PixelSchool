package com.pixelschool.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import com.pixelschool.app.data.remote.AuthCredentials
import com.pixelschool.app.data.remote.TokenExtractor

@Composable
fun AuthScreen(
    onLoginDemo: () -> Unit,
    onLoginWithSession: (credentials: AuthCredentials?, cookies: String?, userName: String?) -> Unit,
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
            onAuthCompleted = { credentials, cookies, userName ->
                showWebViewDialog = false
                onLoginWithSession(credentials, cookies, userName)
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GosuslugiAuthDialog(
    onDismiss: () -> Unit,
    onAuthCompleted: (credentials: AuthCredentials?, cookies: String?, userName: String?) -> Unit
) {
    var capturedAuth by remember { mutableStateOf<AuthCredentials?>(null) }
    var capturedUserName by remember { mutableStateOf<String?>(null) }
    var hasCompleted by remember { mutableStateOf(false) }

    fun getSessionCookies(): String {
        val cm = CookieManager.getInstance()
        cm.flush()
        val domains = listOf(
            "https://edu.rk.gov.ru",
            "https://edu.rk.gov.ru/",
            "http://edu.rk.gov.ru",
            "edu.rk.gov.ru",
            ".edu.rk.gov.ru",
            ".rk.gov.ru"
        )
        val set = LinkedHashSet<String>()
        for (d in domains) {
            val c = cm.getCookie(d)
            if (!c.isNullOrBlank()) {
                c.split(";").forEach { cookie ->
                    val trimmed = cookie.trim()
                    if (trimmed.isNotEmpty()) set.add(trimmed)
                }
            }
        }
        return set.joinToString("; ")
    }

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
                    verticalAlignment = Alignment.CenterVertically,
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
                            if (!hasCompleted) {
                                hasCompleted = true
                                val cookies = getSessionCookies()
                                val auth = capturedAuth ?: if (cookies.isNotBlank()) {
                                    AuthCredentials(token = "web_session", vendor = "edu")
                                } else null
                                onAuthCompleted(auth, cookies, capturedUserName)
                            }
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
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            // Strip Android WebView markers matching ru.eljur.region official app
                            val currentUa = settings.userAgentString
                            settings.userAgentString = currentUa
                                .replace(Regex("(Version.*\\s)Chrome"), "Chrome")
                                .replace(Regex(";\\s+wv"), "")

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            fun checkAndCapture(url: String?, isPageFinished: Boolean) {
                                if (url == null || hasCompleted) return

                                try {
                                    // 1. Check if URL contains official v_token or direct (token, vendor, login)
                                    val auth = TokenExtractor.extractAuth(url)
                                    if (auth != null && auth.isValid) {
                                        capturedAuth = auth
                                        hasCompleted = true
                                        val cookies = getSessionCookies()
                                        onAuthCompleted(auth, cookies, capturedUserName)
                                        return
                                    }

                                    // 2. If this is intermediate ESIA authorization callback (code=...),
                                    // DO NOT stop or capture; allow edu.rk.gov.ru to exchange it on server!
                                    if (TokenExtractor.isEsiaCallbackCode(url)) {
                                        return
                                    }

                                    // 3. Detect journal web interface on page finished
                                    val isTargetDiary = url.contains("/journal-app") ||
                                            url.contains("/journal-student") ||
                                            (url.contains("edu.rk.gov.ru") && !url.contains("/authorize") &&
                                             !url.contains("esia.gosuslugi.ru") && !url.contains("flow=v2"))

                                    if (isPageFinished && isTargetDiary) {
                                        val cookies = getSessionCookies()

                                        evaluateJavascript(
                                            """
                                            (function() {
                                                var t = '';
                                                var v = '';
                                                try {
                                                    t = localStorage.getItem('token') || localStorage.getItem('auth_token') || sessionStorage.getItem('token') || '';
                                                    v = localStorage.getItem('vendor') || sessionStorage.getItem('vendor') || '';
                                                } catch(e) {}
                                                var u = '';
                                                try {
                                                    var el = document.querySelector('.user-name, .b-dropdown__user, .header-user__name, .m-user__name');
                                                    if (el) u = el.innerText.trim();
                                                } catch(e) {}
                                                return JSON.stringify({ token: t, vendor: v, user: u });
                                            })()
                                            """.trimIndent()
                                        ) { jsResult ->
                                            if (hasCompleted) return@evaluateJavascript

                                            var jsToken: String? = null
                                            var jsVendor: String? = null
                                            var jsUser: String? = null
                                            try {
                                                if (!jsResult.isNullOrBlank() && jsResult != "null") {
                                                    val clean = if (jsResult.startsWith("\"") && jsResult.endsWith("\"")) {
                                                        JSONObject(JSONObject("{\"d\":$jsResult}").getString("d"))
                                                    } else {
                                                        JSONObject(jsResult)
                                                    }
                                                    jsToken = clean.optString("token").ifEmpty { null }
                                                    jsVendor = clean.optString("vendor").ifEmpty { null }
                                                    jsUser = clean.optString("user").ifEmpty { null }
                                                }
                                            } catch (_: Exception) {}

                                            val finalAuth = capturedAuth
                                                ?: if (!jsToken.isNullOrBlank() && !jsVendor.isNullOrBlank()) {
                                                    AuthCredentials(token = jsToken, vendor = jsVendor)
                                                } else if (cookies.isNotBlank()) {
                                                    AuthCredentials(token = "web_session", vendor = "edu")
                                                } else null

                                            val finalUser = jsUser ?: capturedUserName

                                            if (finalAuth != null) {
                                                hasCompleted = true
                                                onAuthCompleted(finalAuth, cookies, finalUser)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val u = request?.url?.toString()
                                    checkAndCapture(u, isPageFinished = false)
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    checkAndCapture(url, isPageFinished = false)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    checkAndCapture(url, isPageFinished = true)
                                }
                            }

                            // Official flow=v2 used by ru.eljur.region
                            loadUrl("https://edu.rk.gov.ru/journal-esia-region-action?flow=v2")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
