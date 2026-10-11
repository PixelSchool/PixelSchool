package com.pixelschool.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pixelschool.app.data.local.SessionStore
import com.pixelschool.app.data.remote.EljurApiClient
import com.pixelschool.app.data.repository.DiaryRepository
import com.pixelschool.app.ui.MainAppScreen
import com.pixelschool.app.ui.theme.PixelSchoolTheme
import com.pixelschool.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionStore = SessionStore(applicationContext)
        val cacheStore = com.pixelschool.app.data.local.CacheStore(applicationContext)
        val apiClient = EljurApiClient()
        val repository = DiaryRepository(sessionStore, apiClient, cacheStore, applicationContext)
        val viewModel = MainViewModel(repository)

        // Init Notification Channel & Request Permission on Android 13+
        com.pixelschool.app.notification.NotificationHelper.createNotificationChannel(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            PixelSchoolTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}
