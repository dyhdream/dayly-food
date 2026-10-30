package com.daylyfood.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.domain.guessMeal
import com.daylyfood.app.ui.ComposeDishScreen
import com.daylyfood.app.ui.DishesScreen
import com.daylyfood.app.ui.HomeScreen
import com.daylyfood.app.ui.LoadingScreen
import com.daylyfood.app.ui.ProfileScreen
import com.daylyfood.app.ui.RootViewModel
import com.daylyfood.app.ui.SearchScreen
import com.daylyfood.app.ui.UpdateViewModel
import com.daylyfood.app.ui.theme.DaylyFoodTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

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
    LaunchedEffect(Unit) {
        delay(3_000)
        updateVm.checkSilently()
    }

    // Coming back from the background re-checks too, so a release published while the app
    // sat in the background is picked up without killing the process.
    var firstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) {
            firstResume = false
        } else {
            updateVm.checkSilently()
        }
    }

    if (!ready) {
        LoadingScreen()
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    var addMeal by remember { mutableStateOf<String?>(null) }
    var addDay by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var composing by remember { mutableStateOf(false) }

    // Back leaves the current surface (compose -> search -> tab), and only then the app.
    BackHandler(enabled = composing || addMeal != null || tab != 0) {
        when {
            composing -> composing = false
            addMeal != null -> addMeal = null
            else -> tab = 0
        }
    }

    when {
        // First run: the profile is the whole screen, no tabs.
        profile == null -> ProfileScreen(
            initial = null,
            onSave = { root.saveProfile(it) },
            onCancel = null,
        )

        addMeal != null && composing -> ComposeDishScreen(
            meal = addMeal!!,
            day = addDay,
            onDone = { composing = false; addMeal = null },
        )

        addMeal != null -> SearchScreen(
            meal = addMeal!!,
            day = addDay,
            onCompose = { composing = true },
            onDone = { addMeal = null },
        )

        composing -> ComposeDishScreen(
            meal = guessMeal(LocalTime.now().hour),
            day = LocalDate.now().toEpochDay(),
            onDone = { composing = false; tab = 1 },
        )

        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("记录") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("菜品") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("我的") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { current ->
                    when (current) {
                        0 -> HomeScreen(
                            onAdd = { meal, day ->
                                addMeal = meal
                                addDay = day
                            },
                        )

                        1 -> DishesScreen(onCompose = { composing = true })

                        else -> ProfileScreen(
                            initial = profile,
                            onSave = { root.saveProfile(it) },
                            onCancel = null,
                        )
                    }
                }
            }
        }
    }
}
