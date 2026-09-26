package com.sai.cardtrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ViewModelKeyTest {

    @Test
    fun `view model key includes the database session`() {
        assertEquals("home-Ru-1", viewModelKey("home", "Ru", 1))
        assertNotEquals(
            viewModelKey("home", "Ru", 1),
            viewModelKey("home", "Ru", 2)
        )
        assertNotEquals(
            viewModelKey("search", "En", 3),
            viewModelKey("search", "En", 4)
        )
    }
}
