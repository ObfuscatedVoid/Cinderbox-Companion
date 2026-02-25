package com.sdvsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sdvsync.ui.screens.DashboardScreen
import com.sdvsync.ui.screens.GameDownloadScreen
import com.sdvsync.ui.screens.LoginScreen
import com.sdvsync.ui.screens.SettingsScreen
import com.sdvsync.ui.screens.SyncDetailScreen
import com.sdvsync.ui.screens.SyncLogScreen
import com.sdvsync.ui.theme.SdvSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SdvSyncTheme {
                val navController = rememberNavController()
                SdvSyncNavGraph(navController)
            }
        }
    }
}

private const val NAV_ANIM_DURATION = 300
private const val FADE_ANIM_DURATION = 400

@Composable
fun SdvSyncNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "login",
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NAV_ANIM_DURATION, easing = EaseInOut),
            ) + fadeIn(tween(NAV_ANIM_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NAV_ANIM_DURATION, easing = EaseInOut),
            ) + fadeOut(tween(NAV_ANIM_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NAV_ANIM_DURATION, easing = EaseInOut),
            ) + fadeIn(tween(NAV_ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NAV_ANIM_DURATION, easing = EaseInOut),
            ) + fadeOut(tween(NAV_ANIM_DURATION))
        },
    ) {
        composable(
            route = "login",
            enterTransition = { fadeIn(tween(FADE_ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(FADE_ANIM_DURATION)) },
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
            )
        }

        composable("dashboard") {
            DashboardScreen(
                onSaveClick = { folderName, hasCloud, hasLocal ->
                    navController.navigate("sync_detail/$folderName/$hasCloud/$hasLocal")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onGameDownloadClick = {
                    navController.navigate("game_download")
                },
                onSyncLogClick = {
                    navController.navigate("sync_log")
                },
            )
        }

        composable("game_download") {
            GameDownloadScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "sync_detail/{saveFolderName}/{hasCloud}/{hasLocal}",
            arguments = listOf(
                navArgument("saveFolderName") { type = NavType.StringType },
                navArgument("hasCloud") { type = NavType.BoolType },
                navArgument("hasLocal") { type = NavType.BoolType },
            ),
        ) { backStackEntry ->
            val saveFolderName = backStackEntry.arguments?.getString("saveFolderName") ?: return@composable
            val hasCloud = backStackEntry.arguments?.getBoolean("hasCloud") ?: false
            val hasLocal = backStackEntry.arguments?.getBoolean("hasLocal") ?: false
            SyncDetailScreen(
                saveFolderName = saveFolderName,
                hasCloud = hasCloud,
                hasLocal = hasLocal,
                onBack = { navController.popBackStack() },
            )
        }

        composable("sync_log") {
            SyncLogScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
