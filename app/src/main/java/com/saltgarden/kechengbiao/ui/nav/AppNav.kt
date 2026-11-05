package com.saltgarden.kechengbiao.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.saltgarden.kechengbiao.ui.import.ImportScreen
import com.saltgarden.kechengbiao.ui.import.ImportViewModel
import com.saltgarden.kechengbiao.ui.schedule.ScheduleScreen
import com.saltgarden.kechengbiao.ui.schedule.ScheduleViewModel
import com.saltgarden.kechengbiao.ui.settings.SettingsScreen
import com.saltgarden.kechengbiao.ui.settings.SettingsViewModel
import com.saltgarden.kechengbiao.ui.theme.kcbBackdrop

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Schedule : Screen("schedule", "课表", Icons.Filled.CalendarMonth)
    data object Import : Screen("import", "导入", Icons.Filled.Download)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val scheduleVm: ScheduleViewModel = viewModel()
    val importVm: ImportViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()

    Box(Modifier.fillMaxSize().kcbBackdrop()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Schedule.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    vm = scheduleVm,
                    onGoImport = { navController.navigate(Screen.Import.route) },
                    onGoSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Import.route) {
                ImportScreen(
                    vm = importVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    vm = settingsVm,
                    onBack = { navController.popBackStack() },
                    onGoImport = { navController.navigate(Screen.Import.route) }
                )
            }
        }
    }
}
