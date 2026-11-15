package com.orienteer.app.presentation.settings

import com.orienteer.app.R
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsWarningContractTest {

    @Test
    fun dataWarningStringResourceExists() {
        // Resource id must remain for Settings declutter (warnings kept).
        assertTrue(R.string.settings_data_warning != 0)
    }
}
