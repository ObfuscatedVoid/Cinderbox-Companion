package com.sdvsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sdvsync.ui.screens.DashboardScreen
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

@Composable
fun SdvSyncNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "login",
    ) {
        composable("login") {
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
                onSyncLogClick = {
                    navController.navigate("sync_log")
                },
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
