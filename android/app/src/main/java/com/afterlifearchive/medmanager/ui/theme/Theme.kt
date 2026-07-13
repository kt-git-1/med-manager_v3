package com.afterlifearchive.medmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MedicationColorScheme = lightColorScheme(
    primary = Color(0xFFB84A34),
    onPrimary = Color.White,
    background = Color(0xFFFFF8F3),
    surface = Color(0xFFFFF8F3),
)

@Composable
fun MedicationAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MedicationColorScheme,
        content = content,
    )
}
