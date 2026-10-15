package com.pixelschool.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val apkDownloadUrl: String?,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean
)

class UpdateChecker(
    private val client: OkHttpClient = RussianTrustManager.defaultOkHttpClient
) {

    suspend fun checkLatestRelease(currentVersion: String = com.pixelschool.app.BuildConfig.VERSION_NAME): Result<AppUpdateInfo> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/PixelSchool/PixelSchool/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "PixelSchool-Android-App")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }
                    val body = response.body?.string()
                        ?: return@withContext Result.failure(Exception("Пустой ответ от GitHub"))

                    val json = JSONObject(body)
                    val rawTag = json.optString("tag_name")
                    val version = rawTag.removePrefix("v").trim()
                    val htmlUrl = json.optString("html_url")
                    val notes = json.optString("body")

                    val assets = json.optJSONArray("assets") ?: JSONArray()
                    var directApkUrl: String? = null
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            directApkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }

                    val hasUpdate = isNewer(version, currentVersion)
                    Result.success(
                        AppUpdateInfo(
                            latestVersion = version,
                            releaseUrl = htmlUrl,
                            apkDownloadUrl = directApkUrl,
                            releaseNotes = notes,
                            isUpdateAvailable = hasUpdate
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        if (remoteVersion.isBlank()) return false
        val rParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
