package com.curated.app.features.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {

    @Test
    fun `usernames are trimmed and lowercased`() {
        assertEquals("maya_chen", AuthRules.normalizeUsername("  Maya_Chen "))
    }

    @Test
    fun `username rules`() {
        assertNull(AuthRules.usernameProblem("maya.chen_92"))
        assertNotNull(AuthRules.usernameProblem("ab"))
        assertNotNull(AuthRules.usernameProblem("a".repeat(21)))
        assertNotNull(AuthRules.usernameProblem("maya chen"))
        assertNotNull(AuthRules.usernameProblem("maya-chen"))
        assertNotNull(AuthRules.usernameProblem(".maya"))
        assertNotNull(AuthRules.usernameProblem("maya."))
    }

    @Test
    fun `password needs eight characters`() {
        assertNotNull(AuthRules.passwordProblem("1234567"))
        assertNull(AuthRules.passwordProblem("12345678"))
    }

    @Test
    fun `code must be exactly six digits`() {
        assertTrue(AuthRules.isCodeComplete("123456"))
        assertFalse(AuthRules.isCodeComplete("12345"))
        assertFalse(AuthRules.isCodeComplete("12345a"))
    }

    @Test
    fun `email plausibility`() {
        assertTrue(AuthRules.isEmailPlausible(" maya@example.com "))
        assertFalse(AuthRules.isEmailPlausible("maya@example"))
        assertFalse(AuthRules.isEmailPlausible("maya example.com"))
    }
}
