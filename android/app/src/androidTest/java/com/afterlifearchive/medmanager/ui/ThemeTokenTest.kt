package com.afterlifearchive.medmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class ThemeTokenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightAndDarkExposeCanonicalSemanticColors() {
        var lightBackground = Color.Unspecified
        var lightSecondary = Color.Unspecified
        var darkBackground = Color.Unspecified
        var darkSecondary = Color.Unspecified
        composeRule.setContent {
            MedicationAppTheme(darkTheme = false) {
                lightBackground = MaterialTheme.colorScheme.background
                lightSecondary = MedicationTheme.colors.readableSecondaryText
            }
            MedicationAppTheme(darkTheme = true) {
                darkBackground = MaterialTheme.colorScheme.background
                darkSecondary = MedicationTheme.colors.readableSecondaryText
            }
        }
        composeRule.runOnIdle {
            assertEquals(Color(0xFFF2FAFC), lightBackground)
            assertEquals(Color(0xFF66676B), lightSecondary)
            assertEquals(Color(0xFF242E30), darkBackground)
            assertEquals(Color(0xFFD1D9DE), darkSecondary)
            assertNotEquals(lightBackground, darkBackground)
        }
    }
}
