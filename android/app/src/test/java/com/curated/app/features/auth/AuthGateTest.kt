package com.curated.app.features.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthGateTest {

    @Test
    fun `an open app stays open while the session settles`() {
        assertEquals(AuthGatePhase.READY, phaseWhileSettling(AuthGatePhase.READY))
    }

    @Test
    fun `profile setup stays open while the session settles`() {
        assertEquals(AuthGatePhase.NEEDS_PROFILE, phaseWhileSettling(AuthGatePhase.NEEDS_PROFILE))
    }

    @Test
    fun `before anything is showing, a settling session still waits`() {
        assertEquals(AuthGatePhase.LOADING, phaseWhileSettling(AuthGatePhase.LOADING))
        assertEquals(AuthGatePhase.LOADING, phaseWhileSettling(AuthGatePhase.SIGNED_OUT))
    }
}
