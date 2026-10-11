package com.pixelschool.app.data.remote

import android.net.Uri
import android.util.Log
import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.Attachment
import com.pixelschool.app.data.model.AttachmentType
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Lesson
import com.pixelschool.app.data.model.Mark
import com.pixelschool.app.data.model.PeriodMarks
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SessionExpiredException(message: String = "Сессия устарела. Требуется повторный вход.") : Exception(message)

class EljurApiClient(
    private val client: OkHttpClient = RussianTrustManager.createOkHttpClient()
) {

    private val baseUrl = "https://edu.rk.gov.ru"
    // Production devkey extracted from ru.eljur.region official app (sources/g5/d.java)
    private val devKey = "7e3f778541471e910a9021a4beb35d5d"
    // Fallback demo/test devkey from b9/a.java
    private val fallbackDevKey = "19c4bfc2705023fe080ce94ace26aec9"
    private val devKeys = listOf(devKey, fallbackDevKey)

    private val browserUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    companion object {
        private const val TAG = "PixelSchoolNetwork"
    }

    fun checkSessionValidity(response: okhttp3.Response, body: String?) {
        val finalUrl = response.request.url.toString()
        if (finalUrl.contains("/authorize") ||
            finalUrl.contains("/journal-esia-action") ||
            finalUrl.contains("esia.gosuslugi.ru") ||
            finalUrl.contains("/login")
        ) {
            throw SessionExpiredException()
        }

        if (!body.isNullOrBlank()) {
            val isLoginPage = body.contains("<form action=\"/authorize\"") ||
                    body.contains("form action=\"https://edu.rk.gov.ru/authorize\"") ||
                    (body.contains("name=\"login\"") && body.contains("name=\"password\"")) ||
                    body.contains("class=\"login-form\"") ||
                    body.contains("id=\"login_form\"") ||
                    body.contains("Для доступа к этой странице необходимо авторизоваться") ||
                    (body.contains("<title>Авторизация") || body.contains("<title>Вход в систему"))

            if (isLoginPage) {
                throw SessionExpiredException()
            }
        }
    }

    fun buildRequest(url: String, cookies: String? = null, isJson: Boolean = true): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", browserUserAgent)
            .header("sec-ch-ua", "\"Chromium\";v=\"128\", \"Google Chrome\";v=\"128\", \"Not?A_Brand\";v=\"99\"")
            .header("sec-ch-ua-mobile", "?1")
            .header("sec-ch-ua-platform", "\"Android\"")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .header("Referer", "https://edu.rk.gov.ru/?show=home")
            .header("Accept", if (isJson) "application/json, text/plain, */*" else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

        if (!cookies.isNullOrBlank()) {
            builder.header("Cookie", cookies)
        }
        val request = builder.build()
        logRequest(request)
        return request
    }

    private fun logRequest(req: Request) {
        val maskedCookies = req.header("Cookie")?.let { "[cookies present, len=${it.length}]" } ?: "none"
        val query = req.url.query ?: "none"
        Log.d(TAG, "--> ${req.method} ${req.url.scheme}://${req.url.host}${req.url.encodedPath} (query: $query, cookies: $maskedCookies)")
    }

    private fun logResponseError(req: Request, resp: okhttp3.Response, body: String?) {
        val contentType = resp.header("Content-Type") ?: "unknown"
        val query = req.url.query ?: "none"
        val headers = req.headers.names().joinToString(", ") { name ->
            if (name.equals("Cookie", ignoreCase = true)) "Cookie=[MASKED]" else "$name=${req.header(name)}"
        }
        val preview = body?.take(300)?.replace("\n", " ") ?: ""
        Log.e(
            TAG,
            "<-- HTTP ${resp.code} for ${req.method} ${req.url}\n" +
            "    Host: ${req.url.host}, Path: ${req.url.encodedPath}\n" +
            "    Query: $query\n" +
            "    Request Headers: [$headers]\n" +
            "    Response Content-Type: $contentType\n" +
            "    Body Preview: $preview"
        )
    }

    fun parseJsonResponse(response: okhttp3.Response, body: String?, request: Request): JSONObject? {
        if (body.isNullOrBlank()) return null
        val contentType = response.header("Content-Type")?.lowercase() ?: ""
        val trimmed = body.trimStart()
        val isJson = contentType.contains("application/json") || trimmed.startsWith("{") || trimmed.startsWith("[")

        if (!isJson) {
            val preview = body.take(200).replace("\n", " ")
            Log.w(
                TAG,
                "Non-JSON response received from ${request.url}. " +
                "HTTP ${response.code}, Content-Type: '$contentType', Preview: $preview"
            )
            return null
        }

        return try {
            JSONObject(body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON from ${request.url}: ${e.message}")
            null
        }
    }

    fun getBaseUrls(vendor: String = "edu"): List<String> {
        // Exclusively regional gateway: eliminate non-existent api.eljur.ru to avoid DNS UnknownHostException
        return listOf("https://edu.rk.gov.ru")
    }


    private fun extractUserVendor(json: JSONObject, fallbackUserName: String?): UserSession? {
        val respObj = json.optJSONObject("response") ?: json

        // 1. Array representation
        val arr = respObj.optJSONArray("result")
            ?: respObj.optJSONArray("users")
            ?: json.optJSONArray("result")
        if (arr != null && arr.length() > 0) {
            val firstUser = arr.getJSONObject(0)
            val realToken = firstUser.optString("token", "")
            val realVendor = firstUser.optString("vendor", "")
            if (realToken.isNotBlank() && realVendor.isNotBlank()) {
                return UserSession(
                    token = realToken,
                    vendor = realVendor,
                    login = firstUser.optString("login", ""),
                    userTitle = firstUser.optString("user_title", fallbackUserName ?: "Ученик"),
                    schoolTitle = firstUser.optString("vendor_title", "ГИС РК «Образование»")
                )
            }
        }

        // 2. Object map representation (e.g., {"123": {...}})
        val obj = respObj.optJSONObject("result") ?: respObj.optJSONObject("users")
        if (obj != null) {
            for (key in obj.keys()) {
                val user = obj.optJSONObject(key)
                if (user != null) {
                    val realToken = user.optString("token", "")
                    val realVendor = user.optString("vendor", "")
                    if (realToken.isNotBlank() && realVendor.isNotBlank()) {
                        return UserSession(
                            token = realToken,
                            vendor = realVendor,
                            login = user.optString("login", ""),
                            userTitle = user.optString("user_title", fallbackUserName ?: "Ученик"),
                            schoolTitle = user.optString("vendor_title", "ГИС РК «Образование»")
                        )
                    }
                }
            }
        }
        return null
    }

    fun enrichWithRules(session: UserSession, cookies: String? = null): UserSession {
        val vendorParam = if (session.vendor.isNotBlank()) "&vendor=${session.vendor}" else "&vendor=edu"
        val cookieToken = TokenExtractor.extractAuthTokenFromCookies(cookies)
        val effectiveToken = if (session.token.isNotBlank() && session.token != "web_session") {
            session.token
        } else if (!cookieToken.isNullOrBlank()) {
            cookieToken
        } else ""

        val tokenParam = if (effectiveToken.isNotBlank()) "&auth_token=$effectiveToken" else ""
        val baseUrls = getBaseUrls(session.vendor)

        var currentSession = session

        for (base in baseUrls) {
            for (dk in devKeys) {
                try {
                    val url = "$base/apiv3/getrules?devkey=$dk&out_format=json$tokenParam$vendorParam"
                    val req = buildRequest(url, cookies)
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string()
                    val json = if (resp.isSuccessful) parseJsonResponse(resp, body, req) else {
                        logResponseError(req, resp, body)
                        null
                    }
                    if (json != null) {
                        val respObj = json.optJSONObject("response") ?: json
                        if (respObj.optInt("state", 200) == 200) {
                            val result = respObj.optJSONObject("result") ?: respObj
                            val relations = result.optJSONObject("relations")

                            val studentsObj = relations?.optJSONObject("students") ?: result.optJSONObject("students")
                            val studentsArr = relations?.optJSONArray("students") ?: result.optJSONArray("students")

                            var realStudentId = ""
                            var studentTitle: String? = null

                            if (studentsObj != null && studentsObj.length() > 0) {
                                val firstKey = studentsObj.keys().asSequence().firstOrNull()
                                val st = if (firstKey != null) studentsObj.optJSONObject(firstKey) else null
                                realStudentId = st?.optString("id")?.ifBlank { null }
                                    ?: st?.optString("student_id")?.ifBlank { null }
                                    ?: st?.optString("child_id")?.ifBlank { null }
                                    ?: st?.optString("name")?.ifBlank { null }
                                    ?: firstKey ?: ""
                                studentTitle = st?.optString("title")?.ifBlank { null }
                                    ?: st?.optString("name")?.ifBlank { null }
                            } else if (studentsArr != null && studentsArr.length() > 0) {
                                val st = studentsArr.optJSONObject(0)
                                realStudentId = st?.optString("id")?.ifBlank { null }
                                    ?: st?.optString("student_id")?.ifBlank { null }
                                    ?: st?.optString("child_id")?.ifBlank { null }
                                    ?: st?.optString("name")?.ifBlank { null }
                                    ?: ""
                                studentTitle = st?.optString("title")?.ifBlank { null }
                            }

                            if (realStudentId.isBlank()) {
                                realStudentId = result.optString("student_id").ifBlank { null }
                                    ?: result.optString("child_id").ifBlank { null }
                                    ?: result.optString("id").ifBlank { null }
                                    ?: result.optString("name").ifBlank { null }
                                    ?: result.optString("guid").ifBlank { null }
                                    ?: ""
                            }

                            if (studentTitle.isNullOrBlank()) {
                                studentTitle = result.optString("title").ifBlank { null }
                                    ?: currentSession.userTitle
                            }

                            val realSchool = result.optString("school").ifBlank { null }
                                ?: result.optString("vendor_title").ifBlank { null }
                                ?: currentSession.schoolTitle

                            val realVendor = result.optString("vendor").ifBlank { currentSession.vendor }

                            val uploadUrl = result.optString("uploadFileUrl").ifBlank {
                                result.optString("upload_file_url").ifBlank { null }
                            }
                            var foundToken = result.optString("token").ifBlank { null }
                            if (foundToken.isNullOrBlank() && uploadUrl != null) {
                                try {
                                    foundToken = Uri.parse(uploadUrl).getQueryParameter("token")
                                } catch (_: Exception) {}
                            }
                            val finalToken = if (!foundToken.isNullOrBlank()) foundToken else currentSession.token

                            currentSession = currentSession.copy(
                                token = finalToken,
                                vendor = realVendor,
                                studentId = realStudentId,
                                userTitle = studentTitle,
                                schoolTitle = realSchool
                            )

                            if (realStudentId.isNotBlank()) {
                                return currentSession
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Try getprofileuserinfo across base URLs if studentId is still not found
        for (base in baseUrls) {
            for (dk in devKeys) {
                try {
                    val url = "$base/apiv3/getprofileuserinfo?devkey=$dk&out_format=json$tokenParam$vendorParam"
                    val req = buildRequest(url, cookies)
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string()
                    val json = if (resp.isSuccessful) parseJsonResponse(resp, body, req) else {
                        logResponseError(req, resp, body)
                        null
                    }
                    if (json != null) {
                        val respObj = json.optJSONObject("response") ?: json
                        if (respObj.optInt("state", 200) == 200) {
                            val result = respObj.optJSONObject("result") ?: respObj
                            val profile = result.optJSONObject("profileUserInfo") ?: result
                            val stId = profile.optString("studentID").ifBlank {
                                profile.optString("student_id").ifBlank {
                                    profile.optString("userID").ifBlank { null }
                                }
                            }
                            val userName = profile.optString("userName").ifBlank { null }
                            val schoolName = profile.optString("schoolName").ifBlank { null }
                            if (!stId.isNullOrBlank() || !userName.isNullOrBlank()) {
                                return currentSession.copy(
                                    studentId = stId ?: currentSession.studentId,
                                    userTitle = userName ?: currentSession.userTitle,
                                    schoolTitle = schoolName ?: currentSession.schoolTitle
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return currentSession
    }

    /**
     * Resolves user session from AuthCredentials (v_token or direct token+vendor) or web cookies.
     */
    suspend fun resolveSession(
        auth: AuthCredentials?,
        cookies: String?,
        fallbackUserName: String? = null
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        try {
            val cookieToken = TokenExtractor.extractAuthTokenFromCookies(cookies)
            val baseUrls = getBaseUrls(auth?.vendor ?: "edu")

            // Flow 1: If v_token is provided (standard official flow=v2), call getusersvendors
            val vToken = auth?.vToken
            if (!vToken.isNullOrBlank()) {
                for (base in baseUrls) {
                    for (dk in devKeys) {
                        val url = "$base/apiv3/getusersvendors?devkey=$dk&out_format=json&v_token=$vToken"
                        val req = buildRequest(url, cookies)

                        try {
                            val resp = client.newCall(req).execute()
                            val body = resp.body?.string()
                            val json = if (resp.isSuccessful) parseJsonResponse(resp, body, req) else {
                                logResponseError(req, resp, body)
                                null
                            }
                            if (json != null) {
                                val session = extractUserVendor(json, fallbackUserName)
                                if (session != null) {
                                    val enriched = enrichWithRules(session, cookies)
                                    return@withContext Result.success(enriched)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // Flow 2: Direct token intercepted from URL redirect or cookies
            val directToken = auth?.token ?: cookieToken
            val directVendor = auth?.vendor ?: "edu"
            if (!directToken.isNullOrBlank() && directToken != "web_session") {
                val baseSession = UserSession(
                    token = directToken,
                    vendor = directVendor,
                    login = auth?.login ?: "",
                    userTitle = fallbackUserName ?: "Ученик",
                    schoolTitle = "ГИС РК «Образование»"
                )
                val enriched = enrichWithRules(baseSession, cookies)
                return@withContext Result.success(enriched)
            }

            // Flow 3: Session cookies present
            if (!cookies.isNullOrEmpty()) {
                for (base in baseUrls) {
                    for (dk in devKeys) {
                        val cookieReq = buildRequest("$base/apiv3/getusersvendors?devkey=$dk&out_format=json", cookies)
                        try {
                            val cookieResp = client.newCall(cookieReq).execute()
                            val cookieBody = cookieResp.body?.string()
                            val json = if (cookieResp.isSuccessful) parseJsonResponse(cookieResp, cookieBody, cookieReq) else {
                                logResponseError(cookieReq, cookieResp, cookieBody)
                                null
                            }
                            if (json != null) {
                                val session = extractUserVendor(json, fallbackUserName)
                                if (session != null) {
                                    val enriched = enrichWithRules(session, cookies)
                                    return@withContext Result.success(enriched)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Call enrichWithRules directly with base session and cookies
                val baseSession = UserSession(
                    token = cookieToken ?: "web_session",
                    vendor = "edu",
                    login = auth?.login ?: "",
                    userTitle = fallbackUserName ?: "Ученик",
                    schoolTitle = "ГИС РК «Образование»"
                )
                val enriched = enrichWithRules(baseSession, cookies)
                if (enriched.userTitle.isNotBlank() && enriched.userTitle != "Ученик" &&
                    enriched.schoolTitle.isNotBlank() && enriched.schoolTitle != "ГИС РК «Образование»") {
                    return@withContext Result.success(enriched)
                }

                // Scrape profile info from https://edu.rk.gov.ru/?show=home and /journal-app
                val profileResult = scrapeUserProfile(cookies)
                val finalName = if (enriched.userTitle.isNotBlank() && enriched.userTitle != "Ученик") {
                    enriched.userTitle
                } else if (profileResult.first.isNotBlank()) {
                    profileResult.first
                } else {
                    fallbackUserName ?: "Ученик"
                }

                val finalSchool = if (enriched.schoolTitle.isNotBlank() && enriched.schoolTitle != "ГИС РК «Образование»") {
                    enriched.schoolTitle
                } else if (profileResult.second.isNotBlank()) {
                    profileResult.second
                } else {
                    "Республика Крым"
                }

                val rawSession = enriched.copy(
                    userTitle = finalName,
                    schoolTitle = finalSchool
                )
                return@withContext Result.success(rawSession)
            }

            Result.failure(Exception("Не удалось авторизовать пользователя в региональном дневнике. Попробуйте войти снова."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun scrapeUserProfile(cookies: String?): Pair<String, String> {
        if (cookies.isNullOrBlank()) return "" to ""
        val candidateUrls = listOf(
            "$baseUrl/?show=home",
            "$baseUrl/journal-app",
            baseUrl
        )
        var scrapedName = ""
        var scrapedSchool = ""

        for (url in candidateUrls) {
            if (scrapedName.isNotBlank() && scrapedSchool.isNotBlank()) break
            try {
                val req = buildRequest(url, cookies, isJson = false)
                val resp = client.newCall(req).execute()
                val html = resp.body?.string() ?: continue
                val doc = Jsoup.parse(html)

                if (scrapedName.isBlank()) {
                    val nameElem = doc.selectFirst(
                        ".user-name, .b-dropdown__user, .header-user__name, .m-user__name, " +
                        ".user_name, .user-profile__name, .user-info__name, .profile-name, " +
                        ".student-name, .b-header__user, .user__name, .h-user, .user-title, " +
                        "div.user-info .name, span.user-name, a.b-dropdown__user"
                    )
                    val rawName = nameElem?.text()?.trim()
                    if (!rawName.isNullOrBlank() && rawName != "Ученик" && rawName != "Вход" && rawName.length >= 2) {
                        scrapedName = rawName
                    }
                }

                if (scrapedSchool.isBlank()) {
                    val schoolElem = doc.selectFirst(
                        ".school-title, .header-school__title, .m-logo__subtitle, .school_title, " +
                        ".school-name, .b-dropdown__school, .b-school__title, .school, " +
                        ".school__title, .school__name, div.school-info"
                    )
                    val rawSchool = schoolElem?.text()?.trim()
                    if (!rawSchool.isNullOrBlank() && rawSchool != "Республика Крым" && rawSchool != "ГИС РК «Образование»" && rawSchool.length >= 3) {
                        scrapedSchool = rawSchool
                    }
                }
            } catch (_: Exception) {}
        }

        return scrapedName to scrapedSchool
    }

    fun getCurrentWeekRange(date: Date = Date()): String {
        return getWeekRangeForOffset(0, date)
    }

    fun getWeekRangeForOffset(weekOffset: Int = 0, baseDate: Date = Date()): String {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.time = baseDate
        val calendarDelta = -weekOffset // week.-1 is next week (+1), week.1 is prev week (-1)
        if (calendarDelta != 0) {
            cal.add(Calendar.WEEK_OF_YEAR, calendarDelta)
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
        val start = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val end = sdf.format(cal.time)
        return "$start-$end"
    }

    fun getMondayDateForOffset(weekOffset: Int = 0, baseDate: Date = Date()): String {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.time = baseDate
        val calendarDelta = -weekOffset
        if (calendarDelta != 0) {
            cal.add(Calendar.WEEK_OF_YEAR, calendarDelta)
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(cal.time)
    }

    private fun formatDisplayDate(raw: String): String {
        return if (raw.length == 8 && raw.all { it.isDigit() }) {
            "${raw.substring(6, 8)}.${raw.substring(4, 6)}.${raw.substring(0, 4)}"
        } else raw
    }

    private fun getDayOfWeekRussian(dateStr: String): String {
        return try {
            val sdf = if (dateStr.length == 8 && dateStr.all { it.isDigit() }) {
                SimpleDateFormat("yyyyMMdd", Locale.US)
            } else if (dateStr.contains(".")) {
                SimpleDateFormat("dd.MM.yyyy", Locale.US)
            } else {
                return dateStr
            }
            val date = sdf.parse(dateStr) ?: return dateStr
            val cal = Calendar.getInstance()
            cal.time = date
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Понедельник"
                Calendar.TUESDAY -> "Вторник"
                Calendar.WEDNESDAY -> "Среда"
                Calendar.THURSDAY -> "Четверг"
                Calendar.FRIDAY -> "Пятница"
                Calendar.SATURDAY -> "Суббота"
                Calendar.SUNDAY -> "Воскресенье"
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun fetchPeriods(
        token: String,
        vendor: String,
        studentId: String? = null,
        cookies: String? = null
    ): Result<List<AcademicPeriod>> = withContext(Dispatchers.IO) {
        try {
            val vendorParam = if (vendor.isNotBlank()) "&vendor=$vendor" else "&vendor=edu"
            val cookieToken = TokenExtractor.extractAuthTokenFromCookies(cookies)
            val effectiveToken = if (token.isNotBlank() && token != "web_session") {
                token
            } else if (!cookieToken.isNullOrBlank()) {
                cookieToken
            } else ""

            val tokenParam = if (effectiveToken.isNotBlank()) "&auth_token=$effectiveToken" else ""
            val baseUrls = getBaseUrls(vendor)

            for (base in baseUrls) {
                for (dk in devKeys) {
                    val urls = mutableListOf<String>()
                    if (!studentId.isNullOrBlank()) {
                        urls.add("$base/apiv3/getperiods?devkey=$dk&out_format=json$tokenParam$vendorParam&weeks=true&show_disabled=true&student=$studentId")
                    }
                    urls.add("$base/apiv3/getperiods?devkey=$dk&out_format=json$tokenParam$vendorParam&weeks=true&show_disabled=true")

                    for (url in urls) {
                        try {
                            val req = buildRequest(url, cookies)
                            val resp = client.newCall(req).execute()
                            val body = resp.body?.string()
                            val json = if (resp.isSuccessful) parseJsonResponse(resp, body, req) else {
                                logResponseError(req, resp, body)
                                null
                            }
                            if (json != null) {
                                val respObj = json.optJSONObject("response") ?: json
                                if (respObj.optInt("state", 200) == 200) {
                                    val periods = parsePeriodsJson(respObj, studentId)
                                    if (periods.isNotEmpty()) {
                                        return@withContext Result.success(periods)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "fetchPeriods error: ${e.message} for $url")
                        }
                    }
                }
            }
            Result.success(generateDefaultAcademicPeriods())
        } catch (_: Exception) {
            Result.success(generateDefaultAcademicPeriods())
        }
    }

    fun parsePeriodsJson(respObj: JSONObject, filterStudentId: String? = null): List<AcademicPeriod> {
        val result = respObj.optJSONObject("result") ?: respObj

        var studentObj: JSONObject? = null
        val studentsObj = result.optJSONObject("students")
        val studentsArr = result.optJSONArray("students")

        if (studentsObj != null && studentsObj.length() > 0) {
            if (!filterStudentId.isNullOrBlank() && studentsObj.has(filterStudentId)) {
                studentObj = studentsObj.optJSONObject(filterStudentId)
            }
            if (studentObj == null) {
                for (key in studentsObj.keys()) {
                    val candidate = studentsObj.optJSONObject(key)
                    if (candidate != null && candidate.has("periods")) {
                        studentObj = candidate
                        break
                    }
                }
            }
            if (studentObj == null) {
                val firstKey = studentsObj.keys().asSequence().firstOrNull()
                if (firstKey != null) studentObj = studentsObj.optJSONObject(firstKey)
            }
        } else if (studentsArr != null && studentsArr.length() > 0) {
            if (!filterStudentId.isNullOrBlank()) {
                for (i in 0 until studentsArr.length()) {
                    val candidate = studentsArr.optJSONObject(i)
                    if (candidate != null && (candidate.optString("name") == filterStudentId || candidate.optString("id") == filterStudentId)) {
                        studentObj = candidate
                        break
                    }
                }
            }
            if (studentObj == null) {
                for (i in 0 until studentsArr.length()) {
                    val candidate = studentsArr.optJSONObject(i)
                    if (candidate != null && candidate.has("periods")) {
                        studentObj = candidate
                        break
                    }
                }
            }
            if (studentObj == null) {
                studentObj = studentsArr.optJSONObject(0)
            }
        }

        if (studentObj == null && result.has("periods")) {
            studentObj = result
        }

        if (studentObj == null) return emptyList()

        val periodObjects = mutableListOf<JSONObject>()
        val pArr = studentObj.optJSONArray("periods")
        val pObj = studentObj.optJSONObject("periods")

        if (pArr != null) {
            for (i in 0 until pArr.length()) {
                val p = pArr.optJSONObject(i)
                if (p != null) periodObjects.add(p)
            }
        } else if (pObj != null) {
            val sortedKeys = pObj.keys().asSequence().sorted().toList()
            for (k in sortedKeys) {
                val p = pObj.optJSONObject(k)
                if (p != null) periodObjects.add(p)
            }
        }

        if (periodObjects.isEmpty()) return emptyList()

        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
        val todayStr = sdf.format(Date())

        val list = mutableListOf<AcademicPeriod>()
        for ((idx, p) in periodObjects.withIndex()) {
            val name = p.optString("name", "${idx + 1}")
            val title = p.optString("fullname").ifBlank {
                p.optString("title").ifBlank { "$name четверть" }
            }
            val startRaw = p.optString("start", "").replace("-", "").replace(".", "").trim()
            val endRaw = p.optString("end", "").replace("-", "").replace(".", "").trim()

            if (startRaw.length == 8 && endRaw.length == 8) {
                val rangeDays = "$startRaw-$endRaw"
                val isActive = todayStr in startRaw..endRaw
                list.add(
                    AcademicPeriod(
                        id = name,
                        title = title,
                        start = startRaw,
                        end = endRaw,
                        rangeDays = rangeDays,
                        isActive = isActive
                    )
                )
            }
        }

        if (list.isNotEmpty() && list.none { it.isActive }) {
            val active = list.firstOrNull { todayStr < it.end } ?: list.last()
            return list.map { it.copy(isActive = it.id == active.id) }
        }

        return list
    }

    fun generateDefaultAcademicPeriods(now: Date = Date()): List<AcademicPeriod> {
        val cal = Calendar.getInstance()
        cal.time = now
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-based: 0=Jan, 8=Sep
        val schoolYearStart = if (month >= Calendar.SEPTEMBER) year else year - 1
        val nextYear = schoolYearStart + 1

        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
        val todayStr = sdf.format(now)

        val p1 = AcademicPeriod("1", "1 четверть", "${schoolYearStart}0901", "${schoolYearStart}1026", "${schoolYearStart}0901-${schoolYearStart}1026")
        val p2 = AcademicPeriod("2", "2 четверть", "${schoolYearStart}1105", "${schoolYearStart}1228", "${schoolYearStart}1105-${schoolYearStart}1228")
        val p3 = AcademicPeriod("3", "3 четверть", "${nextYear}0111", "${nextYear}0322", "${nextYear}0111-${nextYear}0322")
        val p4 = AcademicPeriod("4", "4 четверть", "${nextYear}0401", "${nextYear}0525", "${nextYear}0401-${nextYear}0525")

        val periods = listOf(p1, p2, p3, p4)
        val activePeriod = periods.firstOrNull { todayStr in it.start..it.end }
            ?: if (todayStr < p1.end) p1 else if (todayStr < p2.end) p2 else if (todayStr < p3.end) p3 else p4

        return periods.map { it.copy(isActive = it.id == activePeriod.id) }
    }

    suspend fun fetchDiary(
        token: String,
        vendor: String,
        studentId: String? = null,
        days: String? = null,
        cookies: String? = null,
        weekOffset: Int = 0
    ): Result<List<DaySchedule>> = withContext(Dispatchers.IO) {
        try {
            val targetDays = if (!days.isNullOrBlank()) days else getCurrentWeekRange()
            val vendorParam = if (vendor.isNotBlank()) "&vendor=$vendor" else "&vendor=edu"
            val cookieToken = TokenExtractor.extractAuthTokenFromCookies(cookies)
            val effectiveToken = if (token.isNotBlank() && token != "web_session") {
                token
            } else if (!cookieToken.isNullOrBlank()) {
                cookieToken
            } else ""

            // 1. Primary SSR web parsing for edu.rk.gov.ru when cookies are available
            if (!cookies.isNullOrEmpty()) {
                val webDays = fetchDiaryFromWeb(cookies, studentId, weekOffset)
                if (webDays.isSuccess && webDays.getOrNull()?.isNotEmpty() == true) {
                    return@withContext webDays
                }
                if (webDays.exceptionOrNull() is SessionExpiredException) {
                    return@withContext webDays
                }
            }

            // 2. Official API v3 calls if authentic token is available
            if (effectiveToken.isNotBlank()) {
                val tokenParam = "&auth_token=$effectiveToken"
                val studentCandidates = mutableListOf<String?>()
                if (!studentId.isNullOrBlank()) {
                    studentCandidates.add(studentId)
                    if (studentId.startsWith("u.")) {
                        studentCandidates.add(studentId.removePrefix("u."))
                    } else {
                        studentCandidates.add("u.$studentId")
                    }
                }
                studentCandidates.add(null)

                var lastApiError: String? = null
                val baseUrls = getBaseUrls(vendor)

                for (base in baseUrls) {
                    for (dk in devKeys) {
                        for (cand in studentCandidates) {
                            val candParam = if (cand != null) "&student=$cand" else ""
                            val candidateUrls = listOf(
                                "$base/apiv3/getdiary?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam&days=$targetDays&rings=1",
                                "$base/apiv3/getdiary?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam&days=$targetDays",
                                "$base/apiv3/getschedule?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam&days=$targetDays&rings=1",
                                "$base/api/getdiary?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam&days=$targetDays&rings=1",
                                "$base/api/getschedule?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam&days=$targetDays&rings=1"
                            )

                            for (url in candidateUrls) {
                                try {
                                    val req = buildRequest(url, cookies)
                                    val response = client.newCall(req).execute()
                                    val body = response.body?.string()

                                    checkSessionValidity(response, body)

                                    if (response.isSuccessful) {
                                        val json = parseJsonResponse(response, body, req)
                                        if (json != null) {
                                            val respObj = json.optJSONObject("response") ?: json
                                            if (respObj.optInt("state", 200) == 200) {
                                                val parsedDays = parseApiDiaryDays(respObj, cand ?: studentId)
                                                if (parsedDays.isNotEmpty()) {
                                                    Log.d(TAG, "Successfully loaded ${parsedDays.size} days from $url")
                                                    return@withContext Result.success(parsedDays)
                                                }
                                            } else {
                                                val err = respObj.optJSONObject("error")?.optString("message")
                                                    ?: "state=${respObj.optInt("state")}"
                                                lastApiError = err
                                                Log.w(TAG, "Diary API call returned error: $err for $url")
                                            }
                                        } else {
                                            logResponseError(req, response, body)
                                        }
                                    } else {
                                        logResponseError(req, response, body)
                                        if (response.code != 404) {
                                            lastApiError = "HTTP ${response.code}"
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (e is SessionExpiredException) return@withContext Result.failure(e)
                                    lastApiError = e.message
                                    Log.w(TAG, "Exception querying diary API: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            Result.failure(Exception("Не удалось загрузить дневник из региональной системы"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMarks(
        token: String,
        vendor: String,
        studentId: String? = null,
        days: String? = null,
        cookies: String? = null
    ): Result<List<SubjectSummary>> = withContext(Dispatchers.IO) {
        try {
            val vendorParam = if (vendor.isNotBlank()) "&vendor=$vendor" else "&vendor=edu"
            val cookieToken = TokenExtractor.extractAuthTokenFromCookies(cookies)
            val effectiveToken = if (token.isNotBlank() && token != "web_session") {
                token
            } else if (!cookieToken.isNullOrBlank()) {
                cookieToken
            } else ""

            // 1. Primary SSR web parsing for edu.rk.gov.ru when cookies are available
            if (!cookies.isNullOrEmpty()) {
                val webMarks = scrapeMarksFromWeb(cookies, studentId)
                if (webMarks.isSuccess && webMarks.getOrNull()?.isNotEmpty() == true) {
                    return@withContext webMarks
                }
                if (webMarks.exceptionOrNull() is SessionExpiredException) {
                    return@withContext webMarks
                }
            }

            // 2. Official API v3 calls if authentic token is available
            if (effectiveToken.isNotBlank()) {
                val tokenParam = "&auth_token=$effectiveToken"
                val targetRanges = mutableListOf<String?>()
                if (!days.isNullOrBlank()) {
                    targetRanges.add(days)
                }

                // Also query periods to get active period range
                val periodsRes = fetchPeriods(effectiveToken, vendor, studentId, cookies).getOrNull()
                val activePeriod = periodsRes?.firstOrNull { it.isActive } ?: periodsRes?.firstOrNull()
                if (activePeriod != null && activePeriod.rangeDays !in targetRanges) {
                    targetRanges.add(activePeriod.rangeDays)
                }

                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                val schoolYearStart = if (month >= Calendar.SEPTEMBER) currentYear else currentYear - 1
                val nextYear = schoolYearStart + 1
                val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
                val todayStr = sdf.format(cal.time)

                val fullTermRange = "${schoolYearStart}0901-${nextYear}0531"
                if (fullTermRange !in targetRanges) targetRanges.add(fullTermRange)

                val yearToDateRange = "${schoolYearStart}0901-$todayStr"
                if (yearToDateRange !in targetRanges) targetRanges.add(yearToDateRange)

                val currentWeek = getCurrentWeekRange()
                if (currentWeek !in targetRanges) targetRanges.add(currentWeek)

                targetRanges.add(null) // without days param

                val studentCandidates = mutableListOf<String?>()
                if (!studentId.isNullOrBlank()) {
                    studentCandidates.add(studentId)
                    if (studentId.startsWith("u.")) {
                        studentCandidates.add(studentId.removePrefix("u."))
                    } else {
                        studentCandidates.add("u.$studentId")
                    }
                }
                studentCandidates.add(null)

                // Also fetch final/quarter marks in parallel or sequentially
                val finalMarksMap = fetchFinalAssessments(effectiveToken, vendor, studentId, cookies).getOrDefault(emptyMap())

                val baseUrls = getBaseUrls(vendor)
                for (base in baseUrls) {
                    for (dk in devKeys) {
                        for (cand in studentCandidates) {
                            val candParam = if (cand != null) "&student=$cand" else ""
                            for (range in targetRanges) {
                                val daysParam = if (range != null) "&days=$range" else ""
                                val candidateMarkUrls = listOf(
                                    "$base/apiv3/getmarks?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam$daysParam",
                                    "$base/api/getmarks?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam$daysParam"
                                )
                                for (url in candidateMarkUrls) {
                                    try {
                                        val req = buildRequest(url, cookies)
                                        val response = client.newCall(req).execute()
                                        val body = response.body?.string()

                                        checkSessionValidity(response, body)

                                        if (response.isSuccessful) {
                                            val json = parseJsonResponse(response, body, req)
                                            if (json != null) {
                                                val respObj = json.optJSONObject("response") ?: json
                                                if (respObj.optInt("state", 200) == 200) {
                                                    val marks = parseApiMarks(respObj, cand ?: studentId)
                                                    if (marks.isNotEmpty()) {
                                                        val enrichedMarks = if (finalMarksMap.isNotEmpty()) {
                                                            marks.map { subj ->
                                                                val fm = subj.finalMark ?: finalMarksMap[subj.subject]
                                                                subj.copy(finalMark = fm)
                                                            }
                                                        } else marks

                                                        Log.d(TAG, "Successfully fetched ${enrichedMarks.size} subjects from $url")
                                                        return@withContext Result.success(enrichedMarks)
                                                    }
                                                }
                                            } else {
                                                logResponseError(req, response, body)
                                            }
                                        } else {
                                            logResponseError(req, response, body)
                                        }
                                    } catch (e: Exception) {
                                        if (e is SessionExpiredException) return@withContext Result.failure(e)
                                        Log.w(TAG, "Marks API candidate failed: ${e.message} for $url")
                                    }
                                }
                            }
                        }
                    }
                }

                // If getmarks was empty, but getfinalassessments returned final/quarter marks:
                if (finalMarksMap.isNotEmpty()) {
                    val summaries = finalMarksMap.map { (subject: String, finalMark: String) ->
                        val num = finalMark.take(1).toFloatOrNull() ?: 0f
                        SubjectSummary(
                            subject = subject,
                            average = num,
                            marks = listOf(Mark(value = finalMark)),
                            finalMark = finalMark
                        )
                    }.sortedByDescending { it.average }
                    return@withContext Result.success(summaries)
                }
            }

            Result.failure(Exception("Не удалось загрузить оценки из региональной системы"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseApiMarks(respObj: JSONObject, filterStudentId: String? = null): List<SubjectSummary> {
        val result = respObj.optJSONObject("result") ?: respObj

        // 1. Locate student container
        var studentObj: JSONObject? = null
        val studentsObj = result.optJSONObject("students")
        val studentsArr = result.optJSONArray("students")

        val altId = if (!filterStudentId.isNullOrBlank()) {
            if (filterStudentId.startsWith("u.")) filterStudentId.removePrefix("u.") else "u.$filterStudentId"
        } else null

        if (studentsObj != null && studentsObj.length() > 0) {
            if (!filterStudentId.isNullOrBlank()) {
                studentObj = studentsObj.optJSONObject(filterStudentId)
                    ?: if (altId != null) studentsObj.optJSONObject(altId) else null
            }
            if (studentObj == null) {
                for (key in studentsObj.keys()) {
                    val candidate = studentsObj.optJSONObject(key)
                    if (candidate != null && (candidate.has("lessons") || candidate.has("marks"))) {
                        studentObj = candidate
                        break
                    }
                }
            }
            if (studentObj == null) {
                val firstKey = studentsObj.keys().asSequence().firstOrNull()
                if (firstKey != null) studentObj = studentsObj.optJSONObject(firstKey)
            }
        } else if (studentsArr != null && studentsArr.length() > 0) {
            if (!filterStudentId.isNullOrBlank()) {
                for (i in 0 until studentsArr.length()) {
                    val candidate = studentsArr.optJSONObject(i)
                    val cId = candidate?.optString("id").takeIf { !it.isNullOrBlank() } ?: candidate?.optString("name")
                    if (cId == filterStudentId || cId == altId) {
                        studentObj = candidate
                        break
                    }
                }
            }
            if (studentObj == null) {
                for (i in 0 until studentsArr.length()) {
                    val candidate = studentsArr.optJSONObject(i)
                    if (candidate != null && (candidate.has("lessons") || candidate.has("marks"))) {
                        studentObj = candidate
                        break
                    }
                }
            }
            if (studentObj == null) {
                studentObj = studentsArr.optJSONObject(0)
            }
        }

        if (studentObj == null) {
            if (result.has("lessons") || result.has("marks")) {
                studentObj = result
            }
        }

        if (studentObj == null) return emptyList()

        // 2. Extract lessons
        val lessonObjects = mutableListOf<JSONObject>()
        val lessonsArr = studentObj.optJSONArray("lessons")
        val lessonsObj = studentObj.optJSONObject("lessons")

        if (lessonsArr != null) {
            for (i in 0 until lessonsArr.length()) {
                val l = lessonsArr.optJSONObject(i)
                if (l != null) lessonObjects.add(l)
            }
        } else if (lessonsObj != null) {
            val sortedKeys = lessonsObj.keys().asSequence().sorted().toList()
            for (k in sortedKeys) {
                val l = lessonsObj.optJSONObject(k)
                if (l != null) lessonObjects.add(l)
            }
        }

        val list = mutableListOf<SubjectSummary>()
        for (lessonObj in lessonObjects) {
            val name = lessonObj.optString("name").ifBlank {
                lessonObj.optString("title").ifBlank {
                    lessonObj.optString("subject").ifBlank { "Предмет" }
                }
            }
            val avgRaw = lessonObj.optString("average", "0").replace(',', '.')
            val avgFloat = avgRaw.toFloatOrNull() ?: 0f
            val finalMark = lessonObj.optString("final_mark").ifBlank {
                lessonObj.optString("itog").ifBlank { null }
            }

            val marksList = mutableListOf<Mark>()
            val marksArr = lessonObj.optJSONArray("marks")
            val marksObj = lessonObj.optJSONObject("marks")

            if (marksArr != null) {
                for (j in 0 until marksArr.length()) {
                    val mObj = marksArr.optJSONObject(j)
                    if (mObj != null) {
                        parseSingleMark(mObj)?.let { marksList.add(it) }
                    } else {
                        val primVal = marksArr.optString(j, "").trim()
                        if (primVal.isNotEmpty()) {
                            marksList.add(Mark(value = primVal))
                        }
                    }
                }
            } else if (marksObj != null) {
                val sortedKeys = marksObj.keys().asSequence().sorted().toList()
                for (k in sortedKeys) {
                    val mObj = marksObj.optJSONObject(k)
                    if (mObj != null) {
                        parseSingleMark(mObj)?.let { marksList.add(it) }
                    } else {
                        val primVal = marksObj.optString(k, "").trim()
                        if (primVal.isNotEmpty()) {
                            marksList.add(Mark(value = primVal))
                        }
                    }
                }
            }

            val effectiveAvg = if (avgFloat > 0f) avgFloat else {
                val numeric = marksList.mapNotNull { it.numericValue }
                if (numeric.isNotEmpty()) numeric.average().toFloat() else 0f
            }

            list.add(
                SubjectSummary(
                    subject = name,
                    average = effectiveAvg,
                    marks = marksList,
                    finalMark = finalMark,
                    periodMarks = groupMarksIntoPeriods(marksList, finalMark)
                )
            )
        }

        return list.sortedByDescending { it.average }
    }

    private fun parseSingleMark(mObj: JSONObject): Mark? {
        val valStr = mObj.optString("value").ifBlank {
            mObj.optString("mark").ifBlank {
                mObj.optString("val", "")
            }
        }.trim()

        if (valStr.isEmpty()) return null

        val weightRaw = mObj.opt("weight")
        val weight = when (weightRaw) {
            is Number -> weightRaw.toInt()
            is String -> weightRaw.toDoubleOrNull()?.toInt() ?: 1
            else -> 1
        }

        val rawDate = mObj.optString("date", "")
        val formattedDate = formatMarkDate(rawDate)

        val comment = mObj.optString("comment").ifBlank {
            mObj.optString("control_type").ifBlank {
                mObj.optString("lesson_comment").ifBlank { null }
            }
        }

        val isControl = mObj.optBoolean("is_control", false) ||
                mObj.optString("control_type_short").isNotBlank() ||
                (comment?.contains("контроль", ignoreCase = true) == true)

        return Mark(
            value = valStr,
            weight = weight,
            date = formattedDate,
            comment = comment,
            isControl = isControl
        )
    }

    private fun formatMarkDate(raw: String): String {
        if (raw.isBlank()) return ""
        val clean = raw.replace("-", "").replace(".", "")
        return if (clean.length == 8 && clean.all { it.isDigit() }) {
            "${clean.substring(6, 8)}.${clean.substring(4, 6)}"
        } else if (raw.contains("-") && raw.length >= 10) {
            val parts = raw.split("-")
            "${parts[2].take(2)}.${parts[1]}"
        } else raw
    }

    suspend fun fetchFinalAssessments(
        token: String,
        vendor: String,
        studentId: String? = null,
        cookies: String? = null
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val vendorParam = if (vendor.isNotBlank()) "&vendor=$vendor" else "&vendor=edu"
            val cookieToken = TokenExtractor.extractAuthTokenFromCookies(cookies)
            val effectiveToken = if (token.isNotBlank() && token != "web_session") {
                token
            } else if (!cookieToken.isNullOrBlank()) {
                cookieToken
            } else ""

            val tokenParam = if (effectiveToken.isNotBlank()) "&auth_token=$effectiveToken" else ""

            val studentCandidates = mutableListOf<String?>()
            if (!studentId.isNullOrBlank()) {
                studentCandidates.add(studentId)
                if (studentId.startsWith("u.")) {
                    studentCandidates.add(studentId.removePrefix("u."))
                } else {
                    studentCandidates.add("u.$studentId")
                }
            }
            studentCandidates.add(null)

            val baseUrls = getBaseUrls(vendor)
            for (base in baseUrls) {
                for (dk in devKeys) {
                    for (cand in studentCandidates) {
                        val candParam = if (cand != null) "&student=$cand" else ""
                        val candidateUrls = listOf(
                            "$base/apiv3/getfinalassessments?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam",
                            "$base/apiv3/getfinalmarks?devkey=$dk&out_format=json$tokenParam$vendorParam$candParam"
                        )
                        for (url in candidateUrls) {
                            try {
                                val req = buildRequest(url, cookies)
                                val resp = client.newCall(req).execute()
                                val body = resp.body?.string()
                                if (resp.isSuccessful) {
                                    val json = parseJsonResponse(resp, body, req)
                                    if (json != null) {
                                        val respObj = json.optJSONObject("response") ?: json
                                        if (respObj.optInt("state", 200) == 200) {
                                            val finalMarks = parseFinalAssessmentsJson(respObj, cand ?: studentId)
                                            if (finalMarks.isNotEmpty()) {
                                                return@withContext Result.success(finalMarks)
                                            }
                                        }
                                    } else {
                                        logResponseError(req, resp, body)
                                    }
                                } else {
                                    logResponseError(req, resp, body)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "fetchFinalAssessments error: ${e.message} for $url")
                            }
                        }
                    }
                }
            }
            Result.success(emptyMap())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseFinalAssessmentsJson(respObj: JSONObject, filterStudentId: String? = null): Map<String, String> {
        val result = respObj.optJSONObject("result") ?: respObj
        val studentsObj = result.optJSONObject("students")
        val studentsArr = result.optJSONArray("students")

        val altId = if (!filterStudentId.isNullOrBlank()) {
            if (filterStudentId.startsWith("u.")) filterStudentId.removePrefix("u.") else "u.$filterStudentId"
        } else null

        var targetStudentObj: JSONObject? = null
        if (studentsObj != null && studentsObj.length() > 0) {
            if (!filterStudentId.isNullOrBlank() && studentsObj.has(filterStudentId)) {
                targetStudentObj = studentsObj.optJSONObject(filterStudentId)
            } else if (altId != null && studentsObj.has(altId)) {
                targetStudentObj = studentsObj.optJSONObject(altId)
            }
            if (targetStudentObj == null) {
                for (k in studentsObj.keys()) {
                    val s = studentsObj.optJSONObject(k)
                    if (s != null && (s.has("items") || s.has("lessons"))) {
                        targetStudentObj = s
                        break
                    }
                }
                if (targetStudentObj == null) {
                    targetStudentObj = studentsObj.optJSONObject(studentsObj.keys().asSequence().first())
                }
            }
        } else if (studentsArr != null && studentsArr.length() > 0) {
            for (i in 0 until studentsArr.length()) {
                val s = studentsArr.optJSONObject(i)
                if (s != null) {
                    val sName = s.optString("name")
                    val sId = s.optString("id")
                    if (sName == filterStudentId || sId == filterStudentId || sName == altId || sId == altId) {
                        targetStudentObj = s
                        break
                    }
                }
            }
            if (targetStudentObj == null) {
                targetStudentObj = studentsArr.optJSONObject(0)
            }
        }

        if (targetStudentObj == null && (result.has("items") || result.has("lessons"))) {
            targetStudentObj = result
        }

        if (targetStudentObj == null) return emptyMap()

        val itemsList = mutableListOf<JSONObject>()
        val itemsArr = targetStudentObj.optJSONArray("items") ?: targetStudentObj.optJSONArray("lessons")
        val itemsObj = targetStudentObj.optJSONObject("items") ?: targetStudentObj.optJSONObject("lessons")

        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val itm = itemsArr.optJSONObject(i)
                if (itm != null) itemsList.add(itm)
            }
        } else if (itemsObj != null) {
            for (k in itemsObj.keys()) {
                val itm = itemsObj.optJSONObject(k)
                if (itm != null) itemsList.add(itm)
            }
        }

        val finalMarksMap = mutableMapOf<String, String>()
        for (itm in itemsList) {
            val subjectName = itm.optString("name").ifBlank { itm.optString("title", "") }.trim()
            if (subjectName.isBlank()) continue

            val assessmentsArr = itm.optJSONArray("assessments") ?: itm.optJSONArray("marks")
            val assessmentsObj = itm.optJSONObject("assessments") ?: itm.optJSONObject("marks")

            var lastVal = ""
            if (assessmentsArr != null && assessmentsArr.length() > 0) {
                val lastObj = assessmentsArr.optJSONObject(assessmentsArr.length() - 1)
                lastVal = lastObj?.optString("value")?.ifBlank { lastObj.optString("mark", "") } ?: ""
            } else if (assessmentsObj != null && assessmentsObj.length() > 0) {
                val keys = assessmentsObj.keys().asSequence().sorted().toList()
                val lastKey = keys.lastOrNull()
                if (lastKey != null) {
                    val aObj = assessmentsObj.optJSONObject(lastKey)
                    lastVal = aObj?.optString("value")?.ifBlank { aObj.optString("mark", "") }
                        ?: assessmentsObj.optString(lastKey, "")
                }
            } else {
                lastVal = itm.optString("value").ifBlank { itm.optString("final_mark", "") }
            }

            if (lastVal.isNotBlank()) {
                finalMarksMap[subjectName] = lastVal.trim()
            }
        }
        return finalMarksMap
    }

    fun scrapeMarksFromWeb(cookies: String, studentId: String? = null): Result<List<SubjectSummary>> {
        return try {
            val cleanStudent = studentId?.removePrefix("u.")
            val candidateUrls = mutableListOf<String>()
            if (!cleanStudent.isNullOrBlank()) {
                candidateUrls.add("$baseUrl/journal-student-grades-action/u.$cleanStudent")
                candidateUrls.add("$baseUrl/journal-app/itog/u.$cleanStudent")
                candidateUrls.add("$baseUrl/journal-app/marks/u.$cleanStudent")
                candidateUrls.add("$baseUrl/journal-app/u.$cleanStudent")
            }
            candidateUrls.add("$baseUrl/journal-student-grades-action")
            candidateUrls.add("$baseUrl/journal-app/itog")
            candidateUrls.add("$baseUrl/journal-app/marks")
            candidateUrls.add("$baseUrl/journal-app")

            for (targetUrl in candidateUrls) {
                val req = buildRequest(targetUrl, cookies, isJson = false)
                val resp = client.newCall(req).execute()
                val html = resp.body?.string() ?: continue

                checkSessionValidity(resp, html)

                if (!resp.isSuccessful || html.contains("<title>Ошибка") || html.contains("Страница не найдена")) {
                    logResponseError(req, resp, html)
                    continue
                }

                // Check embedded JSON in script tags first
                val scriptMarks = extractMarksFromHtmlScripts(html, studentId)
                if (scriptMarks.isNotEmpty()) {
                    return Result.success(scriptMarks)
                }

                val doc = Jsoup.parse(html)
                val parsed = parseGradesHtml(doc)
                if (parsed.isNotEmpty()) {
                    return Result.success(parsed)
                }
            }

            Result.failure(Exception("Не удалось извлечь оценки со страницы регионального портала"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGradesHtml(doc: org.jsoup.nodes.Document): List<SubjectSummary> {
        val rows = doc.select("tr.journal-table__row, tr.marks-row, tr.dnevnik-row, table.journal tr, table.dnevnik tr, table.grid tr, table tr")
        if (rows.isEmpty()) return emptyList()

        val subjectMap = LinkedHashMap<String, SubjectSummary>()

        for (row in rows) {
            val subjectElem = row.selectFirst(".subject, .lesson-name, .discipline, .subject-title, td.name, td:first-child") ?: continue
            val subjectName = subjectElem.text().trim()
            if (subjectName.isBlank() ||
                subjectName.contains("Предмет", ignoreCase = true) ||
                subjectName.contains("Дисциплина", ignoreCase = true) ||
                subjectName.contains("ФИО", ignoreCase = true) ||
                subjectName.length < 2 ||
                row.children().all { it.tagName() == "th" }
            ) {
                continue
            }

            val markElements = row.select(".mark, .grade, .mark-badge, span.cell-mark, .mark_val, .val, .cell-grade, .m-mark, span.mark, .dnevnik-mark")
            val marks = mutableListOf<Mark>()

            for (mElem in markElements) {
                val mVal = mElem.ownText().ifBlank { mElem.text() }.trim()
                if (mVal.isNotBlank() && mVal.length <= 4) {
                    val subWeight = mElem.selectFirst("sub")?.text()?.toIntOrNull()
                        ?: mElem.selectFirst("sup")?.text()?.toIntOrNull()
                        ?: mElem.attr("data-weight").toIntOrNull()
                        ?: Regex("""вес[:\s]+(\d+)""", RegexOption.IGNORE_CASE).find(mElem.attr("title"))?.groupValues?.get(1)?.toIntOrNull()
                        ?: 1

                    val tooltip = mElem.attr("title").ifBlank { null }
                    val dateMatch = tooltip?.let { Regex("""\b(\d{2}\.\d{2}(?:\.\d{4})?)\b""").find(it)?.groupValues?.get(1) }
                    val isCtrl = subWeight > 1 || (tooltip?.contains("контроль", ignoreCase = true) == true) || (tooltip?.contains("самостоят", ignoreCase = true) == true)

                    marks.add(
                        Mark(
                            value = mVal,
                            weight = subWeight,
                            date = dateMatch ?: "",
                            comment = tooltip,
                            isControl = isCtrl
                        )
                    )
                }
            }

            if (marks.isEmpty()) {
                val cells = row.select("td")
                for (cell in cells.drop(1)) {
                    val cellText = cell.ownText().trim()
                    if (cellText.isNotEmpty() && cellText.length <= 4 && (cellText[0].isDigit() || cellText.equals("н", true) || cellText.equals("зач", true))) {
                        val tooltip = cell.attr("title").ifBlank { null }
                        val subWeight = Regex("""вес[:\s]+(\d+)""", RegexOption.IGNORE_CASE).find(tooltip ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        marks.add(
                            Mark(
                                value = cellText,
                                weight = subWeight,
                                comment = tooltip,
                                isControl = subWeight > 1 || (tooltip?.contains("контроль", true) == true)
                            )
                        )
                    }
                }
            }

            val avgElem = row.selectFirst(".average, .avg, .average-grade, .middle-mark, .cell-avg")
            val avgStr = avgElem?.text()?.replace(',', '.')?.trim()
            val avgFloat = avgStr?.toFloatOrNull() ?: if (marks.isNotEmpty()) {
                val totalWeight = marks.filter { it.numericValue != null }.sumOf { it.weight }
                if (totalWeight > 0) {
                    val weightedSum = marks.sumOf { ((it.numericValue ?: 0f) * it.weight).toDouble() }
                    (weightedSum / totalWeight).toFloat()
                } else 0f
            } else 0f

            val finalMarkElem = row.selectFirst(".itog, .final-mark, .quarter-mark, .period-mark, td.itog")
            val finalMark = finalMarkElem?.text()?.trim()?.takeIf { it.isNotBlank() && it.length <= 3 }

            if (marks.isNotEmpty() || avgFloat > 0f || finalMark != null) {
                val existing = subjectMap[subjectName]
                if (existing != null) {
                    val mergedMarks = (existing.marks + marks).distinctBy { "${it.value}_${it.date}_${it.comment}" }
                    val bestFinal = existing.finalMark ?: finalMark
                    val bestAvg = if (avgFloat > 0f) avgFloat else existing.average
                    subjectMap[subjectName] = SubjectSummary(
                        subject = subjectName,
                        average = bestAvg,
                        marks = mergedMarks,
                        finalMark = bestFinal,
                        periodMarks = groupMarksIntoPeriods(mergedMarks, bestFinal)
                    )
                } else {
                    subjectMap[subjectName] = SubjectSummary(
                        subject = subjectName,
                        average = avgFloat,
                        marks = marks,
                        finalMark = finalMark,
                        periodMarks = groupMarksIntoPeriods(marks, finalMark)
                    )
                }
            }
        }

        return subjectMap.values.sortedByDescending { it.average }
    }

    private fun parseApiDiaryDays(respObj: JSONObject, filterStudentId: String? = null): List<DaySchedule> {
        val result = respObj.optJSONObject("result") ?: respObj

        // Find days container (can be directly under result or inside students)
        val dayEntries = mutableListOf<Pair<String, JSONObject>>()

        val directDaysObj = result.optJSONObject("days")
        val directDaysArr = result.optJSONArray("days")

        if (directDaysObj != null) {
            for (key in directDaysObj.keys().asSequence().sorted()) {
                val d = directDaysObj.optJSONObject(key)
                if (d != null) dayEntries.add(key to d)
            }
        } else if (directDaysArr != null) {
            for (i in 0 until directDaysArr.length()) {
                val d = directDaysArr.optJSONObject(i)
                if (d != null) {
                    val key = d.optString("name", "day_$i")
                    dayEntries.add(key to d)
                }
            }
        } else {
            // Under students
            val studentsObj = result.optJSONObject("students")
            val studentsArr = result.optJSONArray("students")

            val altId = if (!filterStudentId.isNullOrBlank()) {
                if (filterStudentId.startsWith("u.")) filterStudentId.removePrefix("u.") else "u.$filterStudentId"
            } else null

            val targetStudentObj = if (studentsObj != null && studentsObj.length() > 0) {
                var found: JSONObject? = null
                if (!filterStudentId.isNullOrBlank() && studentsObj.has(filterStudentId)) {
                    found = studentsObj.optJSONObject(filterStudentId)
                } else if (altId != null && studentsObj.has(altId)) {
                    found = studentsObj.optJSONObject(altId)
                }
                if (found == null) {
                    for (k in studentsObj.keys()) {
                        val s = studentsObj.optJSONObject(k)
                        if (s != null && (s.has("days") || s.has("items"))) {
                            found = s
                            break
                        }
                    }
                }
                found ?: studentsObj.optJSONObject(studentsObj.keys().asSequence().first())
            } else if (studentsArr != null && studentsArr.length() > 0) {
                var found: JSONObject? = null
                if (!filterStudentId.isNullOrBlank()) {
                    for (i in 0 until studentsArr.length()) {
                        val s = studentsArr.optJSONObject(i)
                        val sName = s?.optString("name")
                        val sId = s?.optString("id")
                        if (sName == filterStudentId || sId == filterStudentId || sName == altId || sId == altId) {
                            found = s
                            break
                        }
                    }
                }
                if (found == null) {
                    for (i in 0 until studentsArr.length()) {
                        val s = studentsArr.optJSONObject(i)
                        if (s != null && (s.has("days") || s.has("items"))) {
                            found = s
                            break
                        }
                    }
                }
                found ?: studentsArr.optJSONObject(0)
            } else null

            if (targetStudentObj != null) {
                val sDaysObj = targetStudentObj.optJSONObject("days")
                val sDaysArr = targetStudentObj.optJSONArray("days")
                if (sDaysObj != null) {
                    for (key in sDaysObj.keys().asSequence().sorted()) {
                        val d = sDaysObj.optJSONObject(key)
                        if (d != null) dayEntries.add(key to d)
                    }
                } else if (sDaysArr != null) {
                    for (i in 0 until sDaysArr.length()) {
                        val d = sDaysArr.optJSONObject(i)
                        if (d != null) {
                            val key = d.optString("name", "day_$i")
                            dayEntries.add(key to d)
                        }
                    }
                }
            }
        }

        if (dayEntries.isEmpty()) return emptyList()

        val list = mutableListOf<DaySchedule>()
        for ((dayKey, dayData) in dayEntries) {
            val name = dayData.optString("name", dayKey)
            val title = dayData.optString("title", "")
            val holidayName = dayData.optString("holiday_name", "").ifBlank { null }
            val isHoliday = holidayName != null

            val lessons = mutableListOf<Lesson>()

            val itemObjects = mutableListOf<JSONObject>()
            val itemsArr = dayData.optJSONArray("items")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val itm = itemsArr.optJSONObject(i)
                    if (itm != null) itemObjects.add(itm)
                }
            } else {
                val itemsObj = dayData.optJSONObject("items")
                if (itemsObj != null) {
                    val sortedItemKeys = itemsObj.keys().asSequence().sorted().toList()
                    for (k in sortedItemKeys) {
                        val itm = itemsObj.optJSONObject(k)
                        if (itm != null) itemObjects.add(itm)
                    }
                }
            }

            for ((idx, item) in itemObjects.withIndex()) {
                val num = item.optInt("num", idx + 1)
                val startTime = item.optString("starttime", "")
                val endTime = item.optString("endtime", "")
                val subject = item.optString("name", "Урок")
                val room = item.optString("room", "")
                val teacher = item.optString("teacher", "")
                val topic = item.optString("topic", "")

                val marksList = mutableListOf<Mark>()
                val assessmentsArr = item.optJSONArray("assessments") ?: item.optJSONArray("marks")
                val assessmentsObj = item.optJSONObject("assessments") ?: item.optJSONObject("marks")

                if (assessmentsArr != null) {
                    for (i in 0 until assessmentsArr.length()) {
                        val a = assessmentsArr.optJSONObject(i)
                        if (a != null) {
                            marksList.add(
                                Mark(
                                    value = a.optString("value", ""),
                                    weight = a.optInt("weight", 1),
                                    comment = if (a.has("comment") && !a.isNull("comment")) a.getString("comment") else null,
                                    isControl = a.optBoolean("is_control", false)
                                )
                            )
                        } else {
                            val v = assessmentsArr.optString(i, "").trim()
                            if (v.isNotEmpty()) marksList.add(Mark(value = v))
                        }
                    }
                } else if (assessmentsObj != null) {
                    for (k in assessmentsObj.keys()) {
                        val a = assessmentsObj.optJSONObject(k)
                        if (a != null) {
                            marksList.add(
                                Mark(
                                    value = a.optString("value", ""),
                                    weight = a.optInt("weight", 1),
                                    comment = if (a.has("comment") && !a.isNull("comment")) a.getString("comment") else null,
                                    isControl = a.optBoolean("is_control", false)
                                )
                            )
                        } else {
                            val v = assessmentsObj.optString(k, "").trim()
                            if (v.isNotEmpty()) marksList.add(Mark(value = v))
                        }
                    }
                }

                var homeworkText: String? = null
                val hwKeys = listOf("homework", "task", "hometask", "homework_text")
                for (hk in hwKeys) {
                    if (item.has(hk)) {
                        val hwRaw = item.opt(hk)
                        if (hwRaw is String && hwRaw.isNotBlank()) {
                            homeworkText = hwRaw
                            break
                        } else if (hwRaw is JSONObject && hwRaw.length() > 0) {
                            val texts = mutableListOf<String>()
                            for (k in hwRaw.keys()) {
                                val hwItem = hwRaw.optJSONObject(k)
                                val v = hwItem?.optString("value") ?: hwRaw.optString(k)
                                if (!v.isNullOrBlank()) texts.add(v)
                            }
                            if (texts.isNotEmpty()) {
                                homeworkText = texts.joinToString("\n")
                                break
                            }
                        } else if (hwRaw is JSONArray && hwRaw.length() > 0) {
                            val texts = mutableListOf<String>()
                            for (i in 0 until hwRaw.length()) {
                                val hwItem = hwRaw.optJSONObject(i)
                                val v = hwItem?.optString("value") ?: hwRaw.optString(i)
                                if (!v.isNullOrBlank()) texts.add(v)
                            }
                            if (texts.isNotEmpty()) {
                                homeworkText = texts.joinToString("\n")
                                break
                            }
                        }
                    }
                }

                val (cleanHw, attachments) = AttachmentExtractor.extract(homeworkText)
                lessons.add(
                    Lesson(
                        num = num,
                        startTime = startTime,
                        endTime = endTime,
                        subject = subject,
                        room = room,
                        teacher = teacher,
                        topic = if (topic.isNotEmpty()) topic else null,
                        homework = cleanHw,
                        marks = marksList,
                        attachments = attachments
                    )
                )
            }

            val displayDate = formatDisplayDate(name)
            val displayDayOfWeek = title.ifBlank { getDayOfWeekRussian(name) }.ifBlank { "День" }

            list.add(
                DaySchedule(
                    date = displayDate,
                    dayOfWeek = displayDayOfWeek,
                    lessons = lessons,
                    isHoliday = isHoliday,
                    holidayName = holidayName
                )
            )
        }
        return list
    }

    fun fetchDiaryFromWeb(
        cookies: String,
        studentId: String? = null,
        weekOffset: Int = 0
    ): Result<List<DaySchedule>> {
        return try {
            val cleanStudent = studentId?.removePrefix("u.")
            val targetDate = getMondayDateForOffset(weekOffset)

            val candidateUrls = mutableListOf<String>()
            val weekUrl = "$baseUrl/journal-app/week.$weekOffset"
            val fallbackUrl = if (weekOffset == 0) "$baseUrl/journal-app" else null

            if (!cleanStudent.isNullOrBlank()) {
                candidateUrls.add("$weekUrl/u.$cleanStudent")
                if (fallbackUrl != null) candidateUrls.add("$fallbackUrl/u.$cleanStudent")
            }
            candidateUrls.add(weekUrl)
            if (fallbackUrl != null) candidateUrls.add(fallbackUrl)

            val diaryActionWeekUrl = "$baseUrl/journal-student-diary-action/week.$weekOffset"
            val diaryActionFallbackUrl = if (weekOffset == 0) "$baseUrl/journal-student-diary-action" else null
            if (!cleanStudent.isNullOrBlank()) {
                candidateUrls.add("$diaryActionWeekUrl/u.$cleanStudent")
                if (diaryActionFallbackUrl != null) candidateUrls.add("$diaryActionFallbackUrl/u.$cleanStudent")
            }
            candidateUrls.add(diaryActionWeekUrl)
            if (diaryActionFallbackUrl != null) candidateUrls.add(diaryActionFallbackUrl)
            candidateUrls.add("$baseUrl/journal-app?date=$targetDate")

            for (targetUrl in candidateUrls) {
                val request = buildRequest(targetUrl, cookies, isJson = false)
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: continue

                checkSessionValidity(response, html)

                if (!response.isSuccessful || html.contains("<title>Ошибка") || html.contains("Страница не найдена")) {
                    logResponseError(request, response, html)
                    continue
                }

                // Check embedded JSON in script tags first
                val scriptDays = extractDiaryFromHtmlScripts(html, studentId)
                if (scriptDays.isNotEmpty()) {
                    return Result.success(scriptDays)
                }

                val doc = Jsoup.parse(html)
                val parsedDays = parseDiaryHtmlCards(doc)
                if (parsedDays.isNotEmpty()) {
                    return Result.success(parsedDays)
                }
            }

            Result.failure(Exception("Не удалось извлечь расписание со страницы дневника"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDiaryHtmlCards(doc: org.jsoup.nodes.Document): List<DaySchedule> {
        val dayElements = doc.select("div.dnevnik-day, div.m-journal__day, div.journal-day, div.day-card, div.dnevnik__day, div.journal-app__day, .day_block, .day-container, div.pane-day, .day")
        val list = mutableListOf<DaySchedule>()

        for (dayElem in dayElements) {
            val titleElem = dayElem.selectFirst("div.dnevnik-day__title, .day-title, .m-journal__day-header, .dnevnik__day-title, .day-header, .header, h3, h4, .title") ?: continue
            val titleText = titleElem.text().trim()

            var dayOfWeek = ""
            var date = ""
            if (titleText.contains(",")) {
                val parts = titleText.split(",").map { it.trim() }
                val ruDays = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
                if (ruDays.any { parts[0].contains(it, ignoreCase = true) }) {
                    dayOfWeek = parts[0]
                    date = parts.getOrElse(1) { "" }
                } else {
                    date = parts[0]
                    dayOfWeek = parts.getOrElse(1) { "" }
                }
            } else {
                dayOfWeek = getDayOfWeekRussian(titleText).ifBlank { titleText }
                date = titleText
            }

            val isHoliday = dayElem.selectFirst(".holiday, .weekend, .vacation, .is-holiday") != null ||
                    dayElem.text().contains("Каникулы", ignoreCase = true) ||
                    dayElem.text().contains("Выходной день", ignoreCase = true)
            val holidayName = if (isHoliday) "Каникулы / Выходной" else null

            val lessons = mutableListOf<Lesson>()
            val lessonElems = dayElem.select("div.dnevnik-lesson, div.m-journal__lesson, div.lesson-item, tr.lesson-row, div.journal-lesson, div.dnevnik__lesson, div.lesson, tr.lesson")

            for ((idx, lElem) in lessonElems.withIndex()) {
                val num = lElem.selectFirst(".dnevnik-lesson__number, .lesson-number, .num, .number, td.num")
                    ?.text()?.replace(".", "")?.filter { it.isDigit() }?.toIntOrNull() ?: (idx + 1)

                val time = lElem.selectFirst(".dnevnik-lesson__time, .lesson-time, .time, td.time, .clock")?.text()?.trim() ?: ""
                val subject = lElem.selectFirst(".js-rt_licey-dnevnik-subject, .lesson-subject, .subject-title, .dnevnik-lesson__name, span.subject, .subject, .name, td.subject")
                    ?.text()?.trim() ?: "Урок"

                val roomElem = lElem.selectFirst(".dnevnik-lesson__room, .lesson-room, .room, .cabinet, td.room, .dnevnik-lesson__cabinet")
                val roomText = roomElem?.text()?.trim() ?: run {
                    val full = lElem.text()
                    Regex("""каб\.?\s*([0-9А-Яа-яA-Za-z]+)""").find(full)?.groupValues?.get(1) ?: ""
                }

                val teacher = lElem.selectFirst(".dnevnik-lesson__teacher, .lesson-teacher, .teacher, .fio, td.teacher")?.text()?.trim() ?: ""
                val topic = lElem.selectFirst(".dnevnik-lesson__topic, .lesson-topic, .topic, .theme, td.topic")?.text()?.trim()
                val taskElem = lElem.selectFirst(".dnevnik-lesson__task, .lesson-task, .homework-text, .task-description, .homework, .task, .hometask, td.task")
                val rawTaskHtml = taskElem?.html() ?: taskElem?.text() ?: ""
                val (cleanTask, attachments) = AttachmentExtractor.extract(rawTaskHtml)

                val markElems = lElem.select(".dnevnik-mark, .mark-badge, .grade, .lesson-mark, .mark, span.cell-mark, .mark_val, .badge")
                val marks = mutableListOf<Mark>()
                for (mElem in markElems) {
                    val mVal = mElem.ownText().ifBlank { mElem.text() }.trim()
                    if (mVal.isNotBlank() && mVal.length <= 4) {
                        val subWeight = mElem.selectFirst("sub")?.text()?.toIntOrNull()
                            ?: mElem.selectFirst("sup")?.text()?.toIntOrNull()
                            ?: mElem.attr("data-weight").toIntOrNull()
                            ?: Regex("""вес[:\s]+(\d+)""", RegexOption.IGNORE_CASE).find(mElem.attr("title"))?.groupValues?.get(1)?.toIntOrNull()
                            ?: 1
                        val comment = mElem.attr("title").ifBlank { null }
                        val isCtrl = subWeight > 1 || (comment?.contains("контроль", true) == true)
                        marks.add(Mark(value = mVal, weight = subWeight, comment = comment, isControl = isCtrl))
                    }
                }

                val (startTime, endTime) = if (time.contains("-") || time.contains("–")) {
                    val times = time.split(Regex("[-–]"))
                    times[0].trim() to times.getOrElse(1) { "" }.trim()
                } else {
                    time to ""
                }

                lessons.add(
                    Lesson(
                        num = num,
                        startTime = startTime,
                        endTime = endTime,
                        subject = subject,
                        room = roomText,
                        teacher = teacher,
                        topic = topic?.takeIf { it.isNotBlank() },
                        homework = cleanTask?.takeIf { it.isNotBlank() },
                        marks = marks,
                        attachments = attachments
                    )
                )
            }

            list.add(
                DaySchedule(
                    date = date.ifBlank { titleText },
                    dayOfWeek = dayOfWeek.ifBlank { "День" },
                    lessons = lessons,
                    isHoliday = isHoliday,
                    holidayName = holidayName
                )
            )
        }

        return list
    }

    private fun extractMarksFromHtmlScripts(html: String, studentId: String?): List<SubjectSummary> {
        val patterns = listOf(
            Regex("""(?:gon\.marks|gon\.grades|gon\.students)\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),
            Regex("""(?:gon|window\.gon)\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL)
        )
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                try {
                    val jsonStr = match.groupValues[1]
                    val json = JSONObject(jsonStr)
                    val parsed = parseApiMarks(json, studentId)
                    if (parsed.isNotEmpty()) return parsed
                } catch (_: Exception) {}
            }
        }
        return emptyList()
    }

    private fun extractDiaryFromHtmlScripts(html: String, studentId: String?): List<DaySchedule> {
        val patterns = listOf(
            Regex("""(?:gon\.days|gon\.diary|gon\.schedule)\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),
            Regex("""(?:gon|window\.gon)\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL)
        )
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                try {
                    val jsonStr = match.groupValues[1]
                    val json = JSONObject(jsonStr)
                    val parsed = parseApiDiaryDays(json, studentId)
                    if (parsed.isNotEmpty()) return parsed
                } catch (_: Exception) {}
            }
        }
        return emptyList()
    }

    fun groupMarksIntoPeriods(allMarks: List<Mark>, subjectFinalMark: String? = null): List<PeriodMarks> {
        val q1Marks = mutableListOf<Mark>()
        val q2Marks = mutableListOf<Mark>()
        val q3Marks = mutableListOf<Mark>()
        val q4Marks = mutableListOf<Mark>()

        for (m in allMarks) {
            val month = extractMonthFromDate(m.date)
            when (month) {
                9, 10 -> q1Marks.add(m)
                11, 12 -> q2Marks.add(m)
                1, 2, 3 -> q3Marks.add(m)
                4, 5, 6 -> q4Marks.add(m)
                else -> {
                    q1Marks.add(m)
                }
            }
        }

        fun calcAvg(marks: List<Mark>): Float {
            val numMarks = marks.filter { it.numericValue != null }
            val totalWeight = numMarks.sumOf { it.weight }
            return if (totalWeight > 0) {
                (numMarks.sumOf { ((it.numericValue ?: 0f) * it.weight).toDouble() } / totalWeight).toFloat()
            } else 0f
        }

        return listOf(
            PeriodMarks("1", "1 четверть", q1Marks, calcAvg(q1Marks), null),
            PeriodMarks("2", "2 четверть", q2Marks, calcAvg(q2Marks), null),
            PeriodMarks("3", "3 четверть", q3Marks, calcAvg(q3Marks), null),
            PeriodMarks("4", "4 четверть", q4Marks, calcAvg(q4Marks), subjectFinalMark)
        )
    }

    private fun extractMonthFromDate(dateStr: String): Int {
        if (dateStr.isBlank()) return 0
        val parts = dateStr.split(".")
        return if (parts.size >= 2) {
            parts[1].toIntOrNull() ?: 0
        } else {
            Regex("""\.(\d{2})""").find(dateStr)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
    }
}

object AttachmentExtractor {
    private const val BASE_URL = "https://edu.rk.gov.ru"
    private val URL_REGEX = Regex("""https?://[^\s<>"]+""")

    fun extract(rawHtmlOrText: String?): Pair<String?, List<Attachment>> {
        if (rawHtmlOrText.isNullOrBlank()) return null to emptyList()

        val attachments = mutableListOf<Attachment>()
        val seenUrls = mutableSetOf<String>()

        fun normalizeUrl(rawUrl: String): String {
            var url = rawUrl.trim()
            if (url.startsWith("/")) {
                url = "$BASE_URL$url"
            } else if (url.startsWith("http://edu.rk.gov.ru")) {
                url = "https://edu.rk.gov.ru" + url.removePrefix("http://edu.rk.gov.ru")
            }
            return url
        }

        fun isFileUrl(url: String): Boolean {
            return url.contains("/storage/") ||
                    url.contains("/files/") ||
                    url.contains("/download") ||
                    url.endsWith(".pdf", true) ||
                    url.endsWith(".doc", true) ||
                    url.endsWith(".docx", true) ||
                    url.endsWith(".xls", true) ||
                    url.endsWith(".xlsx", true) ||
                    url.endsWith(".ppt", true) ||
                    url.endsWith(".pptx", true) ||
                    url.endsWith(".zip", true)
        }

        fun extractTitle(text: String, url: String, isFile: Boolean): String {
            if (text.isNotBlank() && !text.startsWith("http") && !text.startsWith("/")) {
                return text
            }
            if (isFile) {
                val rawFilename = url.substringAfterLast('/').substringBefore('?')
                return try {
                    java.net.URLDecoder.decode(rawFilename, "UTF-8").ifBlank { "Файл к уроку" }
                } catch (_: Exception) {
                    rawFilename.ifBlank { "Файл к уроку" }
                }
            }
            return extractDomain(url)
        }

        var cleanText: String = rawHtmlOrText.trim()

        if (rawHtmlOrText.contains("<") && rawHtmlOrText.contains(">")) {
            try {
                val doc = Jsoup.parseBodyFragment(rawHtmlOrText)
                val links = doc.select("a[href]")
                for (a in links) {
                    val href = normalizeUrl(a.attr("href"))
                    val linkText = a.text().trim()
                    if (href.isNotBlank() && !seenUrls.contains(href)) {
                        seenUrls.add(href)
                        val isFile = isFileUrl(href)
                        val type = if (isFile) AttachmentType.FILE else AttachmentType.LINK
                        val title = extractTitle(linkText, href, isFile)
                        attachments.add(Attachment(title = title, url = href, type = type))
                    }
                }

                // Remove <a> tags to prevent duplicate regex matching of the same URLs from tags/attributes
                doc.select("a").remove()
                val remainingText = doc.text().trim()
                cleanText = Jsoup.parseBodyFragment(rawHtmlOrText).text().trim()

                // Scan remaining text for any standalone plaintext URLs
                for (match in URL_REGEX.findAll(remainingText)) {
                    val plainUrl = normalizeUrl(match.value.trimEnd('.', ',', ';', ')'))
                    if (plainUrl.isNotBlank() && !seenUrls.contains(plainUrl)) {
                        seenUrls.add(plainUrl)
                        val isFile = isFileUrl(plainUrl)
                        val type = if (isFile) AttachmentType.FILE else AttachmentType.LINK
                        val title = extractTitle("", plainUrl, isFile)
                        attachments.add(Attachment(title = title, url = plainUrl, type = type))
                    }
                }
            } catch (_: Exception) {
                cleanText = rawHtmlOrText.trim()
            }
        } else {
            for (match in URL_REGEX.findAll(rawHtmlOrText)) {
                val plainUrl = normalizeUrl(match.value.trimEnd('.', ',', ';', ')'))
                if (plainUrl.isNotBlank() && !seenUrls.contains(plainUrl)) {
                    seenUrls.add(plainUrl)
                    val isFile = isFileUrl(plainUrl)
                    val type = if (isFile) AttachmentType.FILE else AttachmentType.LINK
                    val title = extractTitle("", plainUrl, isFile)
                    attachments.add(Attachment(title = title, url = plainUrl, type = type))
                }
            }
        }

        val distinctAttachments = attachments.distinctBy { it.url }
        return (cleanText.ifBlank { null }) to distinctAttachments
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: url
            host.removePrefix("www.")
        } catch (_: Exception) {
            url.take(25)
        }
    }
}
