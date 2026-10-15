package com.pixelschool.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.pixelschool.app.data.model.UserSession

class SessionStore(context: Context) {

    private val backupPrefs: SharedPreferences =
        context.getSharedPreferences("pixel_school_session", Context.MODE_PRIVATE)

    private val encPrefs: SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "pixel_school_session_enc",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w("SessionStore", "EncryptedSharedPreferences init failed, using backup prefs", e)
        null
    }

    private fun getSafeString(key: String, defValue: String? = null): String? {
        if (encPrefs != null) {
            try {
                val value = encPrefs.getString(key, null)
                if (value != null) return value
            } catch (e: Exception) {
                Log.w("SessionStore", "Failed to read encrypted key $key, using backup", e)
            }
        }
        return backupPrefs.getString(key, defValue)
    }

    private fun getSafeBoolean(key: String, defValue: Boolean = false): Boolean {
        if (encPrefs != null) {
            try {
                return encPrefs.getBoolean(key, defValue)
            } catch (e: Exception) {
                Log.w("SessionStore", "Failed to read encrypted boolean $key, using backup", e)
            }
        }
        return backupPrefs.getBoolean(key, defValue)
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
        val existingFirst = getSafeString(KEY_FIRST_NAME, "") ?: ""
        val existingLast = getSafeString(KEY_LAST_NAME, "") ?: ""
        val existingSchool = getSafeString(KEY_SCHOOL_NAME, "") ?: ""
        val existingUserTitle = getSafeString(KEY_USER_TITLE, "") ?: ""
        val existingSchoolTitle = getSafeString(KEY_SCHOOL_TITLE, "") ?: ""

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

        val saveToEditor: (SharedPreferences.Editor) -> Unit = { editor ->
            editor.putString(KEY_TOKEN, session.token)
            editor.putString(KEY_VENDOR, session.vendor)
            editor.putString(KEY_LOGIN, session.login)
            editor.putString(KEY_USER_TITLE, effectiveUserTitle)
            editor.putString(KEY_SCHOOL_TITLE, effectiveSchoolTitle)
            editor.putBoolean(KEY_IS_DEMO, session.isDemo)
            editor.putString(KEY_STUDENT_ID, session.studentId)
            editor.putString(KEY_FIRST_NAME, derivedFirst)
            editor.putString(KEY_LAST_NAME, derivedLast)
            editor.putString(KEY_SCHOOL_NAME, derivedSchool)
            if (cookies != null) {
                editor.putString(KEY_COOKIES, cookies)
            }
            editor.apply()
        }

        saveToEditor(backupPrefs.edit())
        encPrefs?.let {
            try {
                saveToEditor(it.edit())
            } catch (e: Exception) {
                Log.w("SessionStore", "Failed to write session to encrypted prefs", e)
            }
        }
    }

    fun getSession(): UserSession? {
        val token = getSafeString(KEY_TOKEN) ?: return null
        val isDemo = getSafeBoolean(KEY_IS_DEMO, false)
        val vendor = getSafeString(KEY_VENDOR, "edu") ?: "edu"
        val rawUser = getSafeString(KEY_USER_TITLE, "") ?: ""
        val rawSchool = getSafeString(KEY_SCHOOL_TITLE, "") ?: ""
        val firstName = getSafeString(KEY_FIRST_NAME, "") ?: ""
        val lastName = getSafeString(KEY_LAST_NAME, "") ?: ""
        val schoolName = getSafeString(KEY_SCHOOL_NAME, "") ?: ""

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
            login = getSafeString(KEY_LOGIN, "") ?: "",
            userTitle = effectiveUser,
            schoolTitle = effectiveSchool,
            isDemo = isDemo,
            studentId = getSafeString(KEY_STUDENT_ID, "") ?: "",
            firstName = firstName,
            lastName = lastName,
            schoolName = if (schoolName.isNotBlank()) schoolName else effectiveSchool
        )
    }

    fun getCookies(): String? = getSafeString(KEY_COOKIES)

    fun clear() {
        backupPrefs.edit().clear().apply()
        encPrefs?.let {
            try {
                it.edit().clear().apply()
            } catch (e: Exception) {
                Log.w("SessionStore", "Failed to clear encrypted prefs", e)
            }
        }
    }
}
