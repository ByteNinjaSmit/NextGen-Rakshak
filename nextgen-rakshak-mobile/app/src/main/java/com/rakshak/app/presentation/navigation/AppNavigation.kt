package com.rakshak.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rakshak.app.presentation.screen.HomeScreen
import com.rakshak.app.presentation.screen.LoginScreen
import com.rakshak.app.presentation.screen.ScanScreen
import com.rakshak.app.presentation.viewmodel.HomeViewModel
import com.rakshak.app.presentation.viewmodel.LoginViewModel
import com.rakshak.app.presentation.viewmodel.ScanViewModel
import com.rakshak.app.presentation.viewmodel.ViewModelFactory

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SCAN = "scan"
}

@Composable
fun AppNavigation() {
    // Credential Manager must be launched from the Activity, not the application
    // context, so keep both: the Activity for sign-in, the app context for DI.
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val navController = rememberNavController()

    val loginViewModel: LoginViewModel = viewModel(factory = ViewModelFactory(context))
    val volunteer by loginViewModel.volunteer.collectAsStateWithLifecycle()
    val signInBusy by loginViewModel.busy.collectAsStateWithLifecycle()
    val signInError by loginViewModel.error.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            // Skip login if a volunteer is already stored.
            LaunchedEffect(volunteer) {
                if (volunteer != null) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            }
            LoginScreen(
                onGoogleSignIn = { role -> loginViewModel.signInWithGoogle(activityContext, role) },
                onDemoSignIn = loginViewModel::signIn,
                busy = signInBusy,
                error = signInError,
            )
        }

        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(factory = ViewModelFactory(context))
            HomeScreen(
                homeViewModel,
                onStartScan = { navController.navigate(Routes.SCAN) },
                onSignOut = {
                    loginViewModel.signOut()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
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
    }
}
