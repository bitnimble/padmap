package app.padmap.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PadmapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(Palette.PRIMARY),
            onPrimary = Color.White,
            surface = Color(0xFF161E2A),
            surfaceContainer = Color(0xFF1F2A38),
            onSurface = Color(Palette.TEXT),
            onSurfaceVariant = Color(Palette.TEXT_DIM),
        ),
        content = content,
    )
}
