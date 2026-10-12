package com.curated.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.features.trip.DeleteTripDialog
import com.curated.app.features.trip.TripOwnerMenu

/**
 * The owner's trip-page top bar with the real TripOwnerMenu and
 * DeleteTripDialog, and no network: tap ⋯, then Delete trip. Confirming does
 * nothing here. Debug builds only, for screenshots.
 */
class DeleteTripPreviewActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CuratedTheme {
                var confirmDelete by remember { mutableStateOf(false) }
                if (confirmDelete) {
                    DeleteTripDialog(isDeleting = false, onConfirm = { confirmDelete = false }, onDismiss = { confirmDelete = false })
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Lisboa · September 2026") },
                            navigationIcon = {
                                IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                            },
                            actions = {
                                IconButton(onClick = {}) { Icon(Icons.Outlined.Share, contentDescription = "Send this trip to someone") }
                                TripOwnerMenu(onDeleteTrip = { confirmDelete = true })
                            }
                        )
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.md)) {
                        Text(
                            "Preview: your own trip, made-up data",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
