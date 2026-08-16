package com.example.pokedex

import com.example.pokedex.domain.scanner.ImageProcessingSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProcessingSettingsTest {
    @Test
    fun `image processing settings clamp untrusted values to bounded memory limits`() {
        val settings = ImageProcessingSettings(maxEdge = 99_999, jpegQuality = 0).sanitized()

        assertEquals(2_048, settings.maxEdge)
        assertEquals(70, settings.jpegQuality)
    }

    @Test
    fun `image processing settings keep minimum quality and size usable`() {
        val settings = ImageProcessingSettings(maxEdge = 1, jpegQuality = 1).sanitized()

        assertEquals(448, settings.maxEdge)
        assertEquals(70, settings.jpegQuality)
    }
}
