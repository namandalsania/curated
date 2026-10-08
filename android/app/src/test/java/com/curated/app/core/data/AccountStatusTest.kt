package com.curated.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AccountStatusTest {

    @Test
    fun `a successful check means the account exists`() {
        assertEquals(AccountStatus.EXISTS, accountStatusOf(succeeded = true, authErrorCode = null))
        assertEquals(AccountStatus.EXISTS, accountStatusOf(failure = null))
    }

    @Test
    fun `only the server saying user_not_found means gone`() {
        // What GET /auth/v1/user returns for a deleted account (measured: 403).
        assertEquals(AccountStatus.GONE, accountStatusOf(succeeded = false, authErrorCode = "user_not_found"))
    }

    @Test
    fun `other auth errors don't sign anyone out`() {
        // What refreshing a deleted account's session returns (measured: 400) -
        // but signing out on another device returns it too.
        assertEquals(AccountStatus.UNKNOWN, accountStatusOf(succeeded = false, authErrorCode = "refresh_token_not_found"))
        assertEquals(AccountStatus.UNKNOWN, accountStatusOf(succeeded = false, authErrorCode = "session_not_found"))
        assertEquals(AccountStatus.UNKNOWN, accountStatusOf(succeeded = false, authErrorCode = "unexpected_failure"))
    }

    @Test
    fun `network failures and timeouts never mean gone`() {
        assertEquals(AccountStatus.UNKNOWN, accountStatusOf(succeeded = false, authErrorCode = null))
        assertEquals(AccountStatus.UNKNOWN, accountStatusOf(IOException("connection reset")))
        assertEquals(AccountStatus.UNKNOWN, accountStatusOf(SocketTimeoutException("timeout")))
        assertEquals(AccountStatus.UNKNOWN, accountStatusOf(UnknownHostException("offline")))
        assertEquals(AccountStatus.UNKNOWN, accountStatusOf(IllegalStateException("user_not_found")))
    }
}
