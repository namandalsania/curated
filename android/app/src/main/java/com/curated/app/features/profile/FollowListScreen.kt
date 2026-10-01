package com.curated.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.curated.app.core.model.User
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.SecondaryButton
import com.curated.app.designsystem.components.SkeletonBox

@Composable
fun FollowListScreen(
    userId: String,
    kind: FollowListKind,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: FollowListViewModel = viewModel(factory = FollowListViewModel.factory(context))
    val state by viewModel.state.collectAsState()

    LaunchedEffect(userId, kind) { viewModel.load(userId, kind) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (kind == FollowListKind.FOLLOWERS) "Followers" else "Following") },
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
                state.isLoading -> Column {
                    repeat(5) { RowSkeleton() }
                }
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load(userId, kind) },
                    modifier = Modifier.align(Alignment.Center)
                )
                state.users.isEmpty() -> EmptyState(
                    headline = if (kind == FollowListKind.FOLLOWERS) "No followers yet" else "Not following anyone yet",
                    body = "People will show up here as the community grows.",
                    icon = Icons.Outlined.People,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            isSelf = user.id == state.viewerId,
                            isFollowing = user.id in state.viewerFollowingIds,
                            onClick = { onUserClick(user.id) },
                            onFollowToggle = { viewModel.toggleFollow(user.id) }
                        )
                        HairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    user: User,
    isSelf: Boolean,
    isFollowing: Boolean,
    onClick: () -> Unit,
    onFollowToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            user.avatarUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isSelf) {
            if (isFollowing) {
                SecondaryButton(onClick = onFollowToggle) { Text("Following") }
            } else {
                PrimaryButton(onClick = onFollowToggle) { Text("Follow") }
            }
        }
    }
}

@Composable
private fun RowSkeleton() {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        SkeletonBox(shape = CircleShape, modifier = Modifier.size(44.dp))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SkeletonBox(modifier = Modifier.width(140.dp).height(14.dp))
            SkeletonBox(modifier = Modifier.width(90.dp).height(12.dp))
        }
    }
}
