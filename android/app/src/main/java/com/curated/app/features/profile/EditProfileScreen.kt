package com.curated.app.features.profile

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.PrimaryButton

@Composable
fun EditProfileScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.factory(context))
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    val pickAvatar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.setAvatar(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                // Only a failed load replaces the form; a failed save keeps
                // the user's edits on screen with the error under them.
                state.error != null && state.username.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    AvatarPicker(
                        imageModel = state.pickedAvatarUri ?: state.currentAvatarUrl,
                        onClick = {
                            pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::setDisplayName,
                        label = { Text("Display name") },
                        singleLine = true,
                        isError = state.displayName.isBlank(),
                        supportingText = if (state.displayName.isBlank()) {
                            { Text("Required") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = "@${state.username}",
                        onValueChange = {},
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = false,
                        supportingText = { Text("Usernames can't be changed.") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.bio,
                        onValueChange = viewModel::setBio,
                        label = { Text("Bio") },
                        placeholder = { Text("Where you travel, how you like to travel") },
                        minLines = 3,
                        maxLines = 5,
                        supportingText = {
                            Text(
                                "${state.bio.length} / $BIO_MAX_LENGTH",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )

                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        PrimaryButton(
                            onClick = viewModel::save,
                            enabled = state.canSave,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

/** The avatar with a small camera badge, so it reads as tappable. */
@Composable
private fun AvatarPicker(imageModel: Any?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(104.dp)) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClickLabel = "Change photo", onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
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
        Box(
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
