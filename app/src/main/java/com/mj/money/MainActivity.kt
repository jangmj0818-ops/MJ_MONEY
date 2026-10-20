package com.mj.money

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mj.money.ui.MjMoneyApp
import com.mj.money.ui.theme.MJMoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MJMoneyTheme {
                MjMoneyApp()
            }
        }
    }
}
