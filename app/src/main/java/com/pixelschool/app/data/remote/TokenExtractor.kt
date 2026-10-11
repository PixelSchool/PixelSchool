package com.pixelschool.app.data.remote

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

data class AuthCredentials(
    val vToken: String? = null,
    val token: String? = null,
    val vendor: String? = null,
    val login: String? = null
) {
    val isValid: Boolean
        get() = !vToken.isNullOrBlank() || (!token.isNullOrBlank() && !vendor.isNullOrBlank())
}

object TokenExtractor {

    /**
     * Extracts authentication parameters mirroring ru.eljur.region official app:
     * - checks "v_token"
     * - checks "token" + "vendor" + "login"
     * NOTE: Does NOT treat ESIA authorization "code" as final auth token,
     * allowing the web server to finish the exchange.
     */
    fun extractAuth(url: String?): AuthCredentials? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(url)

            val vToken = uri.getQueryParameter("v_token")
                ?: extractParamFromFragment(uri, "v_token")

            val token = uri.getQueryParameter("token")
                ?: uri.getQueryParameter("auth_token")
                ?: extractParamFromFragment(uri, "token")
                ?: extractParamFromFragment(uri, "auth_token")

            val vendor = uri.getQueryParameter("vendor")
                ?: extractParamFromFragment(uri, "vendor")
                ?: if (!token.isNullOrBlank()) "edu" else null

            val login = uri.getQueryParameter("login")
                ?: extractParamFromFragment(uri, "login")

            if (!vToken.isNullOrBlank()) {
                AuthCredentials(vToken = vToken)
            } else if (!token.isNullOrBlank()) {
                AuthCredentials(token = token, vendor = vendor ?: "edu", login = login)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts auth_token or token value from cookie header if present.
     */
    fun extractAuthTokenFromCookies(cookies: String?): String? {
        if (cookies.isNullOrBlank()) return null
        for (part in cookies.split(";")) {
            val trimmed = part.trim()
            if (trimmed.startsWith("auth_token=")) {
                val value = trimmed.substringAfter("auth_token=").trim()
                if (value.isNotBlank()) return value
            }
            if (trimmed.startsWith("token=")) {
                val value = trimmed.substringAfter("token=").trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    /**
     * Extracts PHPSESSID value from cookie header if present.
     */
    fun extractPhpsessid(cookies: String?): String? {
        if (cookies.isNullOrBlank()) return null
        for (part in cookies.split(";")) {
            val trimmed = part.trim()
            if (trimmed.startsWith("PHPSESSID=")) {
                val value = trimmed.substringAfter("PHPSESSID=").trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun extractParamFromFragment(uri: Uri, paramName: String): String? {
        val fragment = uri.fragment ?: return null
        return Uri.parse("?$fragment").getQueryParameter(paramName)
    }

    /**
     * Helper to check if URL is the ESIA callback step (code=...)
     * where we should allow the WebView to continue to edu.rk.gov.ru.
     */
    fun isEsiaCallbackCode(url: String?): Boolean {
        if (url == null) return false
        return url.contains("code=") && !url.contains("v_token=")
    }

    /**
     * Decodes JWT payload (without external libraries) and extracts user identifier or name.
     */
    fun extractUserTitleFromJwt(jwt: String?): String? {
        if (jwt == null || !jwt.startsWith("eyJ")) return null

        return try {
            val parts = jwt.split(".")
            if (parts.size >= 2) {
                var payloadStr = parts[1]
                val remainder = payloadStr.length % 4
                if (remainder > 0) {
                    payloadStr += "=".repeat(4 - remainder)
                }

                val bytes = Base64.decode(payloadStr, Base64.URL_SAFE or Base64.NO_WRAP)
                val json = JSONObject(String(bytes, Charsets.UTF_8))

                val fullname = json.optString("fullname").ifBlank { null }
                    ?: json.optString("name").ifBlank { null }
                    ?: json.optString("formatted_name").ifBlank { null }
                    ?: json.optString("kid_fullname").ifBlank { null }

                if (!fullname.isNullOrBlank()) return fullname

                val first = json.optString("first_name").ifBlank {
                    json.optString("given_name").ifBlank {
                        json.optString("firstname").ifBlank { null }
                    }
                }
                val last = json.optString("last_name").ifBlank {
                    json.optString("family_name").ifBlank {
                        json.optString("lastname").ifBlank { null }
                    }
                }
                if (!last.isNullOrBlank() && !first.isNullOrBlank()) {
                    return "$last $first".trim()
                }

                val sbj = json.optJSONObject("urn:esia:sbj")
                val sbjName = sbj?.optString("urn:esia:sbj:nam")?.ifBlank { null }
                if (!sbjName.isNullOrBlank()) return sbjName

                val sub = json.optString("sub").ifBlank { null }
                if (!sub.isNullOrBlank() && !sub.all { it.isDigit() }) return sub

                null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun extractSchoolFromJwt(jwt: String?): String? {
        if (jwt == null || !jwt.startsWith("eyJ")) return null
        return try {
            val parts = jwt.split(".")
            if (parts.size >= 2) {
                var payloadStr = parts[1]
                val remainder = payloadStr.length % 4
                if (remainder > 0) {
                    payloadStr += "=".repeat(4 - remainder)
                }
                val bytes = Base64.decode(payloadStr, Base64.URL_SAFE or Base64.NO_WRAP)
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                json.optString("school_title").ifBlank { null }
                    ?: json.optString("school").ifBlank { null }
                    ?: json.optString("org_name").ifBlank { null }
                    ?: json.optString("vendor_title").ifBlank { null }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

