package com.curated.app.core.legal

/**
 * Where the policy pages live. They're served by GitHub Pages from the repo's
 * docs/ folder (docs/privacy.html etc.), so moving them - a custom domain,
 * say - means changing [BASE_URL] only.
 */
object LegalLinks {
    const val BASE_URL = "https://namandalsania.github.io/curated"

    const val PRIVACY_POLICY = "$BASE_URL/privacy.html"
    const val TERMS_OF_USE = "$BASE_URL/terms.html"
    const val DELETE_ACCOUNT = "$BASE_URL/delete-account.html"
}
