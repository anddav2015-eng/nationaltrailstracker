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
import com.anddav.nationaltrailstracker.ui.trails.TrailsListScreen
import com.anddav.nationaltrailstracker.ui.trails.TrailsListViewModel

private const val TRAILS_LIST_ROUTE = "trails"
private const val TRAIL_DETAIL_ROUTE = "trails/{trailId}"
private const val TRAIL_ID_ARG = "trailId"

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
            )
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
            TrailDetailScreen(viewModel = viewModel)
        }
    }
}