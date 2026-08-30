package com.zango.pokertracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zango.pokertracker.ui.creategame.CreateGameScreen
import com.zango.pokertracker.ui.endgame.EndGameScreen
import com.zango.pokertracker.ui.history.HistoryScreen
import com.zango.pokertracker.ui.livegame.LiveGameScreen
import com.zango.pokertracker.ui.settlement.SettlementScreen

object Routes {
    const val HISTORY = "history"
    const val CREATE_GAME = "create-game"
    const val GAME_ID = "gameId"
    const val LIVE_GAME = "live-game/{$GAME_ID}"
    const val END_GAME = "end-game/{$GAME_ID}"
    const val SETTLEMENT = "settlement/{$GAME_ID}"

    fun liveGame(gameId: Long): String = "live-game/$gameId"

    fun endGame(gameId: Long): String = "end-game/$gameId"

    fun settlement(gameId: Long): String = "settlement/$gameId"
}

private fun gameIdArgument() = listOf(navArgument(Routes.GAME_ID) { type = NavType.LongType })

@Composable
fun PokerNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HISTORY,
        modifier = modifier,
    ) {
        composable(Routes.HISTORY) {
            HistoryScreen(
                onNewGame = { navController.navigate(Routes.CREATE_GAME) },
                onResumeGame = { gameId -> navController.navigate(Routes.liveGame(gameId)) },
                onOpenSettlement = { gameId -> navController.navigate(Routes.settlement(gameId)) },
            )
        }

        composable(Routes.CREATE_GAME) {
            CreateGameScreen(
                onBack = navController::popBackStack,
                onGameStarted = { gameId ->
                    navController.navigate(Routes.liveGame(gameId)) {
                        // The game exists now, so backing up into its setup form would be a lie.
                        popUpTo(Routes.CREATE_GAME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LIVE_GAME, arguments = gameIdArgument()) {
            LiveGameScreen(
                onBack = navController::popBackStack,
                onEndGame = { gameId -> navController.navigate(Routes.endGame(gameId)) },
            )
        }

        composable(Routes.END_GAME, arguments = gameIdArgument()) {
            EndGameScreen(
                onBack = navController::popBackStack,
                onFinished = { gameId ->
                    navController.navigate(Routes.settlement(gameId)) {
                        // The game is over: back from the settlement belongs at history, not in
                        // a live screen for a game that no longer exists.
                        popUpTo(Routes.HISTORY)
                    }
                },
                onViewSettlement = { gameId -> navController.navigate(Routes.settlement(gameId)) },
            )
        }

        composable(Routes.SETTLEMENT, arguments = gameIdArgument()) {
            SettlementScreen(
                onBack = navController::popBackStack,
                onDone = {
                    navController.navigate(Routes.HISTORY) {
                        popUpTo(Routes.HISTORY) { inclusive = true }
                    }
                },
            )
        }
    }
}
