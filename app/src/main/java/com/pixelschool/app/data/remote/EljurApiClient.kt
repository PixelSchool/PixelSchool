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

    suspend fun fetchSessionFromVToken(vToken: String): Result<UserSession> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/apiv3/getusersvendors?out_format=json&v_token=$vToken&auth_token=true"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Пустой ответ сервера"))

            val json = JSONObject(body)
            val resultArr = json.optJSONArray("result")
            if (resultArr != null && resultArr.length() > 0) {
                val firstUser = resultArr.getJSONObject(0)
                val token = firstUser.getString("token")
                val vendor = firstUser.optString("vendor", "edu")
                val login = firstUser.optString("login", "")
                val userTitle = firstUser.optString("user_title", "")
                val vendorTitle = firstUser.optString("vendor_title", "")
                return@withContext Result.success(
                    UserSession(
                        token = token,
                        vendor = vendor,
                        login = login,
                        userTitle = userTitle,
                        schoolTitle = vendorTitle
                    )
                )
            } else {
                return@withContext Result.failure(Exception(json.optString("error", "Не удалось извлечь токен")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDiary(token: String, vendor: String = "edu", cookies: String? = null): Result<List<DaySchedule>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/apiv3/getdiary?auth_token=$token&vendor=$vendor&out_format=json&rings=yes"
            val reqBuilder = Request.Builder().url(url)
            if (!cookies.isNullOrEmpty()) {
                reqBuilder.addHeader("Cookie", cookies)
            }
            val response = client.newCall(reqBuilder.build()).execute()
            val body = response.body?.string()

            if (response.isSuccessful && !body.isNullOrEmpty()) {
                val json = JSONObject(body)
                val respObj = json.optJSONObject("response")
                if (respObj != null && respObj.optInt("state") == 200) {
                    val days = parseApiDiaryDays(respObj)
                    if (days.isNotEmpty()) {
                        return@withContext Result.success(days)
                    }
                }
            }

            // Fallback: Web parser for /journal-app/
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
        val result = respObj.optJSONObject("result") ?: return emptyList()
        val students = result.optJSONObject("students") ?: return emptyList()
        val firstStudentKey = students.keys().asSequence().firstOrNull() ?: return emptyList()
        val studentObj = students.getJSONObject(firstStudentKey)
        val daysObj = studentObj.optJSONObject("days") ?: return emptyList()

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
                                comment = a.optString("comment", null),
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
                    homeworkText = hwItem.optString("value", null)
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

    private fun fetchDiaryFromWeb(cookies: String): Result<List<DaySchedule>> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/journal-app/week.0")
                .addHeader("Cookie", cookies)
                .build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return Result.failure(Exception("Пустая HTML страница"))
            val doc = Jsoup.parse(html)
            val dayElements = doc.select("div.dnevnik-day")

            val list = mutableListOf<DaySchedule>()
            for (dayElem in dayElements) {
                val titleElem = dayElem.selectFirst("div.dnevnik-day__title") ?: continue
                val parts = titleElem.text().split(", ")
                val dayOfWeek = parts.getOrNull(0) ?: ""
                val date = parts.getOrNull(1) ?: ""

                val lessons = mutableListOf<Lesson>()
                val lessonElems = dayElem.select("div.dnevnik-lesson")
                for (lElem in lessonElems) {
                    val num = lElem.selectFirst("div.dnevnik-lesson__number")?.text()?.replace(".", "")?.trim()?.toIntOrNull() ?: 1
                    val time = lElem.selectFirst("div.dnevnik-lesson__time")?.text()?.trim() ?: ""
                    val subject = lElem.selectFirst("span.js-rt_licey-dnevnik-subject")?.text()?.trim() ?: "Урок"
                    val task = lElem.selectFirst("div.dnevnik-lesson__task")?.text()?.trim()
                    val markVal = lElem.selectFirst("div.dnevnik-mark")?.text()?.trim()

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
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
