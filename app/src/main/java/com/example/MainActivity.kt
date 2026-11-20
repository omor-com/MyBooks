package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.navigation.MyBooksApp

class MainActivity : ComponentActivity() {

    private var pendingIncomingUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingIncomingUris = extractIncomingUris(intent)

        val appContainer = (application as MyBooksApplication).container
        setContent {
            MyBooksApp(
                container = appContainer,
                incomingUris = pendingIncomingUris,
                onConsumeIncomingUris = { pendingIncomingUris = emptyList() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newUris = extractIncomingUris(intent)
        if (newUris.isNotEmpty()) {
            pendingIncomingUris = pendingIncomingUris + newUris
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            (application as? MyBooksApplication)?.container?.pageTurnSoundManager?.release()
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun extractIncomingUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uris += it }
            Intent.ACTION_SEND -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                streamUri?.let { uris += it }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                list?.let { uris.addAll(it) }
            }
        }
        return uris
    }
}
