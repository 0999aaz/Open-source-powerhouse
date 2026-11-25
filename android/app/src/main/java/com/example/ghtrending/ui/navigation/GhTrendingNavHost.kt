package com.example.ghtrending.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ghtrending.ui.screen.BookmarksScreen
import com.example.ghtrending.ui.screen.RepoDetailScreen
import com.example.ghtrending.ui.screen.TrendingScreen

private object Routes {
    const val TRENDING = "trending"
    const val BOOKMARKS = "bookmarks"
    const val DETAIL = "detail/{repoId}"
    fun detail(repoId: Long): String = "detail/$repoId"
}

@Composable
fun GhTrendingNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute == Routes.TRENDING || currentRoute == Routes.BOOKMARKS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.TRENDING,
                        onClick = {
                            navController.navigate(Routes.TRENDING) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.TRENDING) {
                                    Icons.Filled.Whatshot
                                } else {
                                    Icons.Outlined.Whatshot
                                },
                                contentDescription = "榜单",
                            )
                        },
                        label = { Text(text = "榜单") },
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.BOOKMARKS,
                        onClick = {
                            navController.navigate(Routes.BOOKMARKS) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.BOOKMARKS) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                                contentDescription = "收藏",
                            )
                        },
                        label = { Text(text = "收藏") },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TRENDING,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TRENDING) {
                TrendingScreen(
                    onRepoClick = { repoId ->
                        navController.navigate(Routes.detail(repoId))
                    },
                )
            }
            composable(Routes.BOOKMARKS) {
                BookmarksScreen(
                    onRepoClick = { repoId ->
                        navController.navigate(Routes.detail(repoId))
                    },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("repoId") { type = NavType.LongType }),
            ) {
                RepoDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
