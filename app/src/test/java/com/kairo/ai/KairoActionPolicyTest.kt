package com.kairo.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KairoActionPolicyTest {
    @Test fun appLaunchAlwaysRequiresConfirmation() =
        assertTrue(KairoActionPolicy.requiresConfirmation(KairoAction.OPEN_INSTALLED_APP))

    @Test fun appLaunchCannotRunWithoutConfirmation() =
        assertFalse(KairoActionPolicy.mayExecute(KairoAction.OPEN_INSTALLED_APP, false))

    @Test fun confirmedAppLaunchIsAllowed() =
        assertTrue(KairoActionPolicy.mayExecute(KairoAction.OPEN_INSTALLED_APP, true))
}
