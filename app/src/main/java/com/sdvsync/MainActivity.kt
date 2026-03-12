package com.sdvsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sdvsync.logging.AppLogger
import com.sdvsync.mods.ModDownloadManager
import com.sdvsync.mods.api.NexusModSource
import com.sdvsync.ui.components.BottomTab
import com.sdvsync.ui.components.StardewBottomBar
import com.sdvsync.ui.screens.BackupListScreen
import com.sdvsync.ui.screens.DashboardScreen
import com.sdvsync.ui.screens.GameDownloadScreen
import com.sdvsync.ui.screens.InstalledModDetailScreen
import com.sdvsync.ui.screens.LoginScreen
import com.sdvsync.ui.screens.ModBrowseScreen
import com.sdvsync.ui.screens.ModDetailScreen
import com.sdvsync.ui.screens.ModManagerScreen
import com.sdvsync.ui.screens.SaveViewerScreen
import com.sdvsync.ui.screens.SettingsScreen
import com.sdvsync.ui.screens.SyncDetailScreen
import com.sdvsync.ui.screens.SyncLogScreen
import com.sdvsync.ui.theme.SdvSyncTheme
import com.sdvsync.ui.viewmodels.InstalledModDetailViewModel
import com.sdvsync.ui.viewmodels.ModBrowseViewModel
import com.sdvsync.ui.viewmodels.ModDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

class MainActivity : ComponentActivity() {

    private val nexusSource: NexusModSource by inject()
    private val downloadManager: ModDownloadManager by inject()

    /** URI from an incoming .sdvsync file intent, consumed by the DashboardScreen. */
    var pendingImportUri: android.net.Uri? = null
        private set

    fun consumePendingImportUri(): android.net.Uri? {
        val uri = pendingImportUri
        pendingImportUri = null
        return uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            SdvSyncTheme {
                val navController = rememberNavController()
                SdvSyncNavGraph(navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri = intent.data
        if (uri != null && intent.action == Intent.ACTION_VIEW && uri.scheme != "nxm") {
            // Likely a .sdvsync file opened via share/file manager
            pendingImportUri = uri
            intent.data = null
            return
        }
        handleNxmIntent(intent)
    }

    private fun handleNxmIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "nxm") return

        val parsed = parseNxmUrl(uri)
        if (parsed == null) {
            Toast.makeText(this, getString(R.string.nxm_invalid_link), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.nxm_starting_download), Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = nexusSource.getDownloadUrl(
                    parsed.modId,
                    parsed.fileId,
                    parsed.key,
                    parsed.expires
                )
                val modName = try {
                    nexusSource.getModDetails(parsed.modId).name
                } catch (_: Exception) {
                    "Mod #${parsed.modId}"
                }
                downloadManager.startDownload(url, modName, parsed.modId, "nexus")
            } catch (e: Exception) {
                AppLogger.e("MainActivity", "NXM download failed", e)
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.nxm_download_failed, e.message ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Clear the intent data so it doesn't re-trigger
        intent?.data = null
    }

    /**
     * Parse an nxm:// URL into its components.
     * Format: nxm://stardewvalley/mods/{modId}/files/{fileId}?key={key}&expires={expires}
     */
    private fun parseNxmUrl(uri: Uri): NxmDownloadParams? {
        val segments = uri.pathSegments ?: return null
        // Expected: [mods, {modId}, files, {fileId}]
        if (segments.size < 4 || segments[0] != "mods" || segments[2] != "files") return null

        val modId = segments[1]
        val fileId = segments[3]
        val key = uri.getQueryParameter("key") ?: return null
        val expires = uri.getQueryParameter("expires") ?: return null

        return NxmDownloadParams(modId, fileId, key, expires)
    }

    private data class NxmDownloadParams(val modId: String, val fileId: String, val key: String, val expires: String)
}

private const val NAV_ANIM_DURATION = 300
private const val FADE_ANIM_DURATION = 400

// Routes that should show the bottom bar
private val BOTTOM_BAR_ROUTES = setOf("saves", "mods", "game_download", "settings")

@Composable
fun SdvSyncNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute by remember(navBackStackEntry) {
        derivedStateOf { navBackStackEntry?.destination?.route }
    }

    val showBottomBar by remember(currentRoute) {
        derivedStateOf { currentRoute in BOTTOM_BAR_ROUTES }
    }

    val selectedTab by remember(currentRoute) {
        derivedStateOf {
            when (currentRoute) {
                "saves" -> BottomTab.SAVES
                "mods" -> BottomTab.MODS
                "game_download" -> BottomTab.DOWNLOAD
                "settings" -> BottomTab.SETTINGS
                else -> BottomTab.SAVES
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "login",
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NAV_ANIM_DURATION, easing = EaseInOut)
            ) + fadeIn(tween(NAV_ANIM_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NAV_ANIM_DURATION, easing = EaseInOut)
            ) + fadeOut(tween(NAV_ANIM_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NAV_ANIM_DURATION, easing = EaseInOut)
            ) + fadeIn(tween(NAV_ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NAV_ANIM_DURATION, easing = EaseInOut)
            ) + fadeOut(tween(NAV_ANIM_DURATION))
        }
    ) {
        // Login screen — outside the bottom bar
        composable(
            route = "login",
            enterTransition = { fadeIn(tween(FADE_ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(FADE_ANIM_DURATION)) }
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // Main section with bottom bar
        composable("main") {
            MainScreen(
                parentNavController = navController,
                showBottomBar = showBottomBar,
                selectedTab = selectedTab
            )
        }

        // Saves sub-screens (outside bottom bar scaffold for clean back stack)
        composable(
            route = "sync_detail/{saveFolderName}/{hasCloud}/{hasLocal}",
            arguments = listOf(
                navArgument("saveFolderName") { type = NavType.StringType },
                navArgument("hasCloud") { type = NavType.BoolType },
                navArgument("hasLocal") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val saveFolderName = backStackEntry.arguments?.getString("saveFolderName") ?: return@composable
            val hasCloud = backStackEntry.arguments?.getBoolean("hasCloud") ?: false
            val hasLocal = backStackEntry.arguments?.getBoolean("hasLocal") ?: false
            SyncDetailScreen(
                saveFolderName = saveFolderName,
                hasCloud = hasCloud,
                hasLocal = hasLocal,
                onBack = { navController.popBackStack() },
                onBackupsClick = { navController.navigate("backups/$saveFolderName") },
                onViewSaveClick = { navController.navigate("save_viewer/$saveFolderName") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        // Backup list screen
        composable(
            route = "backups/{saveFolderName}",
            arguments = listOf(
                navArgument("saveFolderName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val saveFolderName = backStackEntry.arguments?.getString("saveFolderName") ?: return@composable
            BackupListScreen(
                saveFolderName = saveFolderName,
                onBack = { navController.popBackStack() }
            )
        }

        // Save viewer screen
        composable(
            route = "save_viewer/{saveFolderName}",
            arguments = listOf(
                navArgument("saveFolderName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val saveFolderName = backStackEntry.arguments?.getString("saveFolderName") ?: return@composable
            SaveViewerScreen(
                saveFolderName = saveFolderName,
                onBack = { navController.popBackStack() }
            )
        }

        composable("sync_log") {
            SyncLogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Mod sub-screens
        composable(
            route = "installed_mod/{uniqueId}",
            arguments = listOf(
                navArgument("uniqueId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uniqueId = backStackEntry.arguments?.getString("uniqueId") ?: return@composable
            val viewModel: InstalledModDetailViewModel = koinViewModel { parametersOf(uniqueId) }
            InstalledModDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("mods_browse") {
            val viewModel: ModBrowseViewModel = koinViewModel()
            ModBrowseScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onModClick = { modId, source ->
                    navController.navigate("mod_detail/$modId/$source")
                }
            )
        }

        composable(
            route = "mod_detail/{modId}/{source}",
            arguments = listOf(
                navArgument("modId") { type = NavType.StringType },
                navArgument("source") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val modId = backStackEntry.arguments?.getString("modId") ?: return@composable
            val source = backStackEntry.arguments?.getString("source") ?: return@composable
            val viewModel: ModDetailViewModel = koinViewModel { parametersOf(modId, source) }
            ModDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainScreen(parentNavController: NavHostController, showBottomBar: Boolean, selectedTab: BottomTab) {
    val mainNavController = rememberNavController()
    val mainBackStackEntry by mainNavController.currentBackStackEntryAsState()
    val mainCurrentRoute by remember(mainBackStackEntry) {
        derivedStateOf { mainBackStackEntry?.destination?.route }
    }

    val isMainShowBottomBar by remember(mainCurrentRoute) {
        derivedStateOf { mainCurrentRoute in BOTTOM_BAR_ROUTES }
    }

    val mainSelectedTab by remember(mainCurrentRoute) {
        derivedStateOf {
            when (mainCurrentRoute) {
                "saves" -> BottomTab.SAVES
                "mods" -> BottomTab.MODS
                "game_download" -> BottomTab.DOWNLOAD
                "settings" -> BottomTab.SETTINGS
                else -> BottomTab.SAVES
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (isMainShowBottomBar) {
                StardewBottomBar(
                    selectedTab = mainSelectedTab,
                    onTabSelected = { tab ->
                        mainNavController.navigate(tab.route) {
                            popUpTo(mainNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = mainNavController,
            startDestination = "saves",
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(NAV_ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(NAV_ANIM_DURATION)) },
            popEnterTransition = { fadeIn(tween(NAV_ANIM_DURATION)) },
            popExitTransition = { fadeOut(tween(NAV_ANIM_DURATION)) }
        ) {
            // Saves tab
            composable("saves") {
                DashboardScreen(
                    onSaveClick = { folderName, hasCloud, hasLocal ->
                        parentNavController.navigate("sync_detail/$folderName/$hasCloud/$hasLocal")
                    },
                    onSyncLogClick = {
                        parentNavController.navigate("sync_log")
                    }
                )
            }

            // Mods tab
            composable("mods") {
                ModManagerScreen(
                    onBrowseClick = {
                        parentNavController.navigate("mods_browse")
                    },
                    onModClick = { uniqueId ->
                        parentNavController.navigate("installed_mod/$uniqueId")
                    }
                )
            }

            // Download tab
            composable("game_download") {
                GameDownloadScreen(
                    onBack = {
                        mainNavController.navigate("saves") {
                            popUpTo(mainNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // Settings tab
            composable("settings") {
                SettingsScreen(
                    onBack = {
                        mainNavController.navigate("saves") {
                            popUpTo(mainNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onLogout = {
                        parentNavController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
