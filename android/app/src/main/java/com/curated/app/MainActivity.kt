package com.curated.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.navigation.RootNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CuratedTheme {
                RootNavHost()
            }
        }
    }
}
