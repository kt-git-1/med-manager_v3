package com.afterlifearchive.medmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppModeTest {
    @Test
    fun modesMatchTheSharedProductRoles() {
        assertEquals(listOf("PATIENT", "CAREGIVER"), AppMode.entries.map(AppMode::name))
    }
}
