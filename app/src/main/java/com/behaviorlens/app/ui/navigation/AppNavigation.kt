package com.behaviorlens.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.behaviorlens.app.ui.screens.HomeScreen
import com.behaviorlens.app.ui.screens.AnalysisScreen
import com.behaviorlens.app.ui.screens.ResultsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onStartCamera = { navController.navigate("analysis/camera") },
                onStartVideo = { navController.navigate("analysis/video") },
                onStartImage = { navController.navigate("analysis/image") }
            )
        }
        composable("analysis/{mode}") { back ->
            val mode = back.arguments?.getString("mode") ?: "camera"
            AnalysisScreen(
                mode = mode,
                onNavigateToResults = { navController.navigate("results") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("results") {
            ResultsScreen(onBack = { navController.popBackStack() })
        }
    }
}
