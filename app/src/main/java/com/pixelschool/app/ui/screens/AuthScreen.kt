package com.pixelschool.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
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
    onLoginWithSession: (credentials: AuthCredentials?, cookies: String?, userName: String?, schoolName: String?) -> Unit,
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
            onAuthCompleted = { credentials, cookies, userName, schoolName ->
                showWebViewDialog = false
                onLoginWithSession(credentials, cookies, userName, schoolName)
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GosuslugiAuthDialog(
    onDismiss: () -> Unit,
    onAuthCompleted: (credentials: AuthCredentials?, cookies: String?, userName: String?, schoolName: String?) -> Unit
) {
    var capturedAuth by remember { mutableStateOf<AuthCredentials?>(null) }
    var capturedUserName by remember { mutableStateOf<String?>(null) }
    var capturedSchoolName by remember { mutableStateOf<String?>(null) }
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
                                val cookieToken = TokenExtractor.extractAuthTokenFromCookies(cookies)
                                val auth = capturedAuth ?: if (!cookieToken.isNullOrBlank()) {
                                    AuthCredentials(token = cookieToken, vendor = "edu")
                                } else if (cookies.isNotBlank()) {
                                    AuthCredentials(token = "web_session", vendor = "edu")
                                } else null
                                val jwtUser = TokenExtractor.extractUserTitleFromJwt(auth?.token ?: cookieToken)
                                val jwtSchool = TokenExtractor.extractSchoolFromJwt(auth?.token ?: cookieToken)
                                val uName = capturedUserName ?: jwtUser
                                val sName = capturedSchoolName ?: jwtSchool
                                onAuthCompleted(auth, cookies, uName, sName)
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

                            // Modern Pixel 8 / Chrome 128 mobile emulation matching guidelines
                            val chromeUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            settings.userAgentString = chromeUserAgent

                            val chromeHeaders = mapOf(
                                "User-Agent" to chromeUserAgent,
                                "sec-ch-ua" to "\"Chromium\";v=\"128\", \"Google Chrome\";v=\"128\", \"Not?A_Brand\";v=\"99\"",
                                "sec-ch-ua-mobile" to "?1",
                                "sec-ch-ua-platform" to "\"Android\"",
                                "Accept-Language" to "ru-RU,ru;q=0.9",
                                "Referer" to "https://edu.rk.gov.ru/?show=home"
                            )

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
                                        val jwtUser = TokenExtractor.extractUserTitleFromJwt(auth.token)
                                        val jwtSchool = TokenExtractor.extractSchoolFromJwt(auth.token)
                                        onAuthCompleted(auth, cookies, capturedUserName ?: jwtUser, capturedSchoolName ?: jwtSchool)
                                        return
                                    }

                                    // 2. If this is intermediate ESIA authorization callback (code=...),
                                    // DO NOT stop or capture; allow edu.rk.gov.ru to exchange it on server!
                                    if (TokenExtractor.isEsiaCallbackCode(url)) {
                                        return
                                    }

                                    // 3. Detect journal web interface on redirect or page load
                                    val isTargetDiary = url.contains("?show=home") ||
                                            url.contains("/journal-app") ||
                                            url.contains("/journal-student") ||
                                            (url.contains("edu.rk.gov.ru") && !url.contains("/authorize") &&
                                             !url.contains("esia.gosuslugi.ru") && !url.contains("flow=v2") &&
                                             !url.contains("/journal-esia"))

                                    if (isTargetDiary) {
                                        val cookies = getSessionCookies()
                                        val phpsessid = TokenExtractor.extractPhpsessid(cookies)
                                        val cookieToken = TokenExtractor.extractAuthTokenFromCookies(cookies)

                                        if (!phpsessid.isNullOrBlank() || !cookieToken.isNullOrBlank() || isPageFinished) {
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
                                                        if (window.gon && window.gon.user) {
                                                            u = window.gon.user.name || window.gon.user.title || '';
                                                        }
                                                        if (!u) {
                                                            var el = document.querySelector('.user-name, .b-dropdown__user, .header-user__name, .m-user__name, .user_name, .user-profile__name, .student-name, .b-header__user, .user__name, .h-user, .user-title, div.user-info .name, span.user-name');
                                                            if (el) u = el.innerText.trim();
                                                        }
                                                    } catch(e) {}
                                                    var s = '';
                                                    try {
                                                        if (window.gon && window.gon.school) {
                                                            s = window.gon.school.title || window.gon.school.name || '';
                                                        }
                                                        if (!s) {
                                                            var sel = document.querySelector('.school-title, .header-school__title, .m-logo__subtitle, .school_title, .school-name, .b-dropdown__school, .b-school__title, .school, .school__title, .school__name, div.school-info, .header__school');
                                                            if (sel) s = sel.innerText.trim();
                                                        }
                                                    } catch(e) {}
                                                    return JSON.stringify({ token: t, vendor: v, user: u, school: s });
                                                })()
                                                """.trimIndent()
                                            ) { jsResult ->
                                                if (hasCompleted) return@evaluateJavascript

                                                var jsToken: String? = null
                                                var jsVendor: String? = null
                                                var jsUser: String? = null
                                                var jsSchool: String? = null
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
                                                        jsSchool = clean.optString("school").ifEmpty { null }
                                                    }
                                                } catch (_: Exception) {}

                                                val finalAuth = capturedAuth
                                                    ?: if (!jsToken.isNullOrBlank()) {
                                                        AuthCredentials(token = jsToken, vendor = jsVendor ?: "edu")
                                                    } else if (!cookieToken.isNullOrBlank()) {
                                                        AuthCredentials(token = cookieToken, vendor = "edu")
                                                    } else if (cookies.isNotBlank()) {
                                                        AuthCredentials(token = "web_session", vendor = "edu")
                                                    } else null

                                                val jwtUser = TokenExtractor.extractUserTitleFromJwt(finalAuth?.token ?: cookieToken)
                                                val jwtSchool = TokenExtractor.extractSchoolFromJwt(finalAuth?.token ?: cookieToken)
                                                val finalUser = jsUser ?: capturedUserName ?: jwtUser
                                                val finalSchool = jsSchool ?: capturedSchoolName ?: jwtSchool

                                                if (finalAuth != null && (!phpsessid.isNullOrBlank() || !cookieToken.isNullOrBlank() || isPageFinished)) {
                                                    hasCompleted = true
                                                    onAuthCompleted(finalAuth, cookies, finalUser, finalSchool)
                                                }
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

                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    val failingUrl = error?.url.orEmpty()
                                    // Bypass SSL errors for Russian Root CA / regional portal domains
                                    if (failingUrl.contains("rk.gov.ru") ||
                                        failingUrl.contains("gosuslugi.ru") ||
                                        failingUrl.contains("eljur.ru") ||
                                        failingUrl.contains("gu-st.ru")
                                    ) {
                                        handler?.proceed()
                                    } else {
                                        super.onReceivedSslError(view, handler, error)
                                    }
                                }
                            }

                            // Regional Crimean portal ESIA authorization entrypoint
                            loadUrl("https://edu.rk.gov.ru/journal-esia-action/", chromeHeaders)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
