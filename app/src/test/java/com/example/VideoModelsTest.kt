package com.example

import com.example.model.EnhancementPreset
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoModelsTest {

    @Test
    fun presetsProvideValidShaderRanges() {
        EnhancementPreset.entries.forEach { preset ->
            val p = preset.defaults
            assertTrue(p.sharpen in 0f..1f)
            assertTrue(p.denoise in 0f..1f)
            assertTrue(p.hdrClarity in 0f..1f)
            assertTrue(p.vibrance in 0f..1f)
        }
    }

    @Test
    fun formatHelpersFormatDurationAndFileSize() {
        assertEquals("0:03", formatDuration(3500L))
        assertEquals("1:05", formatDuration(65_000L))
        assertEquals("2.0 MB", formatFileSize(2L * 1024L * 1024L))
        assertEquals("512 KB", formatFileSize(512L * 1024L))
    }
}
