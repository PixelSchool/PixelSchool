package com.pixelschool.app.data.remote

import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Lesson
import com.pixelschool.app.data.model.Mark
import com.pixelschool.app.data.model.SubjectSummary
import com.pixelschool.app.data.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
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

    private fun enrichWithRules(session: UserSession): UserSession {
        return try {
            val url = "$baseUrl/apiv3/getrules?devkey=$devKey&out_format=json&auth_token=${session.token}&vendor=${session.vendor}"
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
                val students = relations?.optJSONObject("students")
                val firstStudentKey = students?.keys()?.asSequence()?.firstOrNull()
                val studentObj = if (firstStudentKey != null) students.optJSONObject(firstStudentKey) else null

                val realStudentId = studentObj?.optString("name", firstStudentKey ?: "") ?: (firstStudentKey ?: "")
                val studentTitle = studentObj?.optString("title")?.ifBlank { null }
                    ?: result.optString("title").ifBlank { null }
                    ?: session.userTitle

                session.copy(
                    studentId = realStudentId,
                    userTitle = studentTitle
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
            if (auth != null && !auth.token.isNullOrBlank() && !auth.vendor.isNullOrBlank()) {
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

    suspend fun fetchDiary(
        token: String,
        vendor: String,
        studentId: String? = null,
        days: String? = null,
        cookies: String? = null
    ): Result<List<DaySchedule>> = withContext(Dispatchers.IO) {
        try {
            if (token.isNotBlank() && token != "web_session") {
                val studentParam = if (!studentId.isNullOrBlank()) "&student=$studentId" else ""
                val daysParam = if (!days.isNullOrBlank()) "&days=$days" else ""
                val vendorParam = if (vendor.isNotBlank() && vendor != "edu") "&vendor=$vendor" else ""
                val url = "$baseUrl/apiv3/getdiary?devkey=$devKey&out_format=json&auth_token=$token$vendorParam$studentParam$daysParam&rings=true"

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
                        return@withContext Result.success(parsedDays)
                    } else {
                        val errorMsg = respObj.optJSONObject("error")?.optString("message")
                            ?: "Ошибка дневника (${respObj.optInt("state")})"
                        return@withContext Result.failure(Exception(errorMsg))
                    }
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
                val studentParam = if (!studentId.isNullOrBlank()) "&student=$studentId" else ""
                val daysParam = if (!days.isNullOrBlank()) "&days=$days" else ""
                val vendorParam = if (vendor.isNotBlank() && vendor != "edu") "&vendor=$vendor" else ""
                val url = "$baseUrl/apiv3/getmarks?devkey=$devKey&out_format=json&auth_token=$token$vendorParam$studentParam$daysParam"

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
                        val marks = parseApiMarks(respObj)
                        if (marks.isNotEmpty()) {
                            return@withContext Result.success(marks)
                        }
                    }
                }
            }

            Result.failure(Exception("Не удалось загрузить оценки"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseApiMarks(respObj: JSONObject): List<SubjectSummary> {
        val result = respObj.optJSONObject("result") ?: respObj
        val studentsObj = result.optJSONObject("students") ?: return emptyList()
        val firstStudentKey = studentsObj.keys().asSequence().firstOrNull() ?: return emptyList()
        val studentObj = studentsObj.optJSONObject(firstStudentKey) ?: return emptyList()
        val lessonsArr = studentObj.optJSONArray("lessons") ?: return emptyList()

        val list = mutableListOf<SubjectSummary>()
        for (i in 0 until lessonsArr.length()) {
            val lessonObj = lessonsArr.getJSONObject(i)
            val name = lessonObj.optString("name", "Предмет")
            val avgStr = lessonObj.optString("average", "0")
            val avgFloat = avgStr.toFloatOrNull() ?: 0f

            val marksList = mutableListOf<Mark>()
            val marksArr = lessonObj.optJSONArray("marks")
            if (marksArr != null) {
                for (j in 0 until marksArr.length()) {
                    val mObj = marksArr.getJSONObject(j)
                    val valStr = mObj.optString("value", "")
                    val weight = mObj.optInt("weight", 1)
                    val date = mObj.optString("date", "")
                    val comment = if (mObj.has("comment") && !mObj.isNull("comment")) mObj.getString("comment") else null
                    if (valStr.isNotEmpty()) {
                        marksList.add(
                            Mark(
                                value = valStr,
                                weight = weight,
                                date = date,
                                comment = comment
                            )
                        )
                    }
                }
            }

            list.add(
                SubjectSummary(
                    subject = name,
                    average = avgFloat,
                    marks = marksList
                )
            )
        }
        return list.sortedByDescending { it.average }
    }

    private fun parseApiDiaryDays(respObj: JSONObject): List<DaySchedule> {
        val result = respObj.optJSONObject("result") ?: respObj
        val daysObj = if (result.has("days")) {
            result.optJSONObject("days")
        } else {
            val students = result.optJSONObject("students") ?: return emptyList()
            val firstStudentKey = students.keys().asSequence().firstOrNull() ?: return emptyList()
            val studentObj = students.optJSONObject(firstStudentKey) ?: return emptyList()
            studentObj.optJSONObject("days")
        } ?: return emptyList()

        val list = mutableListOf<DaySchedule>()
        val dayKeys = daysObj.keys().asSequence().sorted().toList()
        for (dayKey in dayKeys) {
            val dayData = daysObj.getJSONObject(dayKey)
            val name = dayData.optString("name", dayKey)
            val title = dayData.optString("title", "")
            val holidayName = dayData.optString("holiday_name", "").ifBlank { null }
            val isHoliday = holidayName != null
            val itemsObj = dayData.optJSONObject("items") ?: JSONObject()

            val lessons = mutableListOf<Lesson>()
            val itemKeys = itemsObj.keys().asSequence().sorted().toList()
            for (itemKey in itemKeys) {
                val item = itemsObj.getJSONObject(itemKey)
                val num = item.optInt("num", itemKey.toIntOrNull() ?: 1)
                val startTime = item.optString("starttime", "")
                val endTime = item.optString("endtime", "")
                val subject = item.optString("name", "Урок")
                val room = item.optString("room", "")
                val teacher = item.optString("teacher", "")
                val topic = item.optString("topic", "")

                val marksList = mutableListOf<Mark>()
                val assessments = item.optJSONArray("assessments")
                if (assessments != null) {
                    for (i in 0 until assessments.length()) {
                        val a = assessments.getJSONObject(i)
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
                val hwObj = item.optJSONObject("homework")
                if (hwObj != null && hwObj.length() > 0) {
                    val firstHwKey = hwObj.keys().next()
                    val hwItem = hwObj.getJSONObject(firstHwKey)
                    homeworkText = if (hwItem.has("value") && !hwItem.isNull("value")) hwItem.getString("value") else null
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

            list.add(
                DaySchedule(
                    date = name,
                    dayOfWeek = title,
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
