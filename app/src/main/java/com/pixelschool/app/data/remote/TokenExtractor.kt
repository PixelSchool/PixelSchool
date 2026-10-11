package com.pixelschool.app.data.remote

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

object TokenExtractor {

    private val JWT_PARAM_NAMES = listOf("code", "token", "auth_token", "v_token", "access_token")
    private val JWT_REGEX = Regex("(?:code|token|auth_token|v_token|access_token)=([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?)")
    private val GENERIC_JWT_REGEX = Regex("(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}(?:\\.[A-Za-z0-9_-]+)?)")

    /**
     * Generic extractor that catches tokens from URL redirects:
     * - checks standard param names: code, token, auth_token, v_token, access_token
     * - detects any parameter starting with "eyJ" (JWT prefix)
     * - checks URL fragments
     * - applies regex fallback for encoded or complex URLs
     */
    fun extractToken(url: String?): String? {
        if (url.isNullOrBlank()) return null

        try {
            val uri = Uri.parse(url)

            // 1. Check known parameter names
            for (name in JWT_PARAM_NAMES) {
                val value = uri.getQueryParameter(name)
                if (!value.isNullOrBlank()) {
                    return value
                }
            }

            // 2. Scan all query parameters for any value starting with "eyJ" (universal JWT detection)
            for (name in uri.queryParameterNames) {
                val value = uri.getQueryParameter(name)
                if (value != null && value.startsWith("eyJ")) {
                    return value
                }
            }

            // 3. Scan URL fragment
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank()) {
                val parts = fragment.split("&")
                for (p in parts) {
                    val kv = p.split("=")
                    if (kv.size == 2) {
                        val k = kv[0]
                        val v = kv[1]
                        if (JWT_PARAM_NAMES.contains(k) || v.startsWith("eyJ")) {
                            return v
                        }
                    }
                }
            }

            // 4. Regex fallback on raw URL string
            val match = JWT_REGEX.find(url) ?: GENERIC_JWT_REGEX.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        } catch (_: Exception) {}

        return null
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
                    ?: json.optString("kid_fullname").ifBlank { null }

                if (!fullname.isNullOrBlank()) return fullname

                val sbj = json.optJSONObject("urn:esia:sbj")
                val sbjName = sbj?.optString("urn:esia:sbj:nam")?.ifBlank { null }
                if (!sbjName.isNullOrBlank()) return sbjName

                val sub = json.optString("sub").ifBlank { null }
                if (!sub.isNullOrBlank()) return sub

                null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun isJwtToken(token: String?): Boolean {
        return token != null && token.startsWith("eyJ") && token.contains(".")
    }
}
