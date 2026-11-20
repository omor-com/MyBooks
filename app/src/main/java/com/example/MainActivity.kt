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

data class IncomingIntentRequest(
    val uris: List<Uri>,
    val openExternallyWithoutImport: Boolean
)

class MainActivity : ComponentActivity() {

    private var pendingIncomingRequest by mutableStateOf<IncomingIntentRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingIncomingRequest = extractIncomingRequest(intent)

        val appContainer = (application as MyBooksApplication).container
        setContent {
            MyBooksApp(
                container = appContainer,
                incomingRequest = pendingIncomingRequest,
                onConsumeIncomingRequest = { pendingIncomingRequest = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val req = extractIncomingRequest(intent)
        if (req != null) {
            pendingIncomingRequest = req
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            (application as? MyBooksApplication)?.container?.pageTurnSoundManager?.release()
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun extractIncomingRequest(intent: Intent?): IncomingIntentRequest? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let {
                IncomingIntentRequest(uris = listOf(it), openExternallyWithoutImport = true)
            }
            Intent.ACTION_SEND -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                streamUri?.let { IncomingIntentRequest(uris = listOf(it), openExternallyWithoutImport = false) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                list?.takeIf { it.isNotEmpty() }?.let {
                    IncomingIntentRequest(uris = it, openExternallyWithoutImport = false)
                }
            }
            else -> null
        }
    }
}
