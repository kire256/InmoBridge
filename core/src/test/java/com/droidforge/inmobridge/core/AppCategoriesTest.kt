package com.droidforge.inmobridge.core

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoriesTest {

    private fun info(
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
        system: Boolean = false,
    ): ApplicationInfo =
        ApplicationInfo().apply {
            this.category = category
            if (system) flags = flags or ApplicationInfo.FLAG_SYSTEM
        }

    @Test
    fun `declared game category wins`() {
        assertEquals("games", AppCategories.classify(info(ApplicationInfo.CATEGORY_GAME), "Whatever"))
    }

    @Test
    fun `declared audio video image social map to media`() {
        for (c in intArrayOf(
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_SOCIAL,
        )) {
            assertEquals("media", AppCategories.classify(info(c), "Whatever"))
        }
    }

    @Test
    fun `declared productivity maps to tools`() {
        assertEquals("tools", AppCategories.classify(info(ApplicationInfo.CATEGORY_PRODUCTIVITY), "Docs"))
    }

    @Test
    fun `name heuristics classify by label`() {
        assertEquals("games", AppCategories.classify(info(), "2048 Puzzle"))
        assertEquals("games", AppCategories.classify(info(), "C64.emu Emulator"))
        assertEquals("tools", AppCategories.classify(info(), "Button Mapper"))
        assertEquals("media", AppCategories.classify(info(), "Snapdragon Camera"))
        assertEquals("system", AppCategories.classify(info(), "Android Settings"))
    }

    @Test
    fun `bucket falls back to system then other`() {
        assertEquals("system", AppCategories.bucket(info(system = true), "Completely Unknown App"))
        assertEquals("other", AppCategories.bucket(info(), "Completely Unknown App"))
        assertEquals("games", AppCategories.bucket(info(ApplicationInfo.CATEGORY_GAME), "Unknown"))
    }

    @Test
    fun `order and labels cover the same ids`() {
        assertEquals(AppCategories.ORDER.size, AppCategories.LABELS.size)
        assertEquals(AppCategories.ORDER.size, AppCategories.ICONS.size)
    }
}
