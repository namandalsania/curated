---
title: Privacy Policy
---

> **DRAFT: not legal advice.** This draft was written from how the app is
> built on 6 October 2026. It must be reviewed by the owner (and ideally a
> lawyer) before it is published or relied on. Text in **[PLACEHOLDER: …]**
> must be filled in.

# Curated Privacy Policy

**Last updated:** [PLACEHOLDER: publication date]

Curated is an app for recording trips day by day and sharing them. This policy
explains what information Curated collects, how it's used, who can see it, and
how to delete it. It covers the Curated Android app and the pages it links to.

Curated is run by **[PLACEHOLDER: legal name of the operator - person or
company]** ("we", "us"). You can contact us at **[PLACEHOLDER: contact email]**.

## The short version

- You need an account (email and password) to use Curated.
- Trips you make **public** can be seen by anyone. **Unlisted** trips can be
  seen by anyone who has the link. **Private** trips are only for you.
- To build a trip from your photos, the app reads the location and time stored
  in each photo **on your phone**. Before a photo is uploaded, the app
  **removes that stored information** from the file (see "Photos and their
  metadata").
- We use Supabase to store your account and content, Google for maps and place
  names, and Firebase Crashlytics to learn about crashes.
- We don't sell your information or show ads.
- You can delete your account, and almost everything in it, at any time from
  the app.

## What we collect

### Your account

| What | Why | Required? |
|---|---|---|
| Email address | Signing in, confirming your account, resetting your password | Yes |
| Password | Signing in. It's stored by our authentication provider, Supabase, as a one-way hash; we never see it. | Yes |
| Name and username | Shown on your profile and next to what you post | Yes |
| Profile photo and bio | Shown on your profile | No |

### What you create

- **Trips:** title, destination, dates, optional budget and season tags,
  visibility (public, unlisted or private), and whether the trip is a draft,
  live or finished.
- **Days and places (stops):** for each place, its name, category and map
  position, and anything you add: a caption, tips, cost, and the time you
  arrived.
- **Photos** you add to places, and the time each was taken (see below).
- **Comments** you write on places in other people's trips.
- **Likes, saved trips, saved places and follows.**
- **Plans:** places you arrange into your own days, and the people you invite
  to a plan.
- **Trips you send to other people**, with any note you add.
- **Reports** you file (the reason, and an optional note) and **accounts you
  block**.

### Photos and their metadata

When you build a trip from your photos, the app reads two things stored inside
each photo: **where it was taken** (GPS coordinates) and **when** (the date and
time). It reads them **on your phone** and uses them to group your photos into
days and places. To do this, the app asks Android for permission to read the
location stored in photos ("access media location"). The app never asks for,
and never reads, your phone's own current location.

**Before a photo is uploaded, the app makes a new copy of it without any
stored metadata**: no GPS position, no date and time, no camera details, and
none of the other information cameras and editing apps store in photos (EXIF,
XMP and IPTC). The copy is turned the right way up and made smaller (at most
2048 pixels on the long side for place photos, 640 for profile photos). Only
that copy is uploaded; the original never leaves your phone.

What is saved separately is what you confirmed while building the trip: each
place's map position, and the time each photo was taken. Those are part of
your trip, and are shown to whoever can see it (see "Who can see what").

[PLACEHOLDER: photos uploaded during testing, before this change, kept their
metadata. Delete or re-process them before launch, then remove this note.]

### Information collected automatically

- **Crash reports.** If the app crashes, Firebase Crashlytics (a Google
  service) receives a report: what went wrong in the app's code, the app
  version, your phone model and Android version, and technical state such as
  free memory and screen orientation. It also uses an identifier for this
  installation of the app, so repeated crashes on one phone can be grouped.
  Crash reports don't include your name, email or content.
- **Service logs.** Like most online services, our providers record technical
  details of requests to them, such as the IP address and time, for security
  and to keep the service running.

We do **not** use advertising identifiers, analytics or tracking for
advertising.

## How we use it

- To run the app: your account, your trips, the feeds, maps, search,
  notifications, comments, sharing and plans.
- To keep people safe: to act on reports, and to apply blocks.
- To fix problems: crash reports and service logs.
- To contact you about your account (for example, confirmation and password
  reset emails).

## Who can see what

### Your profile

Your name, username, profile photo, bio, and how many people you follow and
are followed by are **public**. Anyone using Curated can see them, and so can
the lists of who you follow and who follows you.

### Your trips

You choose this for each trip. For the trip itself (its title, days, places,
captions, tips and comments) the setting is enforced both by the app and by
our database, which refuses to return a trip to someone who isn't allowed to
see it:

- **Public:** shown in Explore, search and your followers' feeds, and on your
  profile. Anyone can open it.
- **Unlisted:** not shown in Explore, search or feeds, but **anyone who has the
  link can open it**. Unlisted isn't secret: treat it like an unlisted video.
- **Private:** only you can see it.

Drafts are only visible to you. While a trip is live, others see only the days
you've posted.

Comments, likes and the places you've saved from other people's trips are
visible to the people who can see that trip.

**Photos work differently.** Photo files are kept in public storage, at long,
random web addresses. The app only shows a photo to people allowed to see its
trip, but **anyone who has a photo's address can open that photo, whatever the
trip's visibility**, for example if someone who could see the trip shares the
address. Photos carry no location or other metadata (see above).

### Blocking

If you block someone, neither of you can see the other's trips, comments or
notifications, any follows between you are removed, and neither of you can
follow, comment on or send trips to the other. The person isn't told. A
blocked person can still see your profile's name, username, photo, bio and
follower counts, but not your trips.

### Reporting

You can report a trip, place, comment or profile. Reports go to **[PLACEHOLDER:
who reviews reports, e.g. "the Curated team"]**, not to the person you
reported, and they aren't told who reported them.

## Who we share it with

We don't sell your information. We share it only with these service providers,
who process it to run Curated:

| Provider | What for | What they receive |
|---|---|---|
| **Supabase** | Stores your account, content and photos; sends account emails | Everything you create, your email, service logs |
| **Google Maps Platform**: Maps SDK and Places API | Showing maps; finding place names and suggestions when you search or build a trip | Map areas you view; the text you type into place search; the map position of places being named |
| **Your phone's geocoding service** (provided by Google on most Android phones) | Turning a map position into a city and country name | Map positions of your places |
| **Firebase Crashlytics** (Google) | Crash reports | The crash information described above |

Google's use of this information is covered by the
[Google Privacy Policy](https://policies.google.com/privacy). Maps are provided
under the [Google Maps/Google Earth Additional Terms of Service](https://maps.google.com/help/terms_maps/).
See also [Supabase's privacy policy](https://supabase.com/privacy) and
[Firebase privacy information](https://firebase.google.com/support/privacy).

Your information is stored on Supabase's servers in **[PLACEHOLDER: Supabase
project region, e.g. "the EU (Frankfurt)"]**. Our providers may process it in
other countries.

We may also disclose information if the law requires it, or to protect people
from harm.

## How long we keep it

- **While your account exists**, we keep what you've created, until you delete
  it (you can delete trips, comments and more at any time) or delete your
  account.
- **Crash reports** are kept by Firebase Crashlytics for about 90 days.
- **Service logs** are kept by our providers for a limited period
  [PLACEHOLDER: check the Supabase plan's log retention].
- **Backups:** deleted information can remain in our database backups for up
  to **[PLACEHOLDER: backup retention period for the Supabase plan]** before
  it's overwritten.
- **Reports about content** are kept after that content or account is deleted,
  for **[PLACEHOLDER: report retention period, e.g. "up to one year"]**, so we
  can deal with abuse.

## Deleting your account

You can delete your account at any time: **Profile → ⋮ → Settings → Delete
account**, then enter your password and type DELETE. It happens straight away
and can't be undone. If you can't use the app, see
[how to request deletion by email](delete-account.html).

**What's deleted:**

- Your account, email and password, name, username, bio and profile photo.
- All your trips (drafts, live and finished), with their days, places and
  photo files.
- Your comments, likes, saved trips and saved places.
- Your plans, and your membership of plans other people shared with you.
- Your follows in both directions.
- Notifications you received, and notifications your actions created for other
  people (for example, "… started following you").
- Trips you sent or were sent.
- Comments, likes and saves that other people made on your trips.
- People you blocked and people who blocked you (the block records), and
  reports you filed.

**What stays:**

- **Places other people saved from your trips** stay in their lists and plans,
  because they belong to those people. Your name, your trip's title and your
  photo are removed from them; the place's name, type and position remain.
  - **One exception:** if someone saved a place from a trip that you had
    *already deleted* before deleting your account, that saved copy can no
    longer be linked back to you, so it keeps the name it showed when it was
    saved (your display name at that time).
- **Reports other people made about your profile or content** are kept, marked
  as being about something that's been deleted, for the period above.
- Copies in **backups** until they're overwritten (see above).

## Your rights

Depending on where you live, you may have rights to access, correct, export or
delete your information, or to object to how it's used. You can see and edit
most of it in the app, and delete it as described above. For anything else,
contact us at **[PLACEHOLDER: contact email]**.
[PLACEHOLDER: add region-specific sections if required, e.g. GDPR legal bases
or California notices.]

## Children

Curated is not for children under **[PLACEHOLDER: minimum age, e.g. 13, or
16 in some countries]**. If you believe a child has created an account,
contact us and we'll delete it.

## Security

Information sent between the app and our providers is encrypted in transit
(HTTPS). Access to your content is controlled by database rules that check who
is asking. No system is perfectly secure; if we learn of a breach affecting
your information, we'll tell you as the law requires.

## Changes

If we change this policy, we'll update the date at the top, and tell you in the
app if the change is significant.

## Contact

**[PLACEHOLDER: legal name of the operator]**\
**[PLACEHOLDER: contact email]**\
**[PLACEHOLDER: postal address, if required]**
