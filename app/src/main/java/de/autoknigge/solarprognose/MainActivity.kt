package de.autoknigge.solarprognose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val crash0 = CrashLog.read(this)
        setContent {
            SolarTheme {
                var crash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(crash0) }
                val c = crash
                if (c != null) CrashScreen(c, onCopy = {
                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Absturzbericht", c))
                }, onContinue = {
                    CrashLog.clear(this)
                    crash = null
                }) else SolarRoot(this)
            }
        }
    }
}

/** Zeigt nach einem Absturz den Fehlerbericht an, statt die App sofort wieder zu starten. */
@androidx.compose.runtime.Composable
private fun CrashScreen(text: String, onCopy: () -> Unit, onContinue: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        androidx.compose.material3.Text("Die App ist abgestürzt", fontSize = 18.sp, color = androidx.compose.ui.graphics.Color.White)
        androidx.compose.material3.Text(
            "Bitte Bericht kopieren und an Claude schicken. Danach kannst du die App neu starten.",
            fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFFBBBBBB), modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp)
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = onCopy) { androidx.compose.material3.Text("Bericht kopieren") }
            androidx.compose.material3.OutlinedButton(onClick = onContinue) { androidx.compose.material3.Text("App starten") }
        }
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 12.dp)) {
            androidx.compose.material3.Text(text, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFFDDDDDD), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}
