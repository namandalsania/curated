package com.curated.app.features.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.PrimaryButton

@Composable
fun ProfileSetupScreen(
    viewModel: ProfileSetupViewModel,
    onComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var username by remember { mutableStateOf(state.prefillUsername) }
    var displayName by remember { mutableStateOf(state.prefillDisplayName) }

    LaunchedEffect(state.complete) {
        if (state.complete) onComplete()
    }

    val pickAvatar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.setAvatar(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            "Set up your profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable {
                    pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center
        ) {
            if (state.avatarUri != null) {
                AsyncImage(
                    model = state.avatarUri,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "Add photo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (state.isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            PrimaryButton(
                onClick = { viewModel.submit(username, displayName) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}
