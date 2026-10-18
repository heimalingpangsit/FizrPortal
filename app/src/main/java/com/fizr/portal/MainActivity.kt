package com.fizr.portal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fizr.portal.ui.HomeScreen
import com.fizr.portal.ui.theme.FizrPortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FizrPortalTheme {
                HomeScreen()
            }
        }
    }
}
