package com.whereisit.findthings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.whereisit.findthings.data.AppContainer
import com.whereisit.findthings.ui.FindThingsApp

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(applicationContext)
        enableEdgeToEdge()
        setContent {
            FindThingsApp(container = container)
        }
    }
}
