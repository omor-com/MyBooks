# MyBooks

**MyBooks** is an offline-first personal digital library and multi-format document reader for Android (Kotlin + Jetpack Compose + Material 3), developed by **Muhammed Omor**, featuring full **English** & **বাংলা (Bangla)** bilingual support, complete in-app Privacy Policy & Terms & Conditions, and a direct **Telegram Community** link inside Settings.

- **Application ID**: `com.mybooks.omor.dev`
- **Default Language**: English (`en`), with instant 1-tap switching between **English** and **বাংলা (`bn`)** from the Home header or Settings
- **Offline Guarantee**: Importing, organizing, reading, bookmarking, highlighting, note-taking, preferences, and local ZIP Backup & Restore work 100% offline with zero login or Firebase required.

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

- **4 Top-Level Tabs**: `Home`, `My Library`, `Bookmarks & Notes`, `Settings`
- **Instant Bilingual Experience**: English (`en`) is the default language, with a quick `বাংলা / English` toggle directly in the Home header and prominent language buttons at the top of Settings.
- **Reader Enhancements**: Comfortable / Night / Study profiles, tap-zone presets (Standard, Left-Handed, Center, Off), paginated vs. continuous scroll modes, live **Focus Timer** with break reminders, **Offline TTS Read Aloud** with speed controls, and **Offline OCR** text copy & highlight on PDF/image pages.
- **Library Productivity & Safety**: Reading Status tracking (`Want to Read`, `Currently Reading`, `Finished`), Collection edit/delete, **Soft-Delete Trash** with restore or permanent purge, Reading Goals & Streak tracking, full-text library search + history, and Markdown (`.md`) / JSON annotation exports.
- **Backup & Restore (Inside Settings Only)**: Creates integrity-checked `.zip` v2 archives (`manifest.json` + SHA-256 checksums + `library_data.json` + reading sessions + covers/sound/files) supporting backward-compatible v1 restore, Metadata-Only / Selected Books / Full Library modes, path-traversal validation, and Duplicate Handling (`Skip`, `Keep Both`, `Update Matches`).
- **Offline Page-Turn Sound**: Optionally downloads `bookreader-page-flip.mp3`, verifies MP3 frame integrity, and plays offline via `SoundPool` on discrete page/chapter turns (with re-download/repair support).
- **Telegram Community & Legal Docs (Inside Settings)**:
  - **Community Button**: Launches `https://t.me/MyBooksCommunity` cleanly in the installed Telegram app or falls back to the default web browser.
  - **Privacy Policy & Terms & Conditions**: Complete multi-section legal screens available directly inside Settings in both **English** and **বাংলা**.

---

## Building & Downloading the APK Directly From an Android Phone (GitHub Actions)

You can build and download an installable APK directly on your Android phone without Android Studio:

1. Push this repository to **GitHub**.
2. Open the repository on your Android phone's browser and tap the **Actions** tab.
3. Select **Build MyBooks APK** (`build-apk.yml`) — it runs automatically on every push, or tap **Run workflow** manually.
4. Wait for the green checkmark (unit & Robolectric tests + `assembleDebug`).
5. Scroll down to **Artifacts** on the workflow summary page and tap **MyBooks-Debug-APK** to download the `.zip` file.
6. Open your phone's **Files** app, extract `app-debug.apk` from the downloaded `.zip`, and tap it to install (allow *"Install unknown apps"* for your Files/Browser app if prompted).

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
- **Minimal Permissions**: `INTERNET` & `ACCESS_NETWORK_STATE` are used only when manually downloading the optional page-turn sound MP3 or opening external links (Telegram Community, Developer Shop, Support Email).
- **Community & Support Links**:
  - Telegram Community: `https://t.me/MyBooksCommunity`
  - Official Shop: [https://muhammedomor.supportkori.shop/](https://muhammedomor.supportkori.shop/)
  - Support Email: `muhammedomor.studio@gmail.com`

---

© 2026 Muhammed Omor. All rights reserved.
