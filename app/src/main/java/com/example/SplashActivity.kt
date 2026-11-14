package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.example.ui.screens.WelcomeAnimationScreen
import com.example.ui.theme.RikzenChatTheme
import com.example.ui.theme.ThemeMode

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            RikzenChatTheme(themeMode = ThemeMode.DARK) {
                WelcomeAnimationScreen(
                    onFinished = { navigateToMain() }
                )
            }
        }
    }

    private fun navigateToMain() {
        if (hasNavigated || isFinishing) return
        hasNavigated = true
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FROM_SPLASH, true)
        }
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }
}
