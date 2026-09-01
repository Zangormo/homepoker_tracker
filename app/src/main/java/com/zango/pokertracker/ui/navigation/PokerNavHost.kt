package com.zango.pokertracker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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

/**
 * Runs [block] only while [entry] is still the screen on top of the back stack.
 *
 * A screen goes on receiving touches for the frames it spends animating away, so two fast taps on
 * a back arrow both reach it. The first pop leaves settings; without this guard the second popped
 * the game hub off as well, and a NavHost with an empty back stack draws nothing at all - which is
 * the window's bare theme colour filling the screen with no UI on it. Comparing back stack entry
 * ids catches that: [NavHostController.currentBackStackEntry] is updated the moment the first pop
 * runs, whereas the leaving entry's own lifecycle stays RESUMED until its transition ends.
 *
 * The same guard keeps a double-tapped row or button from pushing its destination twice.
 */
private inline fun NavHostController.whileOn(entry: NavBackStackEntry, block: () -> Unit) {
    if (currentBackStackEntry?.id == entry.id) block()
}

/**
 * Takes the system back button and gesture away from the NavHost for this screen.
 *
 * The app targets SDK 36, where predictive back is on by default, so a back gesture is answered by
 * the NavHost's own handler - which pops with no guard of its own. On a fast phone two backs are
 * dispatched before the back stack has settled and both pop, which is how the last screen came off
 * the stack and left the window showing bare theme colour. Registering a handler here from inside
 * the destination puts this callback ahead of the NavHost's, so every back goes through the same
 * guarded [popFrom] as the on-screen arrow.
 */
@Composable
private fun GuardedBack(navController: NavHostController, entry: NavBackStackEntry) {
    BackHandler { navController.popFrom(entry) }
}

/** Backs out of [entry], never past the screen the app opens on. */
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    whileOn(entry) {
        // Belt and braces with the guard above: nothing may empty the stack, whatever the caller.
        if (previousBackStackEntry != null) popBackStack()
    }
}

@Composable
fun PokerNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    RecoverFromDeadNavHost(navController)
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

/** How long nothing may be on screen before it counts as stuck rather than mid-animation. */
private val DEAD_SCREEN_GRACE = 700.milliseconds

/**
 * Puts the game hub back if the NavHost stops showing anything at all.
 *
 * A NavHost with nothing to draw leaves the window as a plain theme-coloured surface: the app looks
 * dead, taps land on nothing, and only the drawer still answers. Two ways in have been seen. The
 * back stack can run dry, which the guards above are meant to make unreachable. Or the stack can be
 * intact while the NavHost still shows nothing, because a pop interrupted mid-transition leaves its
 * entries stuck in transition and [NavHostController.visibleEntries] empty - a fault inside the
 * navigation library that no call site can prevent, and one that only showed up on a fast release
 * build on a real phone.
 *
 * Either way the screen is unusable and the user has no way out, so it is worth catching both. The
 * grace period matters: a transition legitimately has nothing visible for a frame or two, and only
 * a gap longer than any real animation means the navigation is genuinely wedged.
 */
@Composable
private fun RecoverFromDeadNavHost(navController: NavHostController) {
    val entries by navController.currentBackStack.collectAsState()
    val visible by navController.visibleEntries.collectAsState()

    // The graph entry itself is always there and is not a screen, so it does not count.
    val screens = entries.count { it.destination !is NavGraph }
    // Before the NavHost sets its graph there is legitimately nothing to show, and navigating then
    // would throw, so nothing happens until at least one screen has been seen.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(screens) { if (screens > 0) started = true }

    val dead = started && (screens == 0 || visible.isEmpty())
    LaunchedEffect(dead) {
        if (!dead) return@LaunchedEffect
        delay(DEAD_SCREEN_GRACE)
        navController.navigate(Routes.HISTORY) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
}

@Composable
private fun PokerRoutes(navController: NavHostController, onOpenMenu: () -> Unit) {
    NavHost(
        navController = navController,
        startDestination = Routes.HISTORY,
    ) {
        composable(Routes.HISTORY) { entry ->
            HistoryScreen(
                onOpenMenu = onOpenMenu,
                onNewGame = {
                    navController.whileOn(entry) { navController.navigate(Routes.CREATE_GAME) }
                },
                onResumeGame = { gameId ->
                    navController.whileOn(entry) { navController.navigate(Routes.liveGame(gameId)) }
                },
                onOpenSettlement = { gameId ->
                    navController.whileOn(entry) {
                        navController.navigate(Routes.settlement(gameId))
                    }
                },
            )
        }

        composable(Routes.PLAYERS) { entry ->
            PlayersScreen(
                onOpenMenu = onOpenMenu,
                onOpenPlayer = { playerId ->
                    navController.whileOn(entry) { navController.navigate(Routes.player(playerId)) }
                },
            )
        }

        composable(Routes.PLAYER_DETAIL, arguments = playerIdArgument()) { entry ->
            GuardedBack(navController, entry)
            PlayerDetailScreen(onBack = { navController.popFrom(entry) })
        }

        composable(Routes.SETTINGS) { entry ->
            GuardedBack(navController, entry)
            SettingsScreen(onBack = { navController.popFrom(entry) })
        }

        composable(Routes.CREATE_GAME) { entry ->
            GuardedBack(navController, entry)
            CreateGameScreen(
                onBack = { navController.popFrom(entry) },
                onGameStarted = { gameId ->
                    navController.whileOn(entry) {
                        navController.navigate(Routes.liveGame(gameId)) {
                            // The game exists now, so backing up into its setup form would be a lie.
                            popUpTo(Routes.CREATE_GAME) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Routes.LIVE_GAME, arguments = gameIdArgument()) { entry ->
            GuardedBack(navController, entry)
            LiveGameScreen(
                onBack = { navController.popFrom(entry) },
                onEndGame = { gameId ->
                    navController.whileOn(entry) { navController.navigate(Routes.endGame(gameId)) }
                },
            )
        }

        composable(Routes.END_GAME, arguments = gameIdArgument()) { entry ->
            GuardedBack(navController, entry)
            EndGameScreen(
                onBack = { navController.popFrom(entry) },
                onFinished = { gameId ->
                    navController.whileOn(entry) {
                        navController.navigate(Routes.settlement(gameId)) {
                            // The game is over: back from the settlement belongs at history, not in
                            // a live screen for a game that no longer exists.
                            popUpTo(Routes.HISTORY)
                        }
                    }
                },
                onViewSettlement = { gameId ->
                    navController.whileOn(entry) {
                        navController.navigate(Routes.settlement(gameId))
                    }
                },
            )
        }

        composable(Routes.SETTLEMENT, arguments = gameIdArgument()) { entry ->
            GuardedBack(navController, entry)
            SettlementScreen(
                onBack = { navController.popFrom(entry) },
                onDone = {
                    navController.whileOn(entry) {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HISTORY) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
