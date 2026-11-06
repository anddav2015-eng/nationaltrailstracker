package com.anddav.nationaltrailstracker.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anddav.nationaltrailstracker.di.LocalAppContainer
import com.anddav.nationaltrailstracker.ui.detail.TrailDetailScreen
import com.anddav.nationaltrailstracker.ui.detail.TrailDetailViewModel
import com.anddav.nationaltrailstracker.ui.map.TrailMapScreen
import com.anddav.nationaltrailstracker.ui.map.TrailMapViewModel
import com.anddav.nationaltrailstracker.ui.settings.SettingsScreen
import com.anddav.nationaltrailstracker.ui.settings.SettingsViewModel
import com.anddav.nationaltrailstracker.ui.summary.SummaryStatsScreen
import com.anddav.nationaltrailstracker.ui.summary.SummaryStatsViewModel
import com.anddav.nationaltrailstracker.ui.trails.TrailsListScreen
import com.anddav.nationaltrailstracker.ui.trails.TrailsListViewModel
import com.anddav.nationaltrailstracker.ui.whereami.WhereAmIScreen
import com.anddav.nationaltrailstracker.ui.whereami.WhereAmIViewModel

private const val TRAILS_LIST_ROUTE = "trails"
private const val TRAIL_DETAIL_ROUTE = "trails/{trailId}"
private const val TRAIL_ID_ARG = "trailId"
private const val SUMMARY_ROUTE = "summary"
private const val SETTINGS_ROUTE = "settings"
private const val WHERE_AM_I_ROUTE = "trails/{trailId}/where"
private const val MAP_ROUTE = "trails/{trailId}/map?stage={stage}&locate={locate}"
private const val STAGE_ARG = "stage"
private const val LOCATE_ARG = "locate"

@Composable
fun NationalTrailsNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val container = LocalAppContainer.current

    NavHost(navController = navController, startDestination = TRAILS_LIST_ROUTE, modifier = modifier) {
        composable(TRAILS_LIST_ROUTE) {
            val viewModel: TrailsListViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { TrailsListViewModel(container.trailRepository, container.progressRepository) }
                },
            )
            TrailsListScreen(
                viewModel = viewModel,
                onTrailClick = { trailId -> navController.navigate("trails/$trailId") },
                onSummaryClick = { navController.navigate(SUMMARY_ROUTE) },
                onSettingsClick = { navController.navigate(SETTINGS_ROUTE) },
            )
        }
        composable(SETTINGS_ROUTE) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(container.progressRepository, container.trailRepository, container.offlineMapStore)
                    }
                },
            )
            SettingsScreen(viewModel = viewModel)
        }
        composable(SUMMARY_ROUTE) {
            val viewModel: SummaryStatsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SummaryStatsViewModel(container.trailRepository, container.progressRepository) }
                },
            )
            SummaryStatsScreen(viewModel = viewModel)
        }
        composable(
            route = TRAIL_DETAIL_ROUTE,
            arguments = listOf(navArgument(TRAIL_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val trailId = backStackEntry.arguments?.getString(TRAIL_ID_ARG)
                ?: error("Missing required nav arg: $TRAIL_ID_ARG")
            val viewModel: TrailDetailViewModel = viewModel(
                key = trailId,
                factory = viewModelFactory {
                    initializer {
                        TrailDetailViewModel(trailId, container.trailRepository, container.progressRepository)
                    }
                },
            )
            TrailDetailScreen(
                viewModel = viewModel,
                onWhereAmIClick = { navController.navigate("trails/$trailId/where") },
                onShowOnMap = { stageIndex -> navController.navigate("trails/$trailId/map?stage=${stageIndex ?: -1}") },
            )
        }
        composable(
            route = WHERE_AM_I_ROUTE,
            arguments = listOf(navArgument(TRAIL_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val trailId = backStackEntry.arguments?.getString(TRAIL_ID_ARG)
                ?: error("Missing required nav arg: $TRAIL_ID_ARG")
            val viewModel: WhereAmIViewModel = viewModel(
                key = "where-$trailId",
                factory = viewModelFactory {
                    initializer { WhereAmIViewModel(trailId, container.trailRepository, container.locationClient) }
                },
            )
            WhereAmIScreen(
                viewModel = viewModel,
                onShowOnMap = { stageIndex ->
                    navController.navigate("trails/$trailId/map?stage=${stageIndex ?: -1}&locate=true")
                },
            )
        }
        composable(
            route = MAP_ROUTE,
            arguments = listOf(
                navArgument(TRAIL_ID_ARG) { type = NavType.StringType },
                navArgument(STAGE_ARG) {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument(LOCATE_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val trailId = backStackEntry.arguments?.getString(TRAIL_ID_ARG)
                ?: error("Missing required nav arg: $TRAIL_ID_ARG")
            val stageIndex = backStackEntry.arguments?.getInt(STAGE_ARG)?.takeIf { it >= 0 }
            val locate = backStackEntry.arguments?.getBoolean(LOCATE_ARG) ?: false
            val viewModel: TrailMapViewModel = viewModel(
                key = "map-$trailId-$stageIndex-$locate",
                factory = viewModelFactory {
                    initializer {
                        TrailMapViewModel(
                            trailId,
                            stageIndex,
                            container.trailRepository,
                            container.locationClient,
                            container.offlineMapStore,
                            startWithLocation = locate,
                        )
                    }
                },
            )
            TrailMapScreen(viewModel = viewModel)
        }
    }
}
