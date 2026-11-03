package com.saltgarden.kechengbiao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.saltgarden.kechengbiao.ui.nav.AppNav
import com.saltgarden.kechengbiao.ui.theme.KcbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KcbTheme {
                AppNav()
            }
        }
    }
}
