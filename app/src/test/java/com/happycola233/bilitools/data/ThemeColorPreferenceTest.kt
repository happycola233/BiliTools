package com.happycola233.bilitools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeColorPreferenceTest {
    @Test
    fun disablingDynamicColorWithoutManualSelectionUsesSakura() {
        assertEquals(
            AppThemeColor.Sakura,
            resolveThemeColorAfterDynamicToggle(
                enabled = false,
                currentColor = AppThemeColor.Dynamic,
                lastManualColorValue = null,
            ),
        )
    }

    @Test
    fun disablingDynamicColorRestoresLastManualSelection() {
        assertEquals(
            AppThemeColor.Iris,
            resolveThemeColorAfterDynamicToggle(
                enabled = false,
                currentColor = AppThemeColor.Dynamic,
                lastManualColorValue = AppThemeColor.Iris.value,
            ),
        )
    }

    @Test
    fun enablingDynamicColorRemembersCurrentManualSelection() {
        assertEquals(
            AppThemeColor.Mint,
            manualThemeColorToRemember(
                currentColor = AppThemeColor.Mint,
                selectedColor = AppThemeColor.Dynamic,
            ),
        )
    }

    @Test
    fun selectingManualColorReplacesRememberedSelection() {
        assertEquals(
            AppThemeColor.Sky,
            manualThemeColorToRemember(
                currentColor = AppThemeColor.Dynamic,
                selectedColor = AppThemeColor.Sky,
            ),
        )
    }

    @Test
    fun repeatedDynamicSelectionDoesNotCreateManualHistory() {
        assertNull(
            manualThemeColorToRemember(
                currentColor = AppThemeColor.Dynamic,
                selectedColor = AppThemeColor.Dynamic,
            ),
        )
    }
}
