package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.GameDatabase
import com.example.data.GameRepository
import com.example.ui.GameScreen
import com.example.ui.GameViewModel
import com.example.ui.GameViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Initialize Room Database and Repository
    val database = GameDatabase.getDatabase(applicationContext)
    val repository = GameRepository(database.gameDao())
    
    // Initialize LobbyMusicPlayer with Context to enable offline caching and background downloads
    com.example.ui.LobbyMusicPlayer.initialize(applicationContext)
    
    setContent {
      MyApplicationTheme {
        // ViewModel instantiated with custom Factory
        val viewModel: GameViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
          factory = GameViewModelFactory(application, repository)
        )
        
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          GameScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}
