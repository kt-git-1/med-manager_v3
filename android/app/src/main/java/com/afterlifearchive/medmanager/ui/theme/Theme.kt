package com.afterlifearchive.medmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val TealLight = Color(0xFF008C80)
private val TealDark = Color(0xFF009E99)
private val TealTextLight = Color(0xFF006E66)
private val TealTextDark = Color(0xFF8AE6DE)
private val ScreenLight = Color(0xFFF2FAFC)
private val ScreenDark = Color(0xFF242E30)
private val CardDark = Color(0xFF384547)
private val ElevatedDark = Color(0xFF454F52)

private val LightColorScheme = lightColorScheme(
    primary = TealLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4EC),
    onPrimaryContainer = TealTextLight,
    secondary = Color(0xFFF06B00),
    tertiary = Color(0xFF1A73D1),
    error = Color(0xFFDB2E33),
    background = ScreenLight,
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF1F2F4),
    onSurfaceVariant = Color(0xFF66676B),
    outline = Color(0x1A000000),
)

private val DarkColorScheme = darkColorScheme(
    primary = TealDark,
    // iOS keeps primary teal actions white-on-teal in both appearances.
    onPrimary = Color.White,
    primaryContainer = Color(0xFF164D49),
    onPrimaryContainer = TealTextDark,
    secondary = Color(0xFFFFA05C),
    tertiary = Color(0xFF8FC1FF),
    error = Color(0xFFFFB4AB),
    background = ScreenDark,
    onBackground = Color(0xFFF1F4F4),
    surface = CardDark,
    onSurface = Color(0xFFF1F4F4),
    surfaceVariant = ElevatedDark,
    onSurfaceVariant = Color(0xFFD1D9DE),
    outline = Color(0x1AFFFFFF),
)

@Immutable
data class MedicationExtendedColors(
    val primaryTealText: Color,
    val caregiverBlue: Color,
    val orange: Color,
    val indigo: Color,
    val patientRed: Color,
    val caregiverRed: Color,
    val elevatedBackground: Color,
    val readableSecondaryText: Color,
    val cardStroke: Color,
    val patientCardShadow: Color,
    val caregiverCardShadow: Color,
    val slotMorning: Color,
    val slotNoon: Color,
    val slotEvening: Color,
    val slotBedtime: Color,
    val authInfoContainer: Color,
    val authInfoIcon: Color,
    val tutorialScrim: Color,
)

private val LightExtendedColors = MedicationExtendedColors(
    primaryTealText = TealTextLight,
    caregiverBlue = Color(0xFF1F7AD1),
    orange = Color(0xFFF06B00),
    indigo = Color(0xFF5752C7),
    patientRed = Color(0xFFDB2E33),
    caregiverRed = Color(0xFFD12929),
    elevatedBackground = Color(0xFFF5F5F7),
    readableSecondaryText = Color(0xFF66676B),
    cardStroke = Color(0x1A000000),
    patientCardShadow = Color(0x12000000),
    caregiverCardShadow = Color(0x0F000000),
    slotMorning = Color(0xFFFF9500),
    slotNoon = Color(0xFF007AFF),
    slotEvening = Color(0xFFAF52DE),
    slotBedtime = Color(0xFF5856D6),
    authInfoContainer = Color(0xFFEAF4FF),
    authInfoIcon = Color(0xFF1976D2),
    tutorialScrim = Color(0x2E000000),
)

private val DarkExtendedColors = LightExtendedColors.copy(
    primaryTealText = TealTextDark,
    elevatedBackground = ElevatedDark,
    readableSecondaryText = Color(0xFFD1D9DE),
    cardStroke = Color(0x1AFFFFFF),
    authInfoContainer = Color(0xFF243B4F),
    authInfoIcon = Color(0xFF8FC1FF),
)

private val LocalMedicationExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object MedicationTheme {
    val colors: MedicationExtendedColors
        @Composable get() = LocalMedicationExtendedColors.current
}

@Composable
fun MedicationAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMedicationExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
