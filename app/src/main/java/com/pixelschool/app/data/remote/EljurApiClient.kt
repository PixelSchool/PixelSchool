package com.pixelschool.app.data.remote

import android.util.Log
import com.pixelschool.app.data.model.AcademicPeriod
import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Lesson
import com.pixelschool.app.data.model.Mark
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

class EljurApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    private val baseUrl = "https://edu.rk.gov.ru"
    private val devKey = "19c4bfc2705023fe080ce94ace26aec9"
    private val browserUserAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    private fun extractUserVendor(json: JSONObject, fallbackUserName: String?): UserSession? {
        val respObj = json.optJSONObject("response") ?: json
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
        return null
    }

    fun enrichWithRules(session: UserSession): UserSession {
        return try {
            val vendorParam = if (session.vendor.isNotBlank()) "&vendor=${session.vendor}" else ""
            val url = "$baseUrl/apiv3/getrules?devkey=$devKey&out_format=json&auth_token=${session.token}$vendorParam"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (resp.isSuccessful && !body.isNullOrEmpty()) {
                val json = JSONObject(body)
                val respObj = json.optJSONObject("response") ?: json
                val result = respObj.optJSONObject("result") ?: respObj
                val relations = result.optJSONObject("relations")

                val studentsObj = relations?.optJSONObject("students")
                val studentsArr = relations?.optJSONArray("students")

                var realStudentId = ""
                var studentTitle: String? = null

                if (studentsObj != null && studentsObj.length() > 0) {
                    val firstKey = studentsObj.keys().asSequence().firstOrNull()
                    val st = if (firstKey != null) studentsObj.optJSONObject(firstKey) else null
                    realStudentId = st?.optString("name")?.ifBlank { null }
                        ?: st?.optString("id")?.ifBlank { null }
                        ?: firstKey ?: ""
                    studentTitle = st?.optString("title")?.ifBlank { null }
                } else if (studentsArr != null && studentsArr.length() > 0) {
                    val st = studentsArr.optJSONObject(0)
                    realStudentId = st?.optString("name")?.ifBlank { null }
                        ?: st?.optString("id")?.ifBlank { null }
                        ?: ""
                    studentTitle = st?.optString("title")?.ifBlank { null }
                }

                if (realStudentId.isBlank()) {
                    realStudentId = result.optString("name").ifBlank { null }
                        ?: result.optString("id").ifBlank { null }
                        ?: ""
                }

                if (studentTitle.isNullOrBlank()) {
                    studentTitle = result.optString("title").ifBlank { null }
                        ?: session.userTitle
                }

                val realSchool = result.optString("school").ifBlank { null }
                    ?: session.schoolTitle

                session.copy(
                    studentId = realStudentId,
                    userTitle = studentTitle,
                    schoolTitle = realSchool
                )
            } else {
                session
            }
        } catch (_: Exception) {
            session
        }
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
            // Flow 1: If v_token is provided (standard official flow=v2), call getusersvendors
            val vToken = auth?.vToken
            if (!vToken.isNullOrBlank()) {
                val url = "$baseUrl/apiv3/getusersvendors?devkey=$devKey&out_format=json&v_token=$vToken"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", browserUserAgent)
                    .apply {
                        if (!cookies.isNullOrEmpty()) addHeader("Cookie", cookies)
                    }.build()

                val resp = client.newCall(req).execute()
                val body = resp.body?.string()
                if (resp.isSuccessful && !body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val session = extractUserVendor(json, fallbackUserName)
                    if (session != null) {
                        val enriched = enrichWithRules(session)
                        return@withContext Result.success(enriched)
                    }
                }
            }

            // Flow 2: Direct token and school vendor intercepted from URL redirect
            if (auth != null && !auth.token.isNullOrBlank() && auth.token != "web_session" && !auth.vendor.isNullOrBlank()) {
                val baseSession = UserSession(
                    token = auth.token,
                    vendor = auth.vendor,
                    login = auth.login ?: "",
                    userTitle = fallbackUserName ?: "Ученик",
                    schoolTitle = "ГИС РК «Образование»"
                )
                val enriched = enrichWithRules(baseSession)
                return@withContext Result.success(enriched)
            }

            // Flow 3: Session cookies present
            if (!cookies.isNullOrEmpty()) {
                // Try getusersvendors with cookies
                val cookieReq = Request.Builder()
                    .url("$baseUrl/apiv3/getusersvendors?devkey=$devKey&out_format=json")
                    .header("User-Agent", browserUserAgent)
                    .addHeader("Cookie", cookies)
                    .build()
                val cookieResp = client.newCall(cookieReq).execute()
                val cookieBody = cookieResp.body?.string()
                if (cookieResp.isSuccessful && !cookieBody.isNullOrEmpty()) {
                    val json = JSONObject(cookieBody)
                    val session = extractUserVendor(json, fallbackUserName)
                    if (session != null) {
                        val enriched = enrichWithRules(session)
                        return@withContext Result.success(enriched)
                    }
                }

                // Scrape profile info from /journal-app
                val profileResult = scrapeUserProfile(cookies)
                val finalName = profileResult.first.ifBlank { fallbackUserName ?: "Ученик" }
                val finalSchool = profileResult.second.ifBlank { "ГИС РК «Образование»" }
                return@withContext Result.success(
                    UserSession(
                        token = "web_session",
                        vendor = "edu",
                        login = "",
                        userTitle = finalName,
                        schoolTitle = finalSchool
                    )
                )
            }

            Result.failure(Exception("Не удалось авторизовать пользователя в региональном дневнике. Попробуйте войти снова."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scrapeUserProfile(cookies: String): Pair<String, String> {
        return try {
            val req = Request.Builder()
                .url("$baseUrl/journal-app")
                .header("User-Agent", browserUserAgent)
                .addHeader("Cookie", cookies)
                .build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return "" to ""
            val doc = Jsoup.parse(html)
            val name = doc.selectFirst(".user-name, .b-dropdown__user, .header-user__name, .m-user__name, .user_name, .user-profile__name")?.text()?.trim() ?: ""
            val school = doc.selectFirst(".school-title, .header-school__title, .m-logo__subtitle, .school_title")?.text()?.trim() ?: "Республика Крым"
            name to school
        } catch (_: Exception) {
            "" to ""
        }
    }

    fun getCurrentWeekRange(date: Date = Date()): String {
        return getWeekRangeForOffset(0, date)
    }

    fun getWeekRangeForOffset(weekOffset: Int = 0, baseDate: Date = Date()): String {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.time = baseDate
        if (weekOffset != 0) {
            cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
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

    fun formatWeekRangeDisplay(range: String): String {
        val parts = range.split("-")
        if (parts.size == 2 && parts[0].length == 8 && parts[1].length == 8) {
            val d1 = "${parts[0].substring(6, 8)}.${parts[0].substring(4, 6)}"
            val d2 = "${parts[1].substring(6, 8)}.${parts[1].substring(4, 6)}"
            return "$d1 – $d2"
        }
        return range
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
        studentId: String? = null
    ): Result<List<AcademicPeriod>> = withContext(Dispatchers.IO) {
        try {
            if (token.isNotBlank() && token != "web_session") {
                val vendorParam = if (vendor.isNotBlank()) "&vendor=$vendor" else ""
                val urls = mutableListOf<String>()
                if (!studentId.isNullOrBlank()) {
                    urls.add("$baseUrl/apiv3/getperiods?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&weeks=true&show_disabled=true&student=$studentId")
                }
                urls.add("$baseUrl/apiv3/getperiods?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&weeks=true&show_disabled=true")

                for (url in urls) {
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .header("User-Agent", browserUserAgent)
                            .build()
                        val resp = client.newCall(req).execute()
                        val body = resp.body?.string()
                        if (resp.isSuccessful && !body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val respObj = json.optJSONObject("response") ?: json
                            if (respObj.optInt("state", 200) == 200) {
                                val periods = parsePeriodsJson(respObj, studentId)
                                if (periods.isNotEmpty()) {
                                    return@withContext Result.success(periods)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("EljurApiClient", "fetchPeriods error: ${e.message} for $url")
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
        cookies: String? = null
    ): Result<List<DaySchedule>> = withContext(Dispatchers.IO) {
        try {
            if (token.isNotBlank() && token != "web_session") {
                val targetDays = if (!days.isNullOrBlank()) days else getCurrentWeekRange()
                val vendorParam = if (vendor.isNotBlank()) "&vendor=$vendor" else ""

                // Multi-tier candidate URLs:
                // 1. getdiary with days + student (if known)
                // 2. getdiary with days (without student)
                // 3. getschedule with days + student
                // 4. getschedule with days
                // 5. getschedule base (without days)
                val candidateUrls = mutableListOf<String>()

                if (!studentId.isNullOrBlank()) {
                    candidateUrls.add("$baseUrl/apiv3/getdiary?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&student=$studentId&days=$targetDays&rings=true")
                }
                candidateUrls.add("$baseUrl/apiv3/getdiary?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&days=$targetDays&rings=true")

                if (!studentId.isNullOrBlank()) {
                    candidateUrls.add("$baseUrl/apiv3/getschedule?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&student=$studentId&days=$targetDays&rings=true")
                }
                candidateUrls.add("$baseUrl/apiv3/getschedule?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&days=$targetDays&rings=true")
                candidateUrls.add("$baseUrl/apiv3/getschedule?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&rings=true")

                var lastApiError: String? = null

                for (url in candidateUrls) {
                    try {
                        val reqBuilder = Request.Builder()
                            .url(url)
                            .header("User-Agent", browserUserAgent)
                        if (!cookies.isNullOrEmpty()) {
                            reqBuilder.addHeader("Cookie", cookies)
                        }

                        val response = client.newCall(reqBuilder.build()).execute()
                        val body = response.body?.string()

                        if (response.isSuccessful && !body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val respObj = json.optJSONObject("response") ?: json
                            if (respObj.optInt("state", 200) == 200) {
                                val parsedDays = parseApiDiaryDays(respObj)
                                if (parsedDays.isNotEmpty()) {
                                    return@withContext Result.success(parsedDays)
                                }
                            } else {
                                val err = respObj.optJSONObject("error")?.optString("message")
                                    ?: "state=${respObj.optInt("state")}"
                                lastApiError = err
                                Log.w("EljurApiClient", "Diary API call returned error: $err for $url")
                            }
                        } else {
                            val err = "HTTP ${response.code}: ${body?.take(150)}"
                            lastApiError = err
                            Log.w("EljurApiClient", "Diary API HTTP failure: $err for $url")
                        }
                    } catch (e: Exception) {
                        lastApiError = e.message
                        Log.w("EljurApiClient", "Exception querying diary API: ${e.message}")
                    }
                }

                if (lastApiError != null) {
                    Log.e("EljurApiClient", "All candidate diary API URLs failed. Last error: $lastApiError")
                }
            }

            // Web fallback: parse /journal-app/week.0 with user session cookies
            if (!cookies.isNullOrEmpty()) {
                val webDays = fetchDiaryFromWeb(cookies)
                if (webDays.isSuccess && webDays.getOrNull()?.isNotEmpty() == true) {
                    return@withContext webDays
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
            if (token.isNotBlank() && token != "web_session") {
                val vendorParam = if (vendor.isNotBlank()) "&vendor=$vendor" else ""

                val targetRanges = mutableListOf<String>()
                if (!days.isNullOrBlank()) {
                    targetRanges.add(days)
                }

                // If days not explicitly specified or as extra candidate, query periods
                val periodsRes = fetchPeriods(token, vendor, studentId).getOrNull()
                val activePeriod = periodsRes?.firstOrNull { it.isActive } ?: periodsRes?.firstOrNull()
                if (activePeriod != null && activePeriod.rangeDays !in targetRanges) {
                    targetRanges.add(activePeriod.rangeDays)
                }

                // Academic year quarter/term fallback ranges
                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                val schoolYearStart = if (month >= Calendar.SEPTEMBER) currentYear else currentYear - 1
                val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
                val todayStr = sdf.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 30)
                val futureStr = sdf.format(cal.time)

                val fullTermRange = "${schoolYearStart}0901-$futureStr"
                if (fullTermRange !in targetRanges) targetRanges.add(fullTermRange)

                val yearToDateRange = "${schoolYearStart}0901-$todayStr"
                if (yearToDateRange !in targetRanges) targetRanges.add(yearToDateRange)

                val currentWeek = getCurrentWeekRange()
                if (currentWeek !in targetRanges) targetRanges.add(currentWeek)

                // Build candidate URLs
                val candidateUrls = mutableListOf<String>()
                for (range in targetRanges) {
                    if (!studentId.isNullOrBlank()) {
                        candidateUrls.add("$baseUrl/apiv3/getmarks?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&student=$studentId&days=$range")
                    }
                    candidateUrls.add("$baseUrl/apiv3/getmarks?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&days=$range")
                }

                if (!studentId.isNullOrBlank()) {
                    candidateUrls.add("$baseUrl/apiv3/getmarks?devkey=$devKey&out_format=json&auth_token=$token$vendorParam&student=$studentId")
                }
                candidateUrls.add("$baseUrl/apiv3/getmarks?devkey=$devKey&out_format=json&auth_token=$token$vendorParam")

                for (url in candidateUrls) {
                    try {
                        val reqBuilder = Request.Builder()
                            .url(url)
                            .header("User-Agent", browserUserAgent)
                        if (!cookies.isNullOrEmpty()) {
                            reqBuilder.addHeader("Cookie", cookies)
                        }

                        val response = client.newCall(reqBuilder.build()).execute()
                        val body = response.body?.string()

                        if (response.isSuccessful && !body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val respObj = json.optJSONObject("response") ?: json
                            if (respObj.optInt("state", 200) == 200) {
                                val marks = parseApiMarks(respObj, studentId)
                                if (marks.isNotEmpty()) {
                                    Log.d("EljurApiClient", "Successfully fetched ${marks.size} subjects from $url")
                                    return@withContext Result.success(marks)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("EljurApiClient", "Marks API candidate failed: ${e.message} for $url")
                    }
                }
            }

            // Web fallback
            if (!cookies.isNullOrEmpty()) {
                val webMarks = scrapeMarksFromWeb(cookies)
                if (webMarks.isSuccess && webMarks.getOrNull()?.isNotEmpty() == true) {
                    return@withContext webMarks
                }
            }

            Result.failure(Exception("Не удалось загрузить оценки"))
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

        if (studentsObj != null && studentsObj.length() > 0) {
            if (!filterStudentId.isNullOrBlank() && studentsObj.has(filterStudentId)) {
                studentObj = studentsObj.optJSONObject(filterStudentId)
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
                    if (candidate != null && (candidate.optString("name") == filterStudentId || candidate.optString("id") == filterStudentId)) {
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
                    finalMark = finalMark
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

    fun scrapeMarksFromWeb(cookies: String): Result<List<SubjectSummary>> {
        return try {
            val candidateUrls = listOf(
                "$baseUrl/journal-student",
                "$baseUrl/journal-app/itog",
                "$baseUrl/journal-app/marks",
                "$baseUrl/journal-app"
            )

            for (targetUrl in candidateUrls) {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", browserUserAgent)
                    .addHeader("Cookie", cookies)
                    .build()
                val resp = client.newCall(req).execute()
                val html = resp.body?.string() ?: continue
                val doc = Jsoup.parse(html)

                val rows = doc.select("tr.journal-table__row, tr.marks-row, tr.dnevnik-row, table.journal tr, table.dnevnik tr")
                if (rows.isEmpty()) continue

                val list = mutableListOf<SubjectSummary>()
                for (row in rows) {
                    val subjectElem = row.selectFirst(".subject, .lesson-name, td:first-child, .subject-title") ?: continue
                    val subjectName = subjectElem.text().trim()
                    if (subjectName.isBlank() || subjectName.contains("Предмет", true) || subjectName.length < 2) continue

                    val markElements = row.select(".mark, .grade, .mark-badge, span.cell-mark, .mark_val")
                    val marks = mutableListOf<Mark>()
                    for (mElem in markElements) {
                        val mVal = mElem.text().trim()
                        if (mVal.isNotBlank() && mVal.length <= 3) {
                            marks.add(Mark(value = mVal))
                        }
                    }

                    val avgElem = row.selectFirst(".average, .avg, .average-grade, td:last-child")
                    val avgStr = avgElem?.text()?.replace(',', '.')?.trim()
                    val avgFloat = avgStr?.toFloatOrNull() ?: if (marks.isNotEmpty()) {
                        val num = marks.mapNotNull { it.numericValue }
                        if (num.isNotEmpty()) num.average().toFloat() else 0f
                    } else 0f

                    if (marks.isNotEmpty() || avgFloat > 0f) {
                        list.add(
                            SubjectSummary(
                                subject = subjectName,
                                average = avgFloat,
                                marks = marks
                            )
                        )
                    }
                }

                if (list.isNotEmpty()) {
                    return Result.success(list.sortedByDescending { it.average })
                }
            }

            Result.failure(Exception("Пустая HTML страница оценок"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseApiDiaryDays(respObj: JSONObject): List<DaySchedule> {
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
            val targetStudentObj = if (studentsObj != null && studentsObj.length() > 0) {
                var found: JSONObject? = null
                for (k in studentsObj.keys()) {
                    val s = studentsObj.optJSONObject(k)
                    if (s != null && (s.has("days") || s.has("items"))) {
                        found = s
                        break
                    }
                }
                found ?: studentsObj.optJSONObject(studentsObj.keys().asSequence().first())
            } else if (studentsArr != null && studentsArr.length() > 0) {
                var found: JSONObject? = null
                for (i in 0 until studentsArr.length()) {
                    val s = studentsArr.optJSONObject(i)
                    if (s != null && (s.has("days") || s.has("items"))) {
                        found = s
                        break
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

            // Items can be JSONObject: {"1": {...}} or JSONArray: [{...}]
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
                if (assessmentsArr != null) {
                    for (i in 0 until assessmentsArr.length()) {
                        val a = assessmentsArr.optJSONObject(i) ?: continue
                        marksList.add(
                            Mark(
                                value = a.optString("value", ""),
                                weight = a.optInt("weight", 1),
                                comment = if (a.has("comment") && !a.isNull("comment")) a.getString("comment") else null,
                                isControl = a.optBoolean("is_control", false)
                            )
                        )
                    }
                }

                var homeworkText: String? = null
                if (item.has("homework")) {
                    val hwRaw = item.opt("homework")
                    if (hwRaw is String && hwRaw.isNotBlank()) {
                        homeworkText = hwRaw
                    } else if (hwRaw is JSONObject && hwRaw.length() > 0) {
                        val texts = mutableListOf<String>()
                        for (k in hwRaw.keys()) {
                            val hwItem = hwRaw.optJSONObject(k)
                            val v = hwItem?.optString("value")
                            if (!v.isNullOrBlank()) texts.add(v)
                        }
                        if (texts.isNotEmpty()) homeworkText = texts.joinToString("\n")
                    } else if (hwRaw is JSONArray && hwRaw.length() > 0) {
                        val texts = mutableListOf<String>()
                        for (i in 0 until hwRaw.length()) {
                            val hwItem = hwRaw.optJSONObject(i)
                            val v = hwItem?.optString("value") ?: hwRaw.optString(i)
                            if (!v.isNullOrBlank()) texts.add(v)
                        }
                        if (texts.isNotEmpty()) homeworkText = texts.joinToString("\n")
                    }
                }

                lessons.add(
                    Lesson(
                        num = num,
                        startTime = startTime,
                        endTime = endTime,
                        subject = subject,
                        room = room,
                        teacher = teacher,
                        topic = if (topic.isNotEmpty()) topic else null,
                        homework = homeworkText,
                        marks = marksList
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

    fun fetchDiaryFromWeb(cookies: String): Result<List<DaySchedule>> {
        return try {
            val candidateUrls = listOf(
                "$baseUrl/journal-app/week.0",
                "$baseUrl/journal-app",
                "$baseUrl/journal-student"
            )

            for (targetUrl in candidateUrls) {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", browserUserAgent)
                    .addHeader("Cookie", cookies)
                    .build()
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: continue
                val doc = Jsoup.parse(html)
                val dayElements = doc.select("div.dnevnik-day, div.m-journal__day, div.journal-day, div.day-card, div.dnevnik__day")
                if (dayElements.isEmpty()) continue

                val list = mutableListOf<DaySchedule>()
                for (dayElem in dayElements) {
                    val titleElem = dayElem.selectFirst("div.dnevnik-day__title, .day-title, .m-journal__day-header, .dnevnik__day-title") ?: continue
                    val parts = titleElem.text().split(", ")
                    val dayOfWeek = parts.getOrNull(0) ?: ""
                    val date = parts.getOrNull(1) ?: parts.getOrNull(0) ?: ""

                    val lessons = mutableListOf<Lesson>()
                    val lessonElems = dayElem.select("div.dnevnik-lesson, div.m-journal__lesson, div.lesson-item, tr.lesson-row, div.journal-lesson, div.dnevnik__lesson")
                    for (lElem in lessonElems) {
                        val num = lElem.selectFirst(".dnevnik-lesson__number, .lesson-number, .num")?.text()?.replace(".", "")?.trim()?.toIntOrNull() ?: 1
                        val time = lElem.selectFirst(".dnevnik-lesson__time, .lesson-time, .time")?.text()?.trim() ?: ""
                        val subject = lElem.selectFirst(".js-rt_licey-dnevnik-subject, .lesson-subject, .subject-title, .dnevnik-lesson__name, span.subject")?.text()?.trim() ?: "Урок"
                        val task = lElem.selectFirst(".dnevnik-lesson__task, .lesson-task, .homework-text, .task-description")?.text()?.trim()
                        val markVal = lElem.selectFirst(".dnevnik-mark, .mark-badge, .grade, .lesson-mark")?.text()?.trim()

                        val marks = if (!markVal.isNullOrEmpty()) listOf(Mark(value = markVal)) else emptyList()

                        val (startTime, endTime) = if (time.contains("-")) {
                            val times = time.split("-")
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
                                room = "",
                                teacher = "",
                                topic = null,
                                homework = task,
                                marks = marks
                            )
                        )
                    }

                    list.add(
                        DaySchedule(
                            date = date,
                            dayOfWeek = dayOfWeek,
                            lessons = lessons
                        )
                    )
                }

                if (list.isNotEmpty()) {
                    return Result.success(list)
                }
            }

            Result.failure(Exception("Пустая HTML страница дневника"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
