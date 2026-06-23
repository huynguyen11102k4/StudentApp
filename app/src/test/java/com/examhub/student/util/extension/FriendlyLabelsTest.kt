package com.examhub.student.util.extension

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlyLabelsTest {
    @Test
    fun replaceTechnicalLabelsRepairsVietnameseMojibake() {
        val message = "B\u00E1\u00BA\u00A1n \u00C4\u2018\u00C3\u00A3 l\u00C6\u00B0u"

        assertEquals(
            "B\u1EA1n \u0111\u00E3 l\u01B0u",
            message.replaceTechnicalLabels()
        )
    }
}
