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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pixelschool.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val sessionStore = SessionStore(applicationContext)
                val cacheStore = com.pixelschool.app.data.local.CacheStore(applicationContext)
                val apiClient = EljurApiClient()
                val repository = DiaryRepository(sessionStore, apiClient, cacheStore, applicationContext)
                return MainViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init Notification Channel & Request Permission on Android 13+
        com.pixelschool.app.notification.NotificationHelper.createNotificationChannel(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val settingsStore = com.pixelschool.app.data.local.SettingsStore(applicationContext)

        setContent {
            val palette by settingsStore.themePalette.collectAsState()
            PixelSchoolTheme(palette = palette) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScreen(
                        viewModel = viewModel,
                        settingsStore = settingsStore
                    )
                }
            }
        }
    }
}
