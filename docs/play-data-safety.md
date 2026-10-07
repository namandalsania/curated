# Play Console: Data safety answers (DRAFT)

> **DRAFT: not legal advice.** Prepared from the code on 6 October 2026, as
> input for the Data safety form in Play Console (App content → Data safety).
> The owner must check every answer against Google's current definitions
> before submitting. Rows marked **⚠ check** are ones where Play's
> classification is genuinely uncertain.

Definitions used below follow Play's form:

- **Collected:** sent off the device (to us or a provider). Data used only on
  the device isn't collected.
- **Shared:** transferred to a third party. Play does *not* count transfers to
  **service providers** acting on our behalf (Supabase, Google Maps Platform,
  Firebase), or **transfers the user initiates** (posting publicly, sending a
  trip to someone), as sharing.
- **Optional:** the user can use the app without providing it.

## Overview section

| Question | Answer | Basis |
|---|---|---|
| Does the app collect or share any of the required user data types? | **Yes** | Account, content, photos, crash data |
| Is all collected data encrypted in transit? | **Yes** | All traffic is HTTPS: Supabase (`https://…supabase.co`), Google Maps/Places, Firebase. The app has no cleartext traffic. |
| Do you provide a way for users to request that their data be deleted? | **Yes** | In app: Settings → Delete account. On the web: `delete-account.html` (URL below). |
| Account creation | **Username and password** (email + password via Supabase Auth). No other sign-in methods. | `AuthRepository` |
| Delete account URL | `https://namandalsania.github.io/curated/delete-account.html` | Pending GitHub Pages being enabled |

## Data types

| Play category → data type | Collected? | Shared? | Optional? | Purposes | What it is in Curated |
|---|---|---|---|---|---|
| **Personal info → Name** | Yes | No | Required | App functionality, Account management | Display name, shown publicly on the profile |
| **Personal info → Email address** | Yes | No | Required | App functionality, Account management | Sign-in, confirmation and reset emails (Supabase Auth) |
| **Personal info → User IDs** | Yes | No | Required | App functionality, Account management | Username and the account's internal ID |
| **Personal info → Other info** | Yes | No | Optional | App functionality | Bio. ⚠ check whether a free-text bio needs declaring here or is covered by "Other user-generated content". |
| **Location → Precise location** | Yes | No | Optional | App functionality | GPS position read from the **metadata of photos the user picks** (permission `ACCESS_MEDIA_LOCATION`), saved as each place's coordinates, and **kept inside the uploaded photo files**. The app never reads the device's own location. ⚠ check: Play's location types are framed around device location; declaring "Precise location" is the cautious answer, because exact coordinates are stored and public trips show them. |
| **Location → Approximate location** | Yes | No | Optional | App functionality | City and country derived from those coordinates (Android geocoder). ⚠ check: may be covered by the Precise row. |
| **Photos and videos → Photos** | Yes | No | Optional | App functionality | Photos added to places and the profile photo, uploaded as original files, metadata included |
| **Messages → Other in-app messages** | Yes | No | Optional | App functionality | Comments on places; notes sent with trip shares. ⚠ check: comments may instead belong under "Other user-generated content". Declaring both is the cautious answer. |
| **App activity → App interactions** | Yes | No | Optional | App functionality | Likes, follows, saved trips, saved places, blocks, and read state of notifications and shares |
| **App activity → In-app search history** | Yes | No | Optional | App functionality | Text typed into place search is sent to Google Places to get suggestions; trip search terms are sent to Supabase to run the query. Neither is stored as history. ⚠ check: collected-but-not-stored may still count as collected. |
| **App activity → Other user-generated content** | Yes | No | Optional | App functionality | Trips, days, places, captions, tips, costs, arrival times, plans, reports (reason and note) |
| **App info and performance → Crash logs** | Yes | No | Required\* | Analytics (app stability) | Firebase Crashlytics crash reports |
| **App info and performance → Diagnostics** | Yes | No | Required\* | Analytics (app stability) | Device model, OS version, app version, memory and disk state sent with crash reports |
| **Device or other IDs → Device or other IDs** | Yes | No | Required\* | Analytics (app stability) | Crashlytics installation UUID and Firebase installation ID, used to group crashes. ⚠ check: Google's Firebase guidance says to declare this for Crashlytics. |

\* "Required" because Crashlytics runs for every user and there is no opt-out
in the app. If an opt-out is added later, these can become optional.

## Not collected

| Data type | Why not |
|---|---|
| Financial info | No payments. A place's **cost** is a user-entered price note (declared as user-generated content), not purchase history. |
| Health and fitness, Contacts, Calendar | Not accessed |
| Audio, Video, Music | Not accessed |
| Files and docs | Photos are picked through the system file picker; only images are read. ⚠ check: no other files are accessed. |
| Web browsing | No in-app browser history. The policy links open in the user's browser. |
| Advertising ID | Not used. No ads or ad SDKs. |

## Security practices

- **Encrypted in transit:** yes, all requests use HTTPS.
- **Deletion:** users can delete their account in the app, or request it at the
  delete-account URL. What deletion removes and keeps is listed in the Privacy
  Policy.
- **Independent security review:** no.
- **Committed to Play Families Policy:** no (not directed at children).

## Things that affect these answers

1. **Photo metadata.** Photos are uploaded with their EXIF metadata,
   including GPS. That keeps "Precise location" firmly collected, and the
   precise position of photos in **unlisted and private** trips sits in files
   that anyone with the address can open. If metadata is stripped before
   upload, the photo row stays but the location exposure narrows to the stop
   coordinates the user sees.
2. **Crashlytics opt-out.** Adding one would let the crash and diagnostics rows
   be marked optional.
3. **Service providers.** Supabase, Google Maps Platform and Firebase are
   declared as service providers, so nothing is "shared". If the owner's
   reading differs for Google Places (which receives typed search text and
   coordinates), mark those rows "Shared: Yes, App functionality".
