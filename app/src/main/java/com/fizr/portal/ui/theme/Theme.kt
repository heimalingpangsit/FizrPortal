package com.fizr.portal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RedPrimary   = Color(0xFFE53935)
val RedDark      = Color(0xFFB71C1C)
val RedContainer = Color(0xFF4A1010)
val SurfaceDark  = Color(0xFF121212)
val SurfaceCard  = Color(0xFF1E1E1E)
val SurfaceLow   = Color(0xFF0A0A0A)
val OnSurface    = Color(0xFFE0E0E0)
val OnSurfaceDim = Color(0xFF8A8A8A)
val Outline      = Color(0xFF2C2C2C)

private val FizrColorScheme = darkColorScheme(
    primary          = RedPrimary,
    onPrimary        = Color.White,
    primaryContainer = RedContainer,
    onPrimaryContainer = Color(0xFFFFCDD2),
    secondary        = Color(0xFF888888),
    background       = SurfaceLow,
    surface          = SurfaceDark,
    surfaceVariant   = SurfaceCard,
    onBackground     = OnSurface,
    onSurface        = OnSurface,
    onSurfaceVariant = OnSurfaceDim,
    outline          = Outline,
    error            = Color(0xFFFF5252),
)

@Composable
fun FizrPortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FizrColorScheme,
        typography  = Typography,
        content     = content,
    )
}
