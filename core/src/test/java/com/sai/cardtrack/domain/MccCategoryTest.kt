package com.sai.cardtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MccCategoryTest {
    @Test
    fun `5411 is groceries`() {
        assertEquals("groceries", MccCategory.suggest("5411"))
    }

    @Test
    fun `unknown mcc is null`() {
        assertNull(MccCategory.suggest("0000"))
        assertNull(MccCategory.suggest(""))
    }
}
