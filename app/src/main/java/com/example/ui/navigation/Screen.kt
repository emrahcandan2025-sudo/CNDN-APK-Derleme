package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "Ana Sayfa")
    data object BuildWizard : Screen("build_wizard", "Yeni Derleme")
    data class BuildDetail(val buildId: Long = 0) : Screen("build_detail/{buildId}", "Derleme Detayı") {
        companion object {
            fun createRoute(buildId: Long) = "build_detail/$buildId"
        }
    }
    data object Repositories : Screen("repositories", "Depolarım")
    data object Downloads : Screen("downloads", "İndirilenler")
    data object Settings : Screen("settings", "Ayarlar")
}
