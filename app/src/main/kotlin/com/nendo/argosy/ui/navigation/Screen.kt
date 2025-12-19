package com.nendo.argosy.ui.navigation

sealed class Screen(val route: String) {
    data object FirstRun : Screen("first_run")
    data object Showcase : Screen("showcase")
    data object Library : Screen("library?platformId={platformId}&source={source}") {
        fun createRoute(platformId: String? = null, source: String? = null): String {
            val params = mutableListOf<String>()
            if (platformId != null) params.add("platformId=$platformId")
            if (source != null) params.add("source=$source")
            return if (params.isEmpty()) "library" else "library?${params.joinToString("&")}"
        }
    }
    data object Downloads : Screen("downloads")
    data object Apps : Screen("apps")
    data object Settings : Screen("settings")
    data object GameDetail : Screen("game/{gameId}") {
        fun createRoute(gameId: Long) = "game/$gameId"
    }
    data object Search : Screen("search")

    companion object {
        const val ROUTE_SHOWCASE = "showcase"
        const val ROUTE_LIBRARY = "library"
        const val ROUTE_GAME_DETAIL = "game"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_DOWNLOADS = "downloads"
        const val ROUTE_APPS = "apps"
        const val ROUTE_SEARCH = "search"
    }
}
