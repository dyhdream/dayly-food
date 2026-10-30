package com.daylyfood.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.ui.HomeScreen
import com.daylyfood.app.ui.LoadingScreen
import com.daylyfood.app.ui.ProfileScreen
import com.daylyfood.app.ui.RootViewModel
import com.daylyfood.app.ui.SearchScreen
import com.daylyfood.app.ui.UpdateViewModel
import com.daylyfood.app.ui.theme.DaylyFoodTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DaylyFoodTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot(root: RootViewModel = hiltViewModel()) {
    val ready by root.ready.collectAsStateWithLifecycle(false)
    val profile by root.profile.collectAsStateWithLifecycle(null)
    val updateVm: UpdateViewModel = hiltViewModel()

    // The one allowed post-start network call: delayed, silent, never on the critical path.
    // A failure only shows up inside the settings screen.
    LaunchedEffect(Unit) {
        delay(3_000)
        updateVm.check()
    }

    var editingProfile by remember { mutableStateOf(false) }
    var searchMeal by remember { mutableStateOf<String?>(null) }

    when {
        !ready -> LoadingScreen()

        profile == null || editingProfile -> ProfileScreen(
            initial = profile,
            onSave = { p ->
                root.saveProfile(p)
                editingProfile = false
            },
            onCancel = if (profile != null) ({ editingProfile = false }) else null,
        )

        searchMeal != null -> SearchScreen(
            meal = searchMeal!!,
            onDone = { searchMeal = null },
        )

        else -> HomeScreen(
            onAdd = { meal -> searchMeal = meal },
            onProfile = { editingProfile = true },
        )
    }
}
