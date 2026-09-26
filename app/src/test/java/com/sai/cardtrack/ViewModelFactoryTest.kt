package com.sai.cardtrack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleViewModel : ViewModel()

class ViewModelFactoryTest {

    @Test
    fun `untyped ViewModel initializer cannot create the requested subclass`() {
        val factory = viewModelFactory {
            initializer<ViewModel> { object : ViewModel() {} }
        }
        try {
            factory.create(SampleViewModel::class.java, MutableCreationExtras())
            throw AssertionError("expected missing initializer for subclass")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message, error.message!!.contains("initializer"))
        }
    }

    @Test
    fun `reified factory creates the concrete class compose requests`() {
        val created = factory { SampleViewModel() }
            .create(SampleViewModel::class.java, MutableCreationExtras())
        assertEquals(SampleViewModel::class.java, created.javaClass)
    }
}
