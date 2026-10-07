package com.curated.app.core.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalLinksTest {

    private val pages = mapOf(
        LegalLinks.PRIVACY_POLICY to "privacy.html",
        LegalLinks.TERMS_OF_USE to "terms.html",
        LegalLinks.DELETE_ACCOUNT to "delete-account.html"
    )

    @Test
    fun `every page hangs off the one base URL, over https`() {
        assertTrue(LegalLinks.BASE_URL.startsWith("https://"))
        assertTrue(!LegalLinks.BASE_URL.endsWith("/"))
        pages.forEach { (url, file) -> assertEquals("${LegalLinks.BASE_URL}/$file", url) }
    }
}
