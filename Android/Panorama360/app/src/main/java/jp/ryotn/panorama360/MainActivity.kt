package jp.ryotn.panorama360

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import jp.ryotn.panorama360.model.MainViewModel
import jp.ryotn.panorama360.model.SettingViewModel
import jp.ryotn.panorama360.ui.theme.Panorama360Theme
import jp.ryotn.panorama360.view.Setting
import jp.ryotn.panorama360.view.ui.theme.cameraViewBackgroundColor
import jp.ryotn.panorama360.view.ui.theme.topAppBarContainerColor
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private val settingModel: SettingViewModel by viewModels()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d("MainActivity", "keycode:${event.keyCode}")
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if(event.action == KeyEvent.ACTION_UP)model.startCapture()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.init()
        settingModel.init()
        setContent {
            Panorama360Theme {
                KeepScreenOn()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GetPermission(model)
                    Contents(model = model,
                        settingModel = settingModel)
                }
            }
        }
    }
}

// Permission
@Composable
fun GetPermission(model: MainViewModel) {
    val context = LocalContext.current
    val permissions = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT
    )

    var isDirectoryPermissionCheck = false
    val directoryPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@rememberLauncherForActivityResult
                Log.d("getPermission", "get File Save Path $uri")
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                model.setSaveDirPath(uri.toString())
                isDirectoryPermissionCheck = model.isFilePermission()
                model.isPermission.value = true
            }
        }

    val launcherMultiplePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val areGranted = permissionsMap.values.reduce { acc, next -> acc && next }
        if (areGranted) {
            Log.d("getPermission.launcherMultiplePermissions", "Permission Granted")
            if (model.isFilePermission()) {
                model.setPermission(true)
            } else if (!isDirectoryPermissionCheck) {
                directoryPermission.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                isDirectoryPermissionCheck = true
            }
        } else {
            Log.d("getPermission.launcherMultiplePermissions", "not permitted")
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleObserver = remember {
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!permissions.all {
                        checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }) {
                    launcherMultiplePermissions.launch(permissions)
                } else {
                    Log.d("getPermission.lifecycleObserver", "Permission　すでに取得済み")
                    if (model.isFilePermission()) {
                        model.setPermission(true)
                    } else if (!isDirectoryPermissionCheck) {
                        directoryPermission.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                        isDirectoryPermissionCheck = true
                    }
                }
            } else if (event == Lifecycle.Event.ON_DESTROY) {
                model.onDestroy()
            }
        }
    }

    DisposableEffect(lifecycle, lifecycleObserver) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Contents(model: MainViewModel, settingModel: SettingViewModel) {
    val isPermission: Boolean by model.isPermission.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        topBar = {
            if (navBackStackEntry?.destination?.route == Route.SETTING.displayName) {
                TopAppBar(
                    title = {
                        Box { Text(text = Route.SETTING.displayName) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.topAppBarContainerColor),
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(navController = navController,
            startDestination = Route.HOME.displayName,
            modifier = Modifier.padding(padding)
        ) {
            composable(route = Route.HOME.displayName) {
                Column {
                    Header(model = model, navController)

                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        if (isPermission && navBackStackEntry?.destination?.route == Route.HOME.displayName) {
                            AndroidView(
                                modifier = Modifier
                                    .weight(1.0f)
                                    .aspectRatio(0.75f)
                                    .background(MaterialTheme.colorScheme.cameraViewBackgroundColor),
                                factory = {
                                    model.initCameraView()
                                    model.mCameraView
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1.0f))
                    Footer(model = model)
                }
            }

            composable(route = Route.SETTING.displayName) {
                Setting(model = settingModel)
            }
        }
    }
}

@Composable
fun Header(model: MainViewModel, navController: NavController) {
    val focus: Float by model.mFocus.asStateFlow().collectAsState()
    val isConnect: Boolean by model.isConnect.collectAsState()
    Column {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(modifier = Modifier.padding(start = 24.dp,
                top = 8.dp),
                onClick = {
                    model.connectMatterport()
                }) {

                if (isConnect) {
                    Icon(
                        modifier = Modifier.size(48.dp),
                        painter = painterResource(id = R.drawable.connected),
                        contentDescription = ""
                    )
                } else {
                    Icon(
                        modifier = Modifier.size(48.dp),
                        painter = painterResource(id = R.drawable.disconnected),
                        contentDescription = ""
                    )
                }
            }
            IconButton(modifier = Modifier.padding(end = 24.dp,
                top = 8.dp),
                onClick = {
                    navController.navigate(route = Route.SETTING.displayName)
                }) {
                Icon(modifier = Modifier.size(48.dp),
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "")
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Slider(modifier = Modifier
                .weight(0.8f)
                .padding(start = 24.dp),
                value = focus,
                valueRange = 0f..1f,
                steps = 10,
                onValueChange = {
                    model.setFocus(it)
                })
            Text(modifier = Modifier.padding(start = 14.dp,
                top = 14.dp,
                end = 24.dp),
                text = "$focus")
        }
    }
}

@Composable
fun Footer(model: MainViewModel) {
    val context = LocalContext.current
    var cameraLabel by remember { mutableStateOf(context.getString(R.string.wide)) }
    val angle: Int by model.mAngle.collectAsState()
    val isUltraWide: Boolean by model.isUltraWide.collectAsState()
    val isConnect: Boolean by model.isConnect.collectAsState()
    val focalLength: Float by model.mFocalLength.asStateFlow().collectAsState()
    val exposureBracketModeList: List<String> by model.mExposureBracketModeList.collectAsState()
    val exposureBracketModeLabel: String by model.mExposureBracketModeLabel.collectAsState()
    Column {
        Text(modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = "$focalLength mm",
            fontWeight = FontWeight.Light
        )
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            Text(
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.Center,
                text = context.getString(R.string.angle, angle)
            )

            Spinner(
                modifier = Modifier.wrapContentSize(),
                dropDownModifier = Modifier.wrapContentSize(),
                items = exposureBracketModeList,
                selectedItem = exposureBracketModeLabel,
                onItemSelected = {
                    model.setExposureBracketMode(it)
                },
                selectedItemFactory = { modifier, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = modifier
                            .padding(8.dp)
                            .wrapContentSize()
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center
                        )

                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_drop_down_24),
                            contentDescription = ""
                        )
                    }
                },
                dropdownItemFactory = { item, _ ->
                    Text(text = item)
                }
            )

            Column {
                IconButton(
                    modifier = Modifier.width(64.dp),
                    onClick = {
                        model.createDir()
                    }) {
                    Icon(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        painter = painterResource(id = R.drawable.create_new_folder),
                        contentDescription = context.getString(R.string.create_dir)
                    )
                }

                Text(
                    modifier = Modifier.width(64.dp),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    text = context.getString(R.string.create_dir)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    modifier = Modifier.size(64.dp),
                    enabled = isConnect,
                    onClick = {
                        model.resetAngle()
                    }) {
                        Icon(
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.CenterHorizontally),
                            painter = painterResource(id = R.drawable.baseline_360_24),
                            contentDescription = context.getString(R.string.reset_angle)
                        )
                }
                Text(
                    fontSize = 12.sp,
                    text = context.getString(R.string.reset_angle),
                    textAlign = TextAlign.Center)
            }
            IconButton(
                modifier = Modifier.size(126.dp),
                onClick = {
                    model.startCapture()
                }) {
                Column {
                    Icon(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.CenterHorizontally),
                        painter = painterResource(id = R.drawable.baseline_mode_standby_24),
                        contentDescription = ""
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    modifier = Modifier.size(64.dp),
                    enabled = isUltraWide,
                    onClick = {
                        cameraLabel = model.toggleCamera()
                    }) {
                    Icon(
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.CenterHorizontally),
                        painter = painterResource(id = R.drawable.baseline_flip_camera_android_24),
                        contentDescription = ""
                    )
                }

                Text(
                    fontSize = 12.sp,
                    text = cameraLabel,
                    textAlign = TextAlign.Center
                )
            }

        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun <T> Spinner(
    modifier: Modifier = Modifier,
    dropDownModifier: Modifier = Modifier,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (Int) -> Unit,
    selectedItemFactory: @Composable (Modifier, T) -> Unit,
    dropdownItemFactory: @Composable (T, Int) -> Unit,
) {
    var expanded: Boolean by remember { mutableStateOf(false) }

    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        selectedItemFactory(
            Modifier
                .clickable { expanded = true },
            selectedItem
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = dropDownModifier
        ) {
            items.forEachIndexed { index, element ->
                DropdownMenuItem(
                    text = {
                        dropdownItemFactory(element, index)
                    },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                    })
            }
        }
    }
}

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        println("FLAG_KEEP_SCREEN_ON")
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            println("FLAG_KEEP_SCREEN_OFF")
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun ContentsPreview() {
    val model = MainViewModel(Application())
    val settingModel = SettingViewModel(Application())
    Panorama360Theme {
        Contents(model = model, settingModel = settingModel)
    }
}

private enum class Route(val displayName: String) {
    HOME("Home"),
    SETTING("Setting");
}