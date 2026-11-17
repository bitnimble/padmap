package app.padmap.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import java.io.IOException
import kotlinx.coroutines.delay

/** Setup and status: how to start the daemon, the overlay permission, and where layouts live. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val script = installStartScript()
        val command = if (script == null) "adb shell sh <could not write start.sh>" else "adb shell sh $script"
        setContent {
            PadmapTheme {
                Surface(Modifier.fillMaxSize()) {
                    Setup(
                        command,
                        allowOverlay = {
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                            )
                        },
                        copyCommand = {
                            getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Padmap start command", command))
                        },
                        canDrawOverlays = { Settings.canDrawOverlays(this) },
                    )
                }
            }
        }
        startForegroundService(Intent(this, OverlayService::class.java))
    }

    /** Copies start.sh where adb's shell user can read it. */
    private fun installStartScript(): File? {
        val dir = getExternalFilesDir(null) ?: return null
        val script = File(dir, "start.sh")
        return try {
            assets.open("start.sh").use { input -> script.outputStream().use { input.copyTo(it) } }
            script
        } catch (_: IOException) {
            null
        }
    }
}

@Composable
private fun Setup(command: String, allowOverlay: () -> Unit, copyCommand: () -> Unit, canDrawOverlays: () -> Boolean) {
    var status by remember { mutableStateOf(OverlayService.status) }
    var overlayAllowed by remember { mutableStateOf(canDrawOverlays()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                status = OverlayService.status
                overlayAllowed = canDrawOverlays()
                delay(REFRESH_MS)
            }
        }
    }
    Column(
        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(status, style = MaterialTheme.typography.titleMedium)
                Text("Padmap turns gamepad input into touches, so you can play touch-only games with a controller.")
                Text("1. Turn on USB debugging (Developer options) and connect to a computer with adb.")
                Text("2. Run this on the computer, once after every phone restart:")
                Text(command, fontFamily = FontFamily.Monospace)
                Text("Rooted: run the same file with su -c sh instead.")
                Text(
                    "3. Open your game and press Select + Start on the gamepad (changeable in the editor's settings) " +
                        "to edit its layout. Layouts are plain JSON in /sdcard/Padmap/<app package>.json.",
                )
            }
        }
        Button(copyCommand) { Text("Copy command") }
        Button(allowOverlay, enabled = !overlayAllowed) { Text("Allow display over other apps") }
    }
}

private const val REFRESH_MS = 1000L
