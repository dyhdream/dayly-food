package com.daylyfood.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.data.ocr.TempPhotoStore
import com.daylyfood.app.domain.guessMeal
import com.daylyfood.app.ui.AiSettingsScreen
import com.daylyfood.app.ui.ComposeDishScreen
import com.daylyfood.app.ui.DishesScreen
import com.daylyfood.app.ui.HomeScreen
import com.daylyfood.app.ui.LoadingScreen
import com.daylyfood.app.ui.MeScreen
import com.daylyfood.app.ui.ProfileScreen
import com.daylyfood.app.ui.RootViewModel
import com.daylyfood.app.ui.ScanOverlay
import com.daylyfood.app.ui.SearchScreen
import com.daylyfood.app.ui.UpdateDialog
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
    val updateState by updateVm.state.collectAsStateWithLifecycle()

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
    var scanUri by remember { mutableStateOf<Uri?>(null) }
    var settingsPage by remember { mutableStateOf<String?>(null) }

    // Camera and gallery open straight from the button; there is no intermediate screen.
    val context = LocalContext.current
    val photoStore = remember { TempPhotoStore(context) }
    // Filled only when the capture succeeds: the scan screen must never open on an empty file.
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraTarget
        cameraTarget = null
        if (ok && target != null) scanUri = target
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanUri = uri
    }

    fun launchCamera() {
        val uri = runCatching {
            val file = photoStore.newFile()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, "创建照片文件失败，改用相册", Toast.LENGTH_LONG).show()
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            return
        }
        cameraTarget = uri
        runCatching { cameraLauncher.launch(uri) }.onFailure {
            cameraTarget = null
            Toast.makeText(context, "这台设备没有可用的相机应用，改用相册", Toast.LENGTH_LONG).show()
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    // Back leaves the current surface (compose/scan/settings -> search -> tab), and only then the app.
    BackHandler(enabled = composing || scanUri != null || settingsPage != null || addMeal != null || tab != 0) {
        when {
            composing -> composing = false
            scanUri != null -> scanUri = null
            settingsPage != null -> settingsPage = null
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

        settingsPage == "profile" -> ProfileScreen(
            initial = profile,
            onSave = {
                root.saveProfile(it)
                settingsPage = null
            },
            onCancel = { settingsPage = null },
        )

        settingsPage == "ai" -> AiSettingsScreen(onBack = { settingsPage = null })

        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(painterResource(R.drawable.ic_gallery), contentDescription = "从相册选")
                    }
                    Spacer(Modifier.height(10.dp))
                    val cameraPress = remember { MutableInteractionSource() }
                    val cameraHeld by cameraPress.collectIsPressedAsState()
                    val cameraScale by animateFloatAsState(
                        targetValue = if (cameraHeld) 0.9f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                        label = "cameraPress",
                    )
                    FloatingActionButton(
                        onClick = { launchCamera() },
                        interactionSource = cameraPress,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.graphicsLayer { scaleX = cameraScale; scaleY = cameraScale },
                    ) {
                        Icon(painterResource(R.drawable.ic_camera), contentDescription = "拍照")
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { TabIcon(Icons.Default.Home, tab == 0) },
                        label = { Text("记录") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { TabIcon(painterResource(R.drawable.ic_bowl), tab == 1) },
                        label = { Text("查找") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { TabIcon(Icons.Default.Person, tab == 2) },
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

                        else -> MeScreen(
                            profile = profile,
                            onOpenProfile = { settingsPage = "profile" },
                            onOpenAi = { settingsPage = "ai" },
                        )
                    }
                }
            }

            // One shot of the camera/gallery: a dialog on top of whatever tab is showing,
            // so a failed read is one line of text rather than a page of its own.
            scanUri?.let { uri ->
                ScanOverlay(
                    uri = uri,
                    meal = guessMeal(LocalTime.now().hour),
                    day = LocalDate.now().toEpochDay(),
                    onDone = { scanUri = null; photoStore.clear() },
                    onRetake = { scanUri = null; launchCamera() },
                    onManualAdd = {
                        scanUri = null
                        photoStore.clear()
                        addMeal = guessMeal(LocalTime.now().hour)
                    },
                    onOpenAiSettings = {
                        scanUri = null
                        photoStore.clear()
                        settingsPage = "ai"
                        tab = 2
                    },
                )
            }
        }
    }

    // Update prompt sits above every screen: important releases must not be missed,
    // optional ones can be postponed for this version only.
    UpdateDialog(
        state = updateState,
        onInstall = updateVm::downloadAndInstall,
        onPostpone = updateVm::postpone,
        onRetry = { updateVm.check() },
        onBrowser = { updateVm.openReleasePage() },
    )
}

/** Tab icons pop a little when selected; the only motion the bottom bar needs. */
@Composable
private fun TabIcon(icon: ImageVector, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "tabIcon",
    )
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    )
}

@Composable
private fun TabIcon(painter: androidx.compose.ui.graphics.painter.Painter, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "tabIconPainter",
    )
    Icon(
        painter,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    )
}
