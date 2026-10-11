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
        private const val KEY_FIRST_NAME = "key_first_name"
        private const val KEY_LAST_NAME = "key_last_name"
        private const val KEY_SCHOOL_NAME = "key_school_name"
    }

    fun saveSession(session: UserSession, cookies: String? = null) {
        val parts = session.userTitle.trim().split(Regex("""\s+"""))
        val derivedLast = if (session.lastName.isNotBlank()) session.lastName else parts.getOrNull(0) ?: ""
        val derivedFirst = if (session.firstName.isNotBlank()) session.firstName else parts.getOrNull(1) ?: ""
        val derivedSchool = if (session.schoolName.isNotBlank()) session.schoolName else session.schoolTitle

        prefs.edit().apply {
            putString(KEY_TOKEN, session.token)
            putString(KEY_VENDOR, session.vendor)
            putString(KEY_LOGIN, session.login)
            putString(KEY_USER_TITLE, session.userTitle)
            putString(KEY_SCHOOL_TITLE, session.schoolTitle)
            putBoolean(KEY_IS_DEMO, session.isDemo)
            putString(KEY_STUDENT_ID, session.studentId)
            putString(KEY_FIRST_NAME, derivedFirst)
            putString(KEY_LAST_NAME, derivedLast)
            putString(KEY_SCHOOL_NAME, derivedSchool)
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
        val rawUser = prefs.getString(KEY_USER_TITLE, "") ?: ""
        val rawSchool = prefs.getString(KEY_SCHOOL_TITLE, "") ?: ""
        val firstName = prefs.getString(KEY_FIRST_NAME, "") ?: ""
        val lastName = prefs.getString(KEY_LAST_NAME, "") ?: ""
        val schoolName = prefs.getString(KEY_SCHOOL_NAME, "") ?: ""

        val effectiveUser = if (rawUser.isNotBlank() && rawUser != "Ученик") {
            rawUser
        } else if (lastName.isNotBlank() && firstName.isNotBlank()) {
            "$lastName $firstName".trim()
        } else if (rawUser.isNotBlank()) {
            rawUser
        } else {
            "Ученик"
        }

        val effectiveSchool = if (rawSchool.isNotBlank() && rawSchool != "Республика Крым" && rawSchool != "ГИС РК «Образование»") {
            rawSchool
        } else if (schoolName.isNotBlank() && schoolName != "Республика Крым" && schoolName != "ГИС РК «Образование»") {
            schoolName
        } else if (rawSchool.isNotBlank()) {
            rawSchool
        } else {
            "Республика Крым"
        }

        return UserSession(
            token = token,
            vendor = vendor,
            login = prefs.getString(KEY_LOGIN, "") ?: "",
            userTitle = effectiveUser,
            schoolTitle = effectiveSchool,
            isDemo = isDemo,
            studentId = prefs.getString(KEY_STUDENT_ID, "") ?: "",
            firstName = firstName,
            lastName = lastName,
            schoolName = if (schoolName.isNotBlank()) schoolName else effectiveSchool
        )
    }

    fun getCookies(): String? = prefs.getString(KEY_COOKIES, null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
