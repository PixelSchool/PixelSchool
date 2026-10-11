package com.pixelschool.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.pixelschool.app.data.model.UserSession

class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixel_school_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "key_auth_token"
        private const val KEY_VENDOR = "key_vendor"
        private const val KEY_LOGIN = "key_login"
        private const val KEY_USER_TITLE = "key_user_title"
        private const val KEY_SCHOOL_TITLE = "key_school_title"
        private const val KEY_IS_DEMO = "key_is_demo"
        private const val KEY_COOKIES = "key_cookies"
        private const val KEY_STUDENT_ID = "key_student_id"
    }

    fun saveSession(session: UserSession, cookies: String? = null) {
        prefs.edit().apply {
            putString(KEY_TOKEN, session.token)
            putString(KEY_VENDOR, session.vendor)
            putString(KEY_LOGIN, session.login)
            putString(KEY_USER_TITLE, session.userTitle)
            putString(KEY_SCHOOL_TITLE, session.schoolTitle)
            putBoolean(KEY_IS_DEMO, session.isDemo)
            putString(KEY_STUDENT_ID, session.studentId)
            if (cookies != null) {
                putString(KEY_COOKIES, cookies)
            }
            apply()
        }
    }

    fun getSession(): UserSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val isDemo = prefs.getBoolean(KEY_IS_DEMO, false)
        val vendor = prefs.getString(KEY_VENDOR, "edu") ?: "edu"

        // Auto-cleanup legacy corrupted sessions where vendor was set to "edu" for a real account
        if (!isDemo && vendor == "edu" && !token.startsWith("web_session")) {
            clear()
            return null
        }

        return UserSession(
            token = token,
            vendor = vendor,
            login = prefs.getString(KEY_LOGIN, "") ?: "",
            userTitle = prefs.getString(KEY_USER_TITLE, "") ?: "",
            schoolTitle = prefs.getString(KEY_SCHOOL_TITLE, "") ?: "",
            isDemo = isDemo,
            studentId = prefs.getString(KEY_STUDENT_ID, "") ?: ""
        )
    }

    fun getCookies(): String? = prefs.getString(KEY_COOKIES, null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
