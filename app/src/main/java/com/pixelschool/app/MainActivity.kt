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
        val apiClient = EljurApiClient()
        val repository = DiaryRepository(sessionStore, apiClient)
        val viewModel = MainViewModel(repository)

        setContent {
            PixelSchoolTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}
