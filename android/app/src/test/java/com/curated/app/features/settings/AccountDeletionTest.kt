package com.curated.app.features.settings

import com.curated.app.core.data.DeleteAccountOutcome
import com.curated.app.core.data.deleteAccountOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionTest {

    // --- The confirmation gate ---

    @Test
    fun `the button needs the password and DELETE typed exactly`() {
        assertTrue(canDeleteAccount("pw", "DELETE"))
        assertTrue(canDeleteAccount("pw", "  DELETE "))
        assertFalse(canDeleteAccount("", "DELETE"))
        assertFalse(canDeleteAccount("pw", "delete"))
        assertFalse(canDeleteAccount("pw", "DELET"))
        assertFalse(canDeleteAccount("pw", "DELETE ACCOUNT"))
        assertFalse(canDeleteAccount("pw", ""))
    }

    @Test
    fun `nothing can be pressed while a deletion is running`() {
        assertFalse(DeleteAccountState(password = "pw", confirmation = "DELETE", isDeleting = true).canDelete)
    }

    // --- Reading the function's answers (see Supabase/functions/delete-account/handler.ts) ---

    @Test
    fun `deleted and already deleted both count as done`() {
        assertEquals(DeleteAccountOutcome.DELETED, deleteAccountOutcome(200, """{"status":"deleted"}"""))
        assertEquals(DeleteAccountOutcome.DELETED, deleteAccountOutcome(200, """{"status":"already_deleted"}"""))
    }

    @Test
    fun `an old sign-in asks for the password again`() {
        assertEquals(
            DeleteAccountOutcome.REAUTH_REQUIRED,
            deleteAccountOutcome(403, """{"error":"reauthentication_required"}""")
        )
    }

    @Test
    fun `a refused session, a failure partway, or anything unexpected is not taken as success`() {
        assertEquals(DeleteAccountOutcome.NOT_SIGNED_IN, deleteAccountOutcome(401, """{"error":"not_signed_in"}"""))
        assertEquals(DeleteAccountOutcome.FAILED, deleteAccountOutcome(500, """{"error":"storage_failed","retryable":true}"""))
        assertEquals(DeleteAccountOutcome.FAILED, deleteAccountOutcome(500, """{"error":"delete_failed","retryable":true}"""))
        // Not deployed yet: the gateway's 404.
        assertEquals(DeleteAccountOutcome.FAILED, deleteAccountOutcome(404, "Requested function was not found"))
        assertEquals(DeleteAccountOutcome.FAILED, deleteAccountOutcome(200, ""))
        assertEquals(DeleteAccountOutcome.FAILED, deleteAccountOutcome(200, null))
    }

    @Test
    fun `every failure has something to say, success has nothing`() {
        assertNull(deleteAccountMessage(DeleteAccountOutcome.DELETED))
        DeleteAccountOutcome.entries.filter { it != DeleteAccountOutcome.DELETED }
            .forEach { assertNotNull(it.name, deleteAccountMessage(it)) }
    }
}
