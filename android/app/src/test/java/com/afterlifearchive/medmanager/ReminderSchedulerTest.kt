package com.afterlifearchive.medmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {
    @Test
    fun notificationBodyUsesOnlyValidatedGenericSlotCopy() {
        assertEquals(R.string.notification_patient_body_morning, patientNotificationBodyResource("morning"))
        assertEquals(R.string.notification_patient_body_noon, patientNotificationBodyResource("NOON"))
        assertEquals(R.string.notification_patient_body_evening, patientNotificationBodyResource("evening"))
        assertEquals(R.string.notification_patient_body_bedtime, patientNotificationBodyResource("bedtime"))
        assertEquals(R.string.notification_patient_body, patientNotificationBodyResource(null))
        assertEquals(R.string.notification_patient_body, patientNotificationBodyResource("patient-or-medication-data"))
    }
}
