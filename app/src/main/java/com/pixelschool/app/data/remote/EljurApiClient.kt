package com.pixelschool.app.data.remote

import com.pixelschool.app.data.model.DaySchedule
import com.pixelschool.app.data.model.Homework
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
        val arr = json.optJSONArray("result")
            ?: json.optJSONObject("response")?.optJSONArray("result")
            ?: json.optJSONObject("result")?.optJSONArray("response")
            ?: json.optJSONObject("response")?.optJSONArray("users")
        if (arr != null && arr.length() > 0) {
            val firstUser = arr.getJSONObject(0)
            return UserSession(
                token = firstUser.getString("token"),
                vendor = firstUser.optString("vendor", "edu"),
                login = firstUser.optString("login", ""),
                userTitle = firstUser.optString("user_title", fallbackUserName ?: "Ученик"),
                schoolTitle = firstUser.optString("vendor_title", "ГИС РК «Образование»")
            )
        }
        return null
    }

    /**
     * Resolves user session from OAuth code/JWT, v_token, or web session cookies.
     */
    suspend fun resolveSession(
        tokenOrVToken: String?,
        cookies: String?,
        fallbackUserName: String? = null
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        try {
            // Attempt 1: If we have an incoming token (JWT code, v_token, or auth token), try exchanging via getusersvendors
            if (!tokenOrVToken.isNullOrEmpty() && tokenOrVToken != "web_session") {
                val candidateUrls = listOf(
                    "$baseUrl/apiv3/getusersvendors?devkey=$devKey&out_format=json&v_token=$tokenOrVToken&auth_token=true",
                    "$baseUrl/apiv3/getusersvendors?devkey=$devKey&out_format=json&auth_token=$tokenOrVToken",
                    "$baseUrl/apiv3/getusersvendors?devkey=$devKey&out_format=json&code=$tokenOrVToken&auth_token=true"
                )

                for (url in candidateUrls) {
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .header("User-Agent", browserUserAgent)
                            .apply {
                                if (!cookies.isNullOrEmpty()) addHeader("Cookie", cookies)
                                if (TokenExtractor.isJwtToken(tokenOrVToken)) addHeader("Authorization", "Bearer $tokenOrVToken")
                            }.build()

                        val resp = client.newCall(req).execute()
                        val body = resp.body?.string()
                        if (resp.isSuccessful && !body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val session = extractUserVendor(json, fallbackUserName)
                            if (session != null) return@withContext Result.success(session)
                        }
                    } catch (_: Exception) {}
                }

                // Attempt 2: If token is a JWT (OAuth code / ESIA token) or persistent token (length >= 16)
                if (TokenExtractor.isJwtToken(tokenOrVToken) || tokenOrVToken.length >= 16) {
                    val jwtName = TokenExtractor.extractUserTitleFromJwt(tokenOrVToken)
                    val profileResult = if (!cookies.isNullOrEmpty()) scrapeUserProfile(cookies) else "" to ""
                    val finalName = profileResult.first.ifBlank { jwtName ?: fallbackUserName ?: "Ученик" }
                    val finalSchool = profileResult.second.ifBlank { "ГИС РК «Образование»" }

                    return@withContext Result.success(
                        UserSession(
                            token = tokenOrVToken,
                            vendor = "edu",
                            login = "",
                            userTitle = finalName,
                            schoolTitle = finalSchool
                        )
                    )
                }
            }

            // Attempt 3: If we have session cookies, query /apiv3/getusersvendors directly with cookies
            if (!cookies.isNullOrEmpty()) {
                val cookieReq = Request.Builder()
                    .url("$baseUrl/apiv3/getusersvendors?devkey=$devKey&out_format=json&auth_token=true")
                    .header("User-Agent", browserUserAgent)
                    .addHeader("Cookie", cookies)
                    .build()
                val cookieResp = client.newCall(cookieReq).execute()
                val cookieBody = cookieResp.body?.string()
                if (cookieResp.isSuccessful && !cookieBody.isNullOrEmpty()) {
                    val json = JSONObject(cookieBody)
                    val session = extractUserVendor(json, fallbackUserName)
                    if (session != null) return@withContext Result.success(session)
                }

                // Attempt 4: Scrape profile info from /journal-app
                val profileResult = scrapeUserProfile(cookies)
                val finalName = profileResult.first.ifBlank { fallbackUserName ?: "Ученик" }
                val finalSchool = profileResult.second.ifBlank { "ГИС РК «Образование»" }
                return@withContext Result.success(
                    UserSession(
                        token = tokenOrVToken ?: "web_session",
                        vendor = "edu",
                        login = "",
                        userTitle = finalName,
                        schoolTitle = finalSchool
                    )
                )
            }

            // Attempt 5: If only fallback username or token string is present
            if (!tokenOrVToken.isNullOrEmpty()) {
                return@withContext Result.success(
                    UserSession(
                        token = tokenOrVToken,
                        vendor = "edu",
                        login = "",
                        userTitle = fallbackUserName ?: "Ученик",
                        schoolTitle = "ГИС РК «Образование»"
                    )
                )
            }

            Result.failure(Exception("Не удалось получить сессию пользователя"))
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
        vendor: String = "edu",
        cookies: String? = null
    ): Result<List<DaySchedule>> = withContext(Dispatchers.IO) {
        try {
            // First try API if we have an API token
            if (token.isNotBlank() && token != "web_session") {
                // When vendor is "edu", omit it from the request URL because regional server edu.rk.gov.ru
                // rejects vendor=edu with "не удалось авторизовать разработчика" (it expects specific school code or no vendor)
                val vendorParam = if (vendor.isNotBlank() && vendor != "edu") "&vendor=$vendor" else ""
                val url = "$baseUrl/apiv3/getdiary?devkey=$devKey&auth_token=$token$vendorParam&out_format=json&rings=yes"
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", browserUserAgent)
                if (!cookies.isNullOrEmpty()) {
                    reqBuilder.addHeader("Cookie", cookies)
                }
                if (TokenExtractor.isJwtToken(token)) {
                    reqBuilder.addHeader("Authorization", "Bearer $token")
                }
                val response = client.newCall(reqBuilder.build()).execute()
                val body = response.body?.string()

                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val respObj = json.optJSONObject("response") ?: json
                    if (respObj.optInt("state", 200) == 200) {
                        val days = parseApiDiaryDays(respObj)
                        if (days.isNotEmpty()) {
                            return@withContext Result.success(days)
                        }
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

            Result.failure(Exception("Не удалось загрузить дневник"))
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                    lessons = lessons
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
