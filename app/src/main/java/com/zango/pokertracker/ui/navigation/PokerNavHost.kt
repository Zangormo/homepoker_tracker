package com.zango.pokertracker.ui.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zango.pokertracker.ui.creategame.CreateGameScreen
import com.zango.pokertracker.ui.endgame.EndGameScreen
import com.zango.pokertracker.ui.history.HistoryScreen
import com.zango.pokertracker.ui.livegame.LiveGameScreen
import com.zango.pokertracker.ui.players.PlayerDetailScreen
import com.zango.pokertracker.ui.players.PlayersScreen
import com.zango.pokertracker.ui.settings.SettingsScreen
import com.zango.pokertracker.ui.settlement.SettlementScreen
import kotlinx.coroutines.launch

object Routes {
    const val HISTORY = "history"
    const val PLAYERS = "players"
    const val CREATE_GAME = "create-game"
    const val SETTINGS = "settings"
    const val GAME_ID = "gameId"
    const val PLAYER_ID = "playerId"
    const val LIVE_GAME = "live-game/{$GAME_ID}"
    const val END_GAME = "end-game/{$GAME_ID}"
    const val SETTLEMENT = "settlement/{$GAME_ID}"
    const val PLAYER_DETAIL = "player/{$PLAYER_ID}"

    fun liveGame(gameId: Long): String = "live-game/$gameId"

    fun endGame(gameId: Long): String = "end-game/$gameId"

    fun settlement(gameId: Long): String = "settlement/$gameId"

    fun player(playerId: Long): String = "player/$playerId"
}

private fun gameIdArgument() = listOf(navArgument(Routes.GAME_ID) { type = NavType.LongType })

private fun playerIdArgument() = listOf(navArgument(Routes.PLAYER_ID) { type = NavType.LongType })

@Composable
fun PokerNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = AppDestination.entries.any { it.route == currentRoute }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        // Screens below the top level own their own back gesture and their own edge swipes, so
        // the drawer only answers to a swipe where the menu button is actually on screen.
        gesturesEnabled = isTopLevel || drawerState.isOpen,
        drawerContent = {
            PokerDrawerContent(
                currentRoute = currentRoute,
                onSelect = { destination ->
                    scope.launch { drawerState.close() }
                    if (destination.route != currentRoute) {
                        navController.navigate(destination.route) {
                            // Switching tabs is not a step deeper into the app: the back stack
                            // stays one level tall, and each tab keeps where it was scrolled to.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) {
        PokerRoutes(
            navController = navController,
            onOpenMenu = { scope.launch { drawerState.open() } },
        )
    }
}

@Composable
private fun PokerRoutes(navController: NavHostController, onOpenMenu: () -> Unit) {
    NavHost(
        navController = navController,
        startDestination = Routes.HISTORY,
    ) {
        composable(Routes.HISTORY) {
            HistoryScreen(
                onOpenMenu = onOpenMenu,
                onNewGame = { navController.navigate(Routes.CREATE_GAME) },
                onResumeGame = { gameId -> navController.navigate(Routes.liveGame(gameId)) },
                onOpenSettlement = { gameId -> navController.navigate(Routes.settlement(gameId)) },
            )
        }

        composable(Routes.PLAYERS) {
            PlayersScreen(
                onOpenMenu = onOpenMenu,
                onOpenPlayer = { playerId -> navController.navigate(Routes.player(playerId)) },
            )
        }

        composable(Routes.PLAYER_DETAIL, arguments = playerIdArgument()) {
            PlayerDetailScreen(onBack = navController::popBackStack)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = navController::popBackStack)
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
