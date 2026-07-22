package com.rakshak.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.di.ServiceLocator

/**
 * Builds ViewModels with dependencies from [ServiceLocator].
 * [volunteer] is required for [ScanViewModel] (who is reporting the match).
 */
class ViewModelFactory(
    private val context: Context,
    private val volunteer: Volunteer? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LoginViewModel::class.java) ->
            LoginViewModel(
                ServiceLocator.volunteerStore(context),
                ServiceLocator.auth(),
                ServiceLocator.volunteerRepository(context),
                ServiceLocator.googleSignIn(context),
            ) as T

        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(
                ServiceLocator.alertRepository(context),
                ServiceLocator.volunteerRepository(context),
            ) as T

        modelClass.isAssignableFrom(ScanViewModel::class.java) -> {
            val current = requireNotNull(volunteer) { "ScanViewModel requires a signed-in volunteer" }
            ScanViewModel(
                repository = ServiceLocator.alertRepository(context),
                matcher = ServiceLocator.faceMatcher(context),
                reportMatch = ServiceLocator.reportMatchUseCase(context),
                volunteer = current,
            ) as T
        }

        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
