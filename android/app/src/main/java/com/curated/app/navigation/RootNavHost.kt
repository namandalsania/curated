package com.curated.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curated.app.features.auth.AuthGatePhase
import com.curated.app.features.auth.AuthGateViewModel
import com.curated.app.features.auth.AuthScreen
import com.curated.app.features.auth.AuthViewModel
import com.curated.app.features.auth.ProfileSetupScreen
import com.curated.app.features.auth.ProfileSetupViewModel

@Composable
fun RootNavHost() {
    val context = LocalContext.current
    val gateViewModel: AuthGateViewModel = viewModel(factory = AuthGateViewModel.factory(context))
    val gateState by gateViewModel.state.collectAsState()

    when (gateState.phase) {
        AuthGatePhase.LOADING -> Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        AuthGatePhase.SIGNED_OUT -> {
            val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(context))
            AuthScreen(authViewModel)
        }
        AuthGatePhase.NEEDS_PROFILE -> {
            val setupViewModel: ProfileSetupViewModel = viewModel(factory = ProfileSetupViewModel.factory(context))
            ProfileSetupScreen(setupViewModel, onComplete = { gateViewModel.recheckProfile() })
        }
        AuthGatePhase.READY -> {
            AppNavHost(currentUserId = gateState.userId.orEmpty())
        }
    }
}
