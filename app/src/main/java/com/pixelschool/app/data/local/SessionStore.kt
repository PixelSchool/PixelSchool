package com.pixelschool.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.pixelschool.app.data.model.UserSession

class SessionStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val encPrefs = EncryptedSharedPreferences.create(
            "pixel_school_session_enc",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val oldPrefs = context.getSharedPreferences("pixel_school_session", Context.MODE_PRIVATE)
        if (oldPrefs.all.isNotEmpty() && encPrefs.all.isEmpty()) {
            val editor = encPrefs.edit()
            oldPrefs.all.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
            }
            editor.apply()
            oldPrefs.edit().clear().apply()
        }
        encPrefs
    } catch (e: Exception) {
        Log.w("SessionStore", "Failed to initialize EncryptedSharedPreferences, fallback to standard", e)
        context.getSharedPreferences("pixel_school_session", Context.MODE_PRIVATE)
    }

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

    private fun isGoodSchool(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        if (s == "Республика Крым" || s == "ГИС РК «Образование»") return false
        return s.contains("школ", ignoreCase = true) ||
               s.contains("гимназ", ignoreCase = true) ||
               s.contains("лицей", ignoreCase = true) ||
               s.contains("СОШ", ignoreCase = true) ||
               s.contains("МБОУ", ignoreCase = true) ||
               s.contains("МАОУ", ignoreCase = true)
    }

    fun saveSession(session: UserSession, cookies: String? = null) {
        val existingFirst = prefs.getString(KEY_FIRST_NAME, "") ?: ""
        val existingLast = prefs.getString(KEY_LAST_NAME, "") ?: ""
        val existingSchool = prefs.getString(KEY_SCHOOL_NAME, "") ?: ""
        val existingUserTitle = prefs.getString(KEY_USER_TITLE, "") ?: ""
        val existingSchoolTitle = prefs.getString(KEY_SCHOOL_TITLE, "") ?: ""

        val parts = session.userTitle.trim().split(Regex("""\s+"""))
        val derivedLast = if (session.lastName.isNotBlank()) {
            session.lastName
        } else if (parts.size >= 2 && session.userTitle != "Ученик") {
            parts.getOrNull(0) ?: ""
        } else {
            existingLast
        }

        val derivedFirst = if (session.firstName.isNotBlank()) {
            session.firstName
        } else if (parts.size >= 2 && session.userTitle != "Ученик") {
            parts.getOrNull(1) ?: ""
        } else {
            existingFirst
        }

        val derivedSchool = when {
            isGoodSchool(session.schoolName) -> session.schoolName
            isGoodSchool(session.schoolTitle) -> session.schoolTitle
            isGoodSchool(existingSchool) -> existingSchool
            session.schoolName.isNotBlank() && session.schoolName != "Республика Крым" && session.schoolName != "ГИС РК «Образование»" -> session.schoolName
            session.schoolTitle.isNotBlank() && session.schoolTitle != "Республика Крым" && session.schoolTitle != "ГИС РК «Образование»" -> session.schoolTitle
            existingSchool.isNotBlank() && existingSchool != "Республика Крым" && existingSchool != "ГИС РК «Образование»" -> existingSchool
            else -> session.schoolName.ifBlank { session.schoolTitle.ifBlank { "Республика Крым" } }
        }

        val effectiveUserTitle = if (session.userTitle.isNotBlank() && session.userTitle != "Ученик") {
            session.userTitle
        } else if (derivedLast.isNotBlank() && derivedFirst.isNotBlank()) {
            "$derivedLast $derivedFirst".trim()
        } else if (existingUserTitle.isNotBlank() && existingUserTitle != "Ученик") {
            existingUserTitle
        } else {
            session.userTitle.ifBlank { "Ученик" }
        }

        val effectiveSchoolTitle = if (session.schoolTitle.isNotBlank() && session.schoolTitle != "Республика Крым" && session.schoolTitle != "ГИС РК «Образование»") {
            session.schoolTitle
        } else if (derivedSchool.isNotBlank() && derivedSchool != "Республика Крым" && derivedSchool != "ГИС РК «Образование»") {
            derivedSchool
        } else if (existingSchoolTitle.isNotBlank() && existingSchoolTitle != "Республика Крым" && existingSchoolTitle != "ГИС РК «Образование»") {
            existingSchoolTitle
        } else {
            session.schoolTitle.ifBlank { "Республика Крым" }
        }

        prefs.edit().apply {
            putString(KEY_TOKEN, session.token)
            putString(KEY_VENDOR, session.vendor)
            putString(KEY_LOGIN, session.login)
            putString(KEY_USER_TITLE, effectiveUserTitle)
            putString(KEY_SCHOOL_TITLE, effectiveSchoolTitle)
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

        val effectiveSchool = when {
            isGoodSchool(schoolName) -> schoolName
            isGoodSchool(rawSchool) -> rawSchool
            schoolName.isNotBlank() && schoolName != "Республика Крым" && schoolName != "ГИС РК «Образование»" -> schoolName
            rawSchool.isNotBlank() && rawSchool != "Республика Крым" && rawSchool != "ГИС РК «Образование»" -> rawSchool
            rawSchool.isNotBlank() -> rawSchool
            schoolName.isNotBlank() -> schoolName
            else -> "Республика Крым"
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
