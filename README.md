# Personal Notes

An offline-first Android notes app: type a note, dictate it, or record it, tag it with a
colour, a time and a place, then send it to another user of the same app as a link or a file.

Kotlin, Jetpack Compose, Material 3, Room. The whole UI is a warm yellow gradient, starting
from an animated splash screen, and the mark is a ruled sheet of paper with a pencil across it.

An iOS companion app with the same features and design lives beside this directory in
`../PersonalNotes-iOS` (SwiftUI, iOS 17+). The two apps share the `.pnote` file format and the
`personalnotes://note/d/<token>` link format, so a note written on one opens on the other — see
the iOS README's "Cross-platform notes" section.

## The logo

The app mark is a **ruled sheet of paper with a pencil resting diagonally across it**, drawn as
vector artwork in the 108x108 adaptive-icon viewport:

| File | Purpose |
| --- | --- |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | The artwork: page, ruled lines, margin rule, pencil with ferrule and eraser. |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Warm yellow gradient with a corner glow. |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Flat silhouette for Android 13+ themed icons. |
| `app/src/main/res/mipmap-anydpi/ic_launcher{,_round}.xml` | Adaptive icon combining the three. |

Everything that must survive a round or squircle mask sits inside the central 72x72 safe zone;
only the pencil's tip and eraser reach past it. The vector uses flat fills only — no gradients,
strokes or filters inside the foreground layer — so it renders identically in every launcher, on
the splash screen, and as a themed icon.

In the app the same artwork is shown through `NotesLogo` (`presentation/components/NotesLogo.kt`)
on the animated splash screen and in the "My notes" header, so there is one source of truth for
the mark.

`design/logo-preview.svg` mirrors the vector for quick visual checks, and
`design/sync-logo-preview.py` regenerates it from the drawable so the two cannot drift:

```bash
python3 design/sync-logo-preview.py
```

## Features

| Area | What it does |
| --- | --- |
| Typing | Title + body, live editing. |
| Speaking | Live dictation through the platform speech recogniser (partial results grow the note instead of duplicating it). |
| Recording | Voice note recorded with `MediaRecorder`; a mic halo reacts to the input level. |
| Note fields | Title, description, created/updated time, place (coordinates + readable label), colour, kind (typed / spoken / mixed), attached recordings. |
| Local storage | Room database plus app-private files. Nothing leaves the device unless the user shares it. |
| Sharing | A `.pnote` file for the share sheet, and a self-contained deep link that carries the note itself. |
| Receiving | Deep link, tapped file, or "Share to Personal Notes" from another app. |
| List | Search across title/text/place, colour filter, four sort orders, swipe-free delete with confirmation. |

## Architecture

Clean architecture in a single Gradle module, with the dependency direction pointing inwards:

```
presentation/   Compose screens, ViewModels, theme, components
      │  depends on
domain/         models (Note, NoteColor, NoteLocation, ...), repository ports, use cases
      │  implemented by
data/           Room, .pnote codec, FileProvider sharing, location, voice
      │
di/AppContainer manual dependency injection (no annotation processor)
core/           deep link parsing and date formatting helpers
```

* `domain` is pure Kotlin: no Android imports, so the use cases are unit tested on the JVM.
* `data` implements the ports. The wire format lives in `data/codec` and is also Android-free.
* `presentation` never touches Room or files; it talks to use cases only.
* `di/AppContainer` is a hand-written object graph. The graph is small and everything is
  constructor-injected, so swapping in Hilt later means replacing one class.

### Why a hand-written container instead of Hilt

AGP 9 compiles Kotlin itself and registers the `kotlin` extension, which changes how
annotation-processor plugins are wired. A manual container removes that entire class of build
risk while keeping constructor injection and testability intact.

## Sharing: the `.pnote` format

A shared note is versioned JSON with a recognisable first line:

```
PERSONALNOTES-NOTE
{
  "schema": "com.kostovskiapps.personalnotes/note",
  "version": 1,
  "id": "local-42",
  "title": "Groceries",
  "description": "Milk, eggs",
  "createdAt": 1700000000000,
  "updatedAt": 1700000500000,
  "colorId": "amber",
  "kindId": "mixed",
  "location": { "label": "Vienna, Austria", "latitude": 48.2082, "longitude": 16.3738 },
  "attachments": [
    { "name": "voice-1700000000000.m4a", "mimeType": "audio/mp4", "data": "<base64url>" }
  ]
}
```

Recordings up to 2 MB are inlined so a single file really is the whole note. Key names are
pinned by `NotePayloadCodecTest`, because a key the DTO does not declare would be silently
dropped when the file is opened.

### Deep links

| Link | Behaviour |
| --- | --- |
| `personalnotes://note/d/<token>` | The token is gzipped + base64url JSON: the link *is* the note. Works through any messenger. |
| `personalnotes://note/42` | Opens note 42, which is already stored on this device. |
| `https://personalnotes.app/note/42` | Same, as an https app link (`autoVerify` is off pending Digital Asset Links). |
| A `content://` `.pnote` uri | Opened from a chat, mail client or file manager. |
| `ACTION_SEND` / `ACTION_SEND_MULTIPLE` | "Share to Personal Notes" from any app; several notes at once are accepted. |

Imports are idempotent: the payload is fingerprinted on content, so receiving the same note
twice — once as a link, once as a file — produces one stored note and an "already in your
notes" message rather than a duplicate.

## Permissions

Requested at first use, never on launch:

* `RECORD_AUDIO` — dictation and voice notes.
* `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — optional place tag. Typing a place by
  hand always works, and a failed fix never blocks saving.
* `INTERNET` — only because dictation may use the network recogniser; notes themselves are
  fully offline.

## Building

```bash
./gradlew :app:assembleDebug      # APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # codec, deep link and use case tests
./gradlew :app:lintDebug          # lint, currently 0 errors
```

Requires a JDK 17+ (`JAVA_HOME`) and the Android SDK (see `local.properties`).
`compileSdk`/`targetSdk` 37, `minSdk` 30.

`gradle.properties` sets `kotlin.compiler.execution.strategy=in-process` so the build does not
depend on the Kotlin compile daemon's marker directory, which is read-only in sandboxed
environments.

## Firebase App Distribution

Builds are shipped to testers with [Firebase App
Distribution](https://firebase.google.com/docs/app-distribution). The Gradle wiring is already in
place; it needs a Firebase project.

### One-time setup

```bash
firebase login                        # or: firebase login:ci  (for CI)

# Pick or create a project, then register this app.
firebase projects:list
firebase projects:create personal-notes-<something> --display-name "Personal Notes"

firebase apps:create ANDROID "Personal Notes" \
  --package-name com.kostovskiapps.personalnotes \
  --project <PROJECT_ID>

# Download the config the build reads. <APP_ID> is printed by apps:create.
firebase apps:sdkconfig ANDROID <APP_ID> --project <PROJECT_ID> > app/google-services.json
```

`app/google-services.json` is **required to build** and is git-ignored — it carries project
identifiers and an API key, so it is supplied per machine or from a CI secret via
`GOOGLE_SERVICES_JSON=/path/to/google-services.json`. Until it exists,
`:app:checkFirebaseConfig` fails with these same instructions rather than the plugin's raw error.
`app/google-services.template.json` shows the expected shape.

In the Firebase console, open **App Distribution → Testers & Groups** and create the group you
want to release to. Individual addresses do not need to be created first — an upload adds them
as testers.

### Releasing

```bash
# Release to a tester group, using the signed-in Firebase CLI for credentials.
./gradlew :app:appDistributionUploadRelease -Pgroups=qa-team

# Individual testers, custom notes, or a service account for CI.
./gradlew :app:appDistributionUploadRelease \
  -Ptesters=alex@example.com,sam@example.com \
  -PreleaseNotes="Sprint 12 build" \
  -PserviceCredentialsFile=/secure/firebase-service-account.json
```

Testers receive a Firebase invitation, install the app, and get every later build as an update.

#### If the CLI credentials are stale

`firebase login --reauth` refreshes the browser session, but a stale entry in
`~/.config/configstore/firebase-tools.json` can survive it: the token exchange returns a new
access token without a new refresh token, leaving the CLI reporting
`Your credentials are no longer valid`. For scripted releases the reliable fix is a service
account:

1. Firebase console → gear → **Project settings → Service accounts → Generate new private key**.
2. Grant it the **Firebase App Distribution Admin** role (IAM).
3. Keep the JSON outside the repository — it is a credential.

```bash
./gradlew :app:appDistributionUploadRelease \
  -Ptesters=kostovskiapps@gmail.com \
  -PserviceCredentialsFile=/path/to/firebase-service-account.json
```

Or export it once and omit the flag:

```bash
export FIREBASE_SERVICE_CREDENTIALS=/path/to/firebase-service-account.json
./gradlew :app:appDistributionUploadRelease -Ptesters=kostovskiapps@gmail.com
```

A successful run prints the console release link, and testers receive the invitation email.

| Property / variable | Purpose |
| --- | --- |
| `-Pgroups=…` | Tester **groups** to release to (created in the console). |
| `-Ptesters=…` | Individual addresses, comma separated. |
| `-PserviceCredentialsFile=…` | Service account JSON, for CI. |
| `FIREBASE_SERVICE_CREDENTIALS` | Same, as an environment variable. |
| `-PreleaseNotes="…"` | Release notes. Defaults to `git log -1`. |
| `GOOGLE_SERVICES_JSON` | Where to find `google-services.json`. |

Two Gradle details worth knowing, both discovered while wiring this up:

* The App Distribution artifact publishes **no Gradle plugin marker**, so `plugins { id(...) }`
  cannot resolve it. The root build adds `com.google.firebase:firebase-appdistribution-gradle` to
  the `buildscript` classpath (version from the catalog) and applies it from `subprojects { }`,
  which also gives the right ordering for configuring its extension.
* It is configured through its own non-deprecated `firebaseAppDistributionDefault` DSL rather
  than the older `firebaseAppDistribution { }` block.

### Signing

The release build currently signs with the debug keystore so that `assembleRelease` and the
upload task work out of the box for internal testing. Upload keys live in Gradle's signing config
and are never committed; before any store release, replace that block with a real upload key
(`*.keystore` is already git-ignored).

## Tests

* `NotePayloadCodecTest` — file/link round trips, exact wire-format keys, gzip, base64url edge
  lengths, fingerprints, and a payload written by an "external" producer.
* `DeepLinkParserTest` — every link shape the app can receive, plus the ones it must ignore.
* `NoteUseCasesTest` — filtering, all four sort orders, validation and timestamps, using an
  in-memory repository.

## Known gaps

* The share sheet's system chooser and the "send file" intent are exercised through the same
  code path as the verified export, but were not driven end-to-end by an automated test.
* Location reverse geocoding depends on the device's `Geocoder`; when it is unavailable the
  note keeps its coordinates.
* App links over https need Digital Asset Links published on the host to verify; until then the
  link opens through the chooser.
* The release build is signed with the debug keystore (see "Signing" above).
* `app/google-services.json` is required to build, so a fresh clone needs the Firebase step in
  "Firebase App Distribution" before `./gradlew assembleDebug` succeeds.
