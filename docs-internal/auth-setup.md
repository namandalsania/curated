# Auth setup — what the Supabase project needs

The app's password reset uses a **6-digit code**, not a link. None of this is
configured by the code; check each item in the Supabase dashboard.

## 1. Reset Password email template (required for reset to work)

Dashboard → **Authentication → Emails → Templates → Reset Password**.

The default template only contains `{{ .ConfirmationURL }}` (a link). The app
never opens links, so replace the body with one that shows the code:

```html
<h2>Reset your Curated password</h2>
<p>Enter this code in the app to choose a new password:</p>
<p style="font-size:28px;font-weight:600;letter-spacing:6px">{{ .Token }}</p>
<p>It expires in 1 hour. If you didn't ask for this, you can ignore this email —
your password hasn't changed.</p>
```

Subject suggestion: `Your Curated password reset code`.

## 2. OTP length and expiry

Dashboard → **Authentication → Providers → Email**:

- **Email OTP Length** must be **6** — the app only accepts 6 digits
  (`AuthRules.CODE_LENGTH`). If the project shows 8, either set it to 6 or
  change that constant.
- **Email OTP Expiration**: default 3600 s is fine; the template above says
  "1 hour", so keep them in sync.

## 3. Email delivery (SMTP)

Supabase's built-in email service is for testing: it is heavily rate-limited
and may only deliver to addresses of project team members. For real users, set
**Authentication → Emails → SMTP Settings** to your own provider (Resend,
Postmark, SES…). The app's 60-second resend cooldown is on top of Supabase's
own per-address limit.

## 4. Confirm signup (only if "Confirm email" is on)

Sign-up works either way:

- **Off**: the account is signed in immediately and the profile (name +
  username from the form) is created.
- **On**: the app shows "Check your inbox". The profile is created on the first
  sign-in after confirming. Signing in before confirming shows
  "Your email isn't confirmed yet" with a **Resend confirmation email** button.

The Confirm signup template can stay link-based.

---

## Google sign-in — setup steps (not implemented)

For deciding later. Roughly half a day, most of it console configuration:

1. **Google Cloud project** (can be `curated-508619`, which already hosts the Maps key)
   - OAuth consent screen: app name, support email, logo, privacy policy URL;
     scopes `openid`, `email`, `profile`. Publish it (testing mode limits to 100 listed users).
2. **OAuth client IDs** — three are needed:
   - **Web** client — its ID and secret go into Supabase (step 4); its ID is
     also the `serverClientId` the Android app requests an ID token for.
   - **Android** client for the **debug** keystore: package `com.curated.app`,
     SHA-1 `3A:7B:77:9B:81:4E:59:2D:67:43:12:83:25:40:C9:18:EB:3E:E6:55` (from
     `./gradlew signingReport`; same one the Maps key uses).
   - **Android** client for the **release / Play App Signing** key — the SHA-1
     from Play Console → App integrity once the app is uploaded. Without it,
     Google sign-in fails only in the Play build.
3. **Android app**
   - Add `androidx.credentials:credentials`, `credentials-play-services-auth`
     and `com.google.android.libraries.identity.googleid:googleid`.
   - Use **Credential Manager** with `GetGoogleIdOption` (or
     `GetSignInWithGoogleOption` for the button), passing the **Web** client
     ID and a hashed nonce.
   - Hand the returned ID token to Supabase:
     `auth.signInWith(IDToken) { idToken = …; provider = Google; nonce = rawNonce }`.
   - New Google users arrive with no `users` row, so they land in profile setup
     (display name prefilled from the Google profile, username chosen there).
4. **Supabase** → Authentication → Providers → **Google**: enable, paste the Web
   client ID + secret, and add the Android client IDs to *Authorized Client IDs*
   so ID tokens minted for the app are accepted. Enable *Skip nonce check* only
   if you don't pass a nonce (not recommended).
5. **Account linking**: decide what happens when someone signs in with Google
   using an email that already has a password account (Supabase links verified
   emails automatically by default).
