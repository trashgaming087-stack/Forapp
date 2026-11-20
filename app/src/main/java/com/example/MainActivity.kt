package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.BuildsScreen
import com.example.ui.screens.InspectorScreen
import com.example.ui.screens.KeysScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.APKForgeTheme
import com.example.ui.theme.ForgeBackground
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.ForgeTextSecondary
import com.example.ui.viewmodel.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ROUTE_STUDIO = "studio"
private const val ROUTE_BUILDS = "builds"
private const val ROUTE_KEYS = "keys"
private const val ROUTE_INSPECTOR = "inspector"

private data class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topTabs = listOf(
    TopTab(ROUTE_STUDIO, "Studio", Icons.Filled.Build, Icons.Outlined.Build),
    TopTab(ROUTE_BUILDS, "Builds", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    TopTab(ROUTE_KEYS, "Keys", Icons.Filled.Key, Icons.Outlined.Key)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            APKForgeTheme {
                ApkForgeApp()
            }
        }
    }
}

@Composable
fun ApkForgeApp(
    studioViewModel: StudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STUDIO

    val draft by studioViewModel.draft.collectAsStateWithLifecycle()
    val pipelineState by studioViewModel.pipelineState.collectAsStateWithLifecycle()
    val builds by studioViewModel.builds.collectAsStateWithLifecycle()
    val signingKeys by studioViewModel.signingKeys.collectAsStateWithLifecycle()
    val selectedTemplateId by studioViewModel.selectedTemplateId.collectAsStateWithLifecycle()
    val inspectorState by studioViewModel.inspectorState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingSaveSourcePath by remember { mutableStateOf<String?>(null) }

    val saveApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { destUri ->
        val srcPath = pendingSaveSourcePath
        pendingSaveSourcePath = null
        if (destUri != null && srcPath != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val srcFile = File(srcPath)
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            srcFile.inputStream().use { input -> input.copyTo(out) }
                        } ?: error("Unable to open destination file")
                    }
                }
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) "APK saved to selected location"
                    else "Failed to save APK: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    val onSaveApk: (String, String) -> Unit = { apkPath, fileName ->
        pendingSaveSourcePath = apkPath
        saveApkLauncher.launch(fileName)
    }

    val onShareApk: (String) -> Unit = { apkPath ->
        runCatching {
            val file = File(apkPath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share or Open APK"))
        }.onFailure { err ->
            scope.launch {
                snackbarHostState.showSnackbar("Unable to share APK: ${err.message}")
            }
        }
    }

    val onInspectApk: (String) -> Unit = { apkPath ->
        studioViewModel.inspectApk(apkPath)
        navController.navigate(ROUTE_INSPECTOR)
    }

    val showChromeNav = currentRoute != ROUTE_INSPECTOR

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ForgeBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showChromeNav && !useRail) {
                    NavigationBar(containerColor = ForgeSurface) {
                        topTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ForgeEmerald,
                                    selectedTextColor = ForgeEmerald,
                                    indicatorColor = ForgeEmerald.copy(alpha = 0.16f),
                                    unselectedIconColor = ForgeTextSecondary,
                                    unselectedTextColor = ForgeTextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChromeNav && useRail) {
                    NavigationRail(
                        containerColor = ForgeSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        topTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}"),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ForgeEmerald,
                                    selectedTextColor = ForgeEmerald,
                                    indicatorColor = ForgeEmerald.copy(alpha = 0.16f),
                                    unselectedIconColor = ForgeTextSecondary,
                                    unselectedTextColor = ForgeTextSecondary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_STUDIO
                    ) {
                        composable(ROUTE_STUDIO) {
                            StudioScreen(
                                draft = draft,
                                pipelineState = pipelineState,
                                signingKeys = signingKeys,
                                selectedTemplateId = selectedTemplateId,
                                viewModel = studioViewModel,
                                onSaveApk = onSaveApk,
                                onShareApk = onShareApk,
                                onInspectApk = onInspectApk
                            )
                        }
                        composable(ROUTE_BUILDS) {
                            BuildsScreen(
                                builds = builds,
                                onLoadProject = { projectId ->
                                    studioViewModel.loadProjectIntoStudio(projectId)
                                    navController.navigate(ROUTE_STUDIO) {
                                        popUpTo(ROUTE_STUDIO) { inclusive = true }
                                    }
                                },
                                onSaveApk = onSaveApk,
                                onShareApk = onShareApk,
                                onInspectApk = onInspectApk,
                                onDeleteBuild = studioViewModel::deleteBuild,
                                onGoToStudio = {
                                    navController.navigate(ROUTE_STUDIO) {
                                        popUpTo(ROUTE_STUDIO) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(ROUTE_KEYS) {
                            KeysScreen(
                                keys = signingKeys,
                                selectedKeyId = draft.selectedKeyId,
                                onSelectKey = studioViewModel::selectSigningKey,
                                onCreateKey = studioViewModel::createSigningKey,
                                onDeleteKey = studioViewModel::deleteSigningKey,
                                onCopyMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                        composable(ROUTE_INSPECTOR) {
                            InspectorScreen(
                                state = inspectorState,
                                onBack = { navController.popBackStack() },
                                onSaveApk = onSaveApk,
                                onShareApk = onShareApk,
                                onCopyMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
