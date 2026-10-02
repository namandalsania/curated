package com.curated.app.features.create

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * A photo picker that keeps the photos' GPS, for every import that builds
 * stops from where photos were taken. Returns the function that opens it.
 *
 * It's the system file picker rather than the Photo Picker: the Photo Picker
 * zeroes every photo's GPS, permission or not. The file picker keeps it when
 * ACCESS_MEDIA_LOCATION is granted, so on API 29+ that's asked for first. The
 * picker opens either way - a refusal only costs the locations.
 *
 * Picking photos to attach to a stop doesn't need this; the Photo Picker is fine there.
 */
@Composable
fun rememberGeoPhotoPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        // Backing out of the picker returns nothing; there's nothing to do.
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val launchPicker = { pickPhotos.launch(arrayOf("image/*")) }
    val requestMediaLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        launchPicker()
    }
    return {
        val needsMediaLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        if (needsMediaLocation) {
            requestMediaLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            launchPicker()
        }
    }
}
