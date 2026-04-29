package com.atlyn.subterranea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.atlyn.subterranea.ui.game.GameScreen
import com.atlyn.subterranea.ui.theme.SubterraneaTheme
import com.atlyn.subterranea.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubterraneaTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameScreen(viewModel = gameViewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Persist the active game on pause so the user can resume after process
        // death, app swap, or system kill. The ViewModel itself also saves on
        // endTurn, but onPause covers cases where the user backgrounds mid-turn.
        gameViewModel.saveActiveGame()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Welcome to $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SubterraneaTheme {
        Greeting("Android")
    }
}
