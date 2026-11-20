# MyBooks

**MyBooks** is an offline-first personal digital library and multi-format document reader for Android (Kotlin + Jetpack Compose + Material 3), developed by **Muhammed Omor**, with an optional authenticated **Community** hub powered by Firebase Auth (Google Sign-In) and Cloud Firestore.

- **Application ID**: `com.mybooks.omor.dev`
- **Default Language**: Bangla (`bn`), with instant in-app switching between **বাংলা** and **English**
- **Offline Guarantee**: Importing, organizing, reading, bookmarking, highlighting, note-taking, preferences, and local ZIP Backup & Restore work 100% offline with zero login required.

---

## Supported Document Formats & Honest Limitations

| Tier | Formats | Support Details | Limitations |
| :--- | :--- | :--- | :--- |
| **Core** | **PDF**, **EPUB**, **TXT**, **HTML**, **Markdown (`.md`)** | PDF rendered natively via Android `PdfRenderer` with pinch-to-zoom, single-page or continuous vertical scroll, embedded text search (`PdfTextExtractor`), and **offline ML Kit Latin OCR** (`OfflineOcrHelper`) for scanned pages. EPUB parsed offline into reflowable chapters, TOC, cover art, metadata, and search. HTML/Markdown/TXT rendered safely with sanitized text extraction and optional **offline TTS Read Aloud** (`TtsReadAloudManager`). | Password-protected or DRM-encrypted PDFs/EPUBs cannot be unlocked offline. Offline OCR uses on-device ML Kit Latin recognition. |
| **Extended** | **DOCX**, **RTF**, **ODT**, **FB2**, **DOC** (detected) | DOCX (`word/document.xml`), ODT (`content.xml`), FB2 XML, and RTF control-word decoding are parsed directly offline. | Legacy binary Word 97–2003 (`.doc`) cannot be converted offline; MyBooks detects it safely and recommends converting to `.docx` or `.pdf`. |
| **Specialized** | **CBZ**, **MOBI**, **AZW3**, **XPS**, **CBR** (detected) | CBZ image archives open page-by-page with zoom. Uncompressed & PalmDOC-LZ77 DRM-free MOBI/AZW3 and XPS `.fpage` glyphs extract cleanly. | Proprietary RAR (`.cbr`), HUFF/CDIC compressed MOBI, and DRM-encrypted Kindle files are rejected cleanly with clear user guidance. |
| **Images** | **JPG**, **JPEG**, **PNG**, **WEBP**, **TIFF** | Displayed in zoomable reader canvas and selectable as custom book covers via zero-permission Android Photo Picker. | — |

---

## Architecture & Core Features

- **5 Top-Level Tabs**: `Home`, `My Library`, `Bookmarks & Notes`, `Community`, `Settings`
- **Reader Enhancements**: Comfortable / Night / Study profiles, tap-zone presets (Standard, Left-Handed, Center, Off), paginated vs. continuous scroll modes, live **Focus Timer** with break reminders, **Offline TTS Read Aloud** with speed controls, and **Offline OCR** text copy & highlight on PDF/image pages.
- **Library Productivity & Safety**: Reading Status tracking (`Want to Read`, `Currently Reading`, `Finished`), Collection edit/delete, **Soft-Delete Trash** with restore or permanent purge, Reading Goals & Streak tracking, full-text library search + history, and Markdown (`.md`) / JSON annotation exports.
- **Backup & Restore (Inside Settings Only)**: Creates integrity-checked `.zip` v2 archives (`manifest.json` + SHA-256 checksums + `library_data.json` + reading sessions + covers/sound/files) supporting backward-compatible v1 restore, Metadata-Only / Selected Books / Full Library modes, path-traversal validation, and Duplicate Handling (`Skip`, `Keep Both`, `Update Matches`).
- **Offline Page-Turn Sound**: Optionally downloads `bookreader-page-flip.mp3`, verifies MP3 frame integrity, and plays offline via `SoundPool` on discrete page/chapter turns (with re-download/repair support).
- **Community & Moderation**:
  - Gated **strictly inside the Community tab** using Android `CredentialManager` (`GetSignInWithGoogleOption` / `GetGoogleIdOption`).
  - Supports text + emoji posts, post likes (`likeCount` backed by Firestore transactions), real-time post/author/book search, user blocking (`blocked_users` + Manage Blocked Users dialog), optional validated Google Drive links (`https://drive.google.com/...` or `https://docs.google.com/...`) with sharing disclaimer, Report Post & Report Profile, and bilingual Community Rules.
  - Protected official administrator account (`muhammedomor.dev@gmail.com`) receives the red admin badge (`#BD081C`) and accesses **Admin Moderation** (review/dismiss reports, hide/restore posts, grant/revoke blue verified badges `#26A5E4`, restrict/unrestrict accounts, view immutable audit logs).

---

## Firebase & Cloud Functions Setup

1. Firestore project & named database are configured in `app/google-services.json`, `app/src/main/res/values/firebase_applet_config.xml`, `firestore.rules`, and `firestore.indexes.json`.
2. Deploy composite indexes & security rules when updating backend configuration, and verify with the emulator test suite (`firestore.test.js`):
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes
   ```
3. Optional Cloud Functions (`/functions/index.js`) require the Firebase Blaze plan to deploy `assignOfficialAdminClaim` (`beforeUserSignedIn` custom claim `{ admin: true }` + `/admins/{uid}`) and `syncAuthorBadgeToPosts`:
   ```bash
   cd functions && npm install && firebase deploy --only functions
   ```
   *(Note: `firestore.rules` already recognizes `request.auth.token.email == 'muhammedomor.dev@gmail.com'` with `email_verified == true` as an additional safeguarded check alongside custom claims and `/admins/{uid}`.)*

---

## Building & Downloading the APK Directly From an Android Phone (GitHub Actions)

You can build and download an installable APK directly on your Android phone without Android Studio:

1. Push this repository to **GitHub**.
2. Open the repository on your Android phone's browser and tap the **Actions** tab.
3. Select **Build MyBooks APK** (`build-apk.yml`) — it runs automatically on every push, or tap **Run workflow** manually.
4. Wait for the green checkmark (unit & Robolectric tests + `assembleDebug`).
5. Scroll down to **Artifacts** on the workflow summary page and tap **MyBooks-Debug-APK** to download the `.zip` file.
6. Open your phone's **Files** app, extract `app-debug.apk` from the downloaded `.zip`, and tap it to install (allow *"Install unknown apps"* for your Files/Browser app if prompted).

### Optional Firebase / Google Sign-In in GitHub-Built APKs
GitHub Actions builds cleanly even when `app/google-services.json` is omitted (all offline library/reader/backup features work immediately). To also enable **online Community & Google Sign-In** in APKs built on GitHub Actions, add a Repository Secret (`Settings → Secrets and variables → Actions`):
- `GOOGLE_SERVICES_JSON`: paste the full JSON contents of `app/google-services.json`

### Signed Release APK Setup (`release-apk.yml`)
To sign release APKs via GitHub Actions without committing keystores to Git:
1. Encode your `.jks` keystore: `base64 -w 0 my-upload-key.jks`
2. Add three **GitHub Repository Secrets** (`Settings → Secrets and variables → Actions`):
   - `RELEASE_KEYSTORE_BASE64`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`
3. Trigger **Build Signed Release APK** from the **Actions** tab and download `MyBooks-Release-APK`.

---

## Privacy & Permissions

- **Zero Broad Storage Permissions**: Never requests `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE`. Uses Android **Storage Access Framework (SAF)** with persistable URI grants and Android **Photo Picker** for covers.
- **Installed Permissions**: Only `INTERNET` and `ACCESS_NETWORK_STATE` (used strictly for the optional Community tab and downloading the optional page-turn sound).
- **Support & Developer Links**:
  - Official Shop: [https://muhammedomor.supportkori.shop/](https://muhammedomor.supportkori.shop/)
  - Support Email: `muhammedomor.studio@gmail.com`

---

© 2026 Muhammed Omor. All rights reserved.
