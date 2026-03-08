package com.example.mykmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.mykmp.data.api.AndroidHttpInit
import com.example.mykmp.data.database.AndroidDatabaseInit
import com.example.mykmp.data.storage.AndroidStorageInit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidStorageInit.init(filesDir)
        AndroidHttpInit.init(this)
        AndroidDatabaseInit.init(this)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}