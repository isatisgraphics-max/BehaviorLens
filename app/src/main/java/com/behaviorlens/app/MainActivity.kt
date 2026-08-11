package com.behaviorlens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.behaviorlens.app.ui.navigation.AppNavigation
import com.behaviorlens.app.ui.theme.BehaviorLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BehaviorLensTheme {
                AppNavigation()
            }
        }
    }
}
