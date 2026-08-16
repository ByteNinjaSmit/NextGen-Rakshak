package com.rakshak.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rakshak.app.presentation.screen.HomeScreen
import com.rakshak.app.presentation.screen.LoginScreen
import com.rakshak.app.presentation.screen.MatchesScreen
import com.rakshak.app.presentation.screen.ProfileScreen
import com.rakshak.app.presentation.screen.ScanScreen
import com.rakshak.app.presentation.viewmodel.HomeViewModel
import com.rakshak.app.presentation.viewmodel.LoginViewModel
import com.rakshak.app.presentation.viewmodel.MatchesViewModel
import com.rakshak.app.presentation.viewmodel.ScanViewModel
import com.rakshak.app.presentation.viewmodel.ViewModelFactory

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SCAN = "scan"
    const val MATCHES = "matches"
    const val PROFILE = "profile"
}

@Composable
fun AppNavigation() {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val navController = rememberNavController()

    val loginViewModel: LoginViewModel = viewModel(factory = ViewModelFactory(context))
    val volunteer by loginViewModel.volunteer.collectAsStateWithLifecycle()
    val signInBusy by loginViewModel.busy.collectAsStateWithLifecycle()
    val signInError by loginViewModel.error.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in listOf(
        Routes.HOME, Routes.MATCHES, Routes.PROFILE
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val items = listOf(
                        Triple(Routes.HOME, "Home", Icons.Filled.Home),
                        Triple(Routes.SCAN, "Scan", Icons.Filled.Search),
                        Triple(Routes.MATCHES, "Matches", Icons.Filled.VerifiedUser),
                        Triple(Routes.PROFILE, "Profile", Icons.Filled.Person)
                    )
                    items.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                if (route == Routes.SCAN) {
                                    navController.navigate(route)
                                } else {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(if (showBottomBar) innerPadding else androidx.compose.foundation.layout.PaddingValues())
        ) {
            composable(Routes.LOGIN) {
                LaunchedEffect(volunteer) {
                    if (volunteer != null) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                }
                LoginScreen(
                    onGoogleSignIn = { role -> loginViewModel.signInWithGoogle(activityContext, role) },
                    onEmailSignIn = loginViewModel::signInWithEmail,
                    onAnonymousSignIn = loginViewModel::signIn,
                    busy = signInBusy,
                    error = signInError,
                )
            }

            composable(Routes.HOME) {
                val homeViewModel: HomeViewModel = viewModel(factory = ViewModelFactory(context))
                LaunchedEffect(volunteer) {
                    if (volunteer == null) {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onStartScan = { navController.navigate(Routes.SCAN) },
                )
            }

            composable(Routes.SCAN) {
                val current = volunteer
                if (current == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val scanViewModel: ScanViewModel =
                        viewModel(factory = ViewModelFactory(context, current))
                    ScanScreen(scanViewModel, onReported = { navController.popBackStack() })
                }
            }

            composable(Routes.MATCHES) {
                val current = volunteer
                if (current == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val matchesViewModel: MatchesViewModel =
                        viewModel(factory = ViewModelFactory(context, current))
                    MatchesScreen(matchesViewModel)
                }
            }

            composable(Routes.PROFILE) {
                ProfileScreen(volunteer = volunteer, onSignOut = loginViewModel::signOut)
            }
        }
    }
}
