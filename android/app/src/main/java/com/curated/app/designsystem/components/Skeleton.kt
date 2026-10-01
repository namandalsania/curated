package com.curated.app.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing

/** A pulsing gray-rounded-rectangle placeholder — the one skeleton primitive in the app. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(CuratedCornerRadius)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

/** Mirrors [TripCard]'s shape: a big image rect plus a couple of text-line rects below. */
@Composable
fun TripCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CuratedCornerRadius))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        SkeletonBox(
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        )
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(18.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
        }
    }
}

/** A circular avatar skeleton plus a couple of text-line rects, for profile headers. */
@Composable
fun ProfileHeaderSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(shape = CircleShape, modifier = Modifier.size(80.dp))
            Column(
                modifier = Modifier.padding(start = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                SkeletonBox(modifier = Modifier.width(120.dp).height(18.dp))
                SkeletonBox(modifier = Modifier.width(80.dp).height(14.dp))
            }
        }
    }
}
