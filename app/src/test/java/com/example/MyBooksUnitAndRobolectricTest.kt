package com.example

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.res.Configuration
import com.example.data.audio.PageTurnSoundManager
import com.example.data.audio.SoundDownloadState
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupMode
import com.example.data.backup.DuplicatePolicy
import com.example.data.local.MyBooksDatabase
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.resolvedReadingStatus
import com.example.data.preferences.PdfFitMode
import com.example.data.preferences.PdfScrollMode
import com.example.data.preferences.ReflowablePaginationMode
import com.example.data.preferences.TapZoneMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.reader.DocumentFormat
import com.example.data.reader.DocumentValidator
import com.example.data.reader.EpubParser
import com.example.data.reader.ExtendedDocumentParser
import com.example.data.reader.PdfTextExtractor
import com.example.data.reader.SafeHtmlTextParser
import com.example.data.reader.TextBlock
import com.example.data.reader.TtsReadAloudManager
import com.example.data.repository.LibraryRepository
import com.example.ui.theme.AppThemeMode
import com.example.ui.viewmodel.ReaderContentState
import com.example.ui.viewmodel.ReaderViewModel
import android.database.sqlite.SQLiteDatabase
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MyBooksUnitAndRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: MyBooksDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var libraryRepo: LibraryRepository
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MyBooksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = UserPreferencesRepository(context)
        libraryRepo = LibraryRepository(context, db.libraryDao(), db.annotationDao())
        tempDir = File(context.cacheDir, "unit_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `room stable book id survives title update and keeps personal notes when requested`() = runBlocking {
        val txtFile = File(tempDir, "sample_notes.txt").apply {
            writeText("Paragraph one.\n\nParagraph two.")
        }
        val importRes = libraryRepo.importDocument(Uri.fromFile(txtFile), takePersistablePermission = false)
        assertTrue(importRes is com.example.data.repository.ImportOutcome.Imported)
        val book = (importRes as com.example.data.repository.ImportOutcome.Imported).book

        libraryRepo.saveNote(
            bookId = book.id,
            bookTitleSnapshot = book.title,
            title = "Important Quote",
            content = "Keep this note even if book is removed"
        )
        libraryRepo.updateBookMetadata(
            bookId = book.id,
            title = "Renamed Title",
            author = "Muhammed Omor",
            description = "Desc",
            publisher = "Pub",
            language = "bn",
            categoryName = "Literature",
            tagNames = listOf("bangla", "classic"),
            collectionIds = emptyList()
        )

        val updated = libraryRepo.getBookWithDetails(book.id)
        assertNotNull(updated)
        assertEquals(book.id, updated!!.book.id)
        assertEquals("Renamed Title", updated.book.title)
        assertEquals("Literature", updated.category?.name)
        assertEquals(2, updated.tags.size)

        // Remove book with deleteNotes = false -> note survives with bookId = null
        libraryRepo.removeBookFromLibrary(book.id, deleteNotes = false)
        assertNull(libraryRepo.getBookById(book.id))
        val notes = db.annotationDao().getAllNotesSnapshot()
        assertEquals(1, notes.size)
        assertNull(notes[0].bookId)
        assertEquals("Keep this note even if book is removed", notes[0].content)
    }

    @Test
    fun `document validator recognizes valid formats and rejects corrupt or generic zip files`() {
        val pdfFile = File(tempDir, "valid.pdf").apply { writeText("%PDF-1.7\n1 0 obj\n<<>>\nendobj") }
        val pdfCheck = DocumentValidator.validateAndInspect(context.contentResolver, Uri.fromFile(pdfFile), "valid.pdf", "application/pdf")
        assertTrue(pdfCheck.isSuccess)
        assertEquals(DocumentFormat.PDF, pdfCheck.getOrThrow().format)

        val emptyFile = File(tempDir, "empty.pdf").apply { writeBytes(byteArrayOf()) }
        val emptyCheck = DocumentValidator.validateAndInspect(context.contentResolver, Uri.fromFile(emptyFile), "empty.pdf", "application/pdf")
        assertTrue(emptyCheck.isFailure)

        val genericZip = File(tempDir, "archive.zip").apply {
            ZipOutputStream(FileOutputStream(this)).use { zos ->
                zos.putNextEntry(ZipEntry("random.dat"))
                zos.write("hello".toByteArray())
                zos.closeEntry()
            }
        }
        val zipCheck = DocumentValidator.validateAndInspect(context.contentResolver, Uri.fromFile(genericZip), "archive.zip", "application/zip")
        assertTrue(zipCheck.isFailure)
    }

    @Test
    fun `safe html parser strips scripts and markdown parser structures headings`() {
        val blocks = SafeHtmlTextParser.parseToBlocks(
            "<html><head><script>alert(1)</script></head><body><h1>Chapter One</h1><p>Hello &amp; Welcome</p></body></html>"
        )
        assertTrue(blocks.none { it.toString().contains("alert") })
        assertTrue(blocks.any { it is TextBlock.Heading && it.text == "Chapter One" })
        assertTrue(blocks.any { it is TextBlock.Paragraph && it.text == "Hello & Welcome" })

        val mdFile = File(tempDir, "guide.md").apply {
            writeText("# Intro\n\n> Quoted wisdom\n\n- Bullet item\n\nNormal paragraph.")
        }
        val mdParsed = ExtendedDocumentParser.parseTextDocument(
            context.contentResolver, Uri.fromFile(mdFile), DocumentFormat.MD, "Guide"
        )
        assertTrue(mdParsed.isSuccess)
        val allBlocks = mdParsed.getOrThrow().sections.flatMap { it.blocks }
        assertTrue(allBlocks.any { it is TextBlock.Heading && it.text == "Intro" })
        assertTrue(allBlocks.any { it is TextBlock.Quote && it.text == "Quoted wisdom" })
        assertTrue(allBlocks.any { it is TextBlock.BulletItem && it.text == "Bullet item" })
    }

    @Test
    fun `epub parser reads minimal valid epub archive`() {
        val epubFile = File(tempDir, "mini.epub")
        ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Sonar Bangla</dc:title><dc:creator>Tagore</dc:creator></metadata><manifest><item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="ch1"/></spine></package>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write("<html><body><h1>First Chapter</h1><p>Amar Sonar Bangla.</p></body></html>".toByteArray())
            zos.closeEntry()
        }

        val parsed = EpubParser.parse(context.contentResolver, Uri.fromFile(epubFile))
        assertTrue(parsed.isSuccess)
        val content = parsed.getOrThrow()
        assertEquals("Sonar Bangla", content.title)
        assertEquals("Tagore", content.author)
        assertEquals(1, content.chapters.size)
    }

    @Test
    fun `backup manager creates valid zip, rejects path traversal, and restores safely`() = runBlocking {
        val bookFile = File(tempDir, "novel.txt").apply { writeText("Chapter 1\n\nOnce upon a time.") }
        val book = BookEntity(
            id = "book-1",
            title = "My Novel",
            author = "Author A",
            originalFilename = "novel.txt",
            formatName = DocumentFormat.TXT.name,
            fileUri = Uri.fromFile(bookFile).toString(),
            fileSizeBytes = bookFile.length(),
            contentHash = "hash-1"
        )
        db.libraryDao().insertBook(book)
        db.annotationDao().upsertNote(
            NoteEntity(
                id = "note-1",
                bookId = book.id,
                bookTitleSnapshot = book.title,
                title = "Opening",
                content = "Great opening paragraph"
            )
        )

        val soundManager = PageTurnSoundManager(context, prefsRepo)
        val backupManager = BackupManager(context, db, prefsRepo) { soundManager.soundFile() }

        val zipDest = File(tempDir, "mybooks_backup.zip")
        val createRes = backupManager.createBackup(
            destinationUri = Uri.fromFile(zipDest),
            mode = BackupMode.FULL_LIBRARY,
            selectedBookIds = emptySet(),
            preferences = prefsRepo.preferencesFlow.first()
        )
        assertTrue(createRes.isSuccess)
        assertTrue(zipDest.exists() && zipDest.length() > 0L)

        // Malicious zip traversal archive must be rejected without mutating DB
        val evilZip = File(tempDir, "evil.zip")
        ZipOutputStream(FileOutputStream(evilZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("evil".toByteArray())
            zos.closeEntry()
        }
        val evilInspect = backupManager.inspectAndValidateBackup(Uri.fromFile(evilZip))
        assertTrue(evilInspect.isFailure)

        // Clear DB and restore valid archive
        db.libraryDao().deleteBookById("book-1")
        db.annotationDao().deleteNote("note-1")
        assertEquals(0, db.libraryDao().getAllBooksSnapshot().size)

        val inspected = backupManager.inspectAndValidateBackup(Uri.fromFile(zipDest)).getOrThrow()
        val restoreRes = backupManager.applyRestore(inspected, DuplicatePolicy.SKIP_DUPLICATES)
        assertTrue(restoreRes.isSuccess)
        assertEquals(1, db.libraryDao().getAllBooksSnapshot().size)
        assertEquals(1, db.annotationDao().getAllNotesSnapshot().size)
    }

    @Test
    fun `page turn sound manager rejects invalid non-mp3 files and accepts valid mp3 frames`() = runBlocking {
        val soundManager = PageTurnSoundManager(context, prefsRepo)
        val file = soundManager.soundFile()

        // Write invalid plain text -> fails validation and resets state
        file.writeText("not an mp3 file at all")
        assertFalse(soundManager.verifyLocalSoundIntegrity())
        assertFalse(file.exists())
        assertEquals(SoundDownloadState.NotDownloaded, soundManager.downloadState.value)

        // Write valid ID3 header + >1024 bytes -> passes magic byte check
        val mp3Bytes = ByteArray(2048).apply {
            this[0] = 'I'.code.toByte()
            this[1] = 'D'.code.toByte()
            this[2] = '3'.code.toByte()
            this[3] = 3
        }
        file.writeBytes(mp3Bytes)
        assertTrue(soundManager.isValidMp3File(file))
        assertTrue(soundManager.verifyLocalSoundIntegrity())
        assertEquals(SoundDownloadState.Ready, soundManager.downloadState.value)
        soundManager.release()
    }

    @Test
    fun `preferences persist language and theme modes cleanly`() = runBlocking {
        val freshStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir, "prefs_toggle_test.preferences_pb") }
        )
        val isolatedPrefs = UserPreferencesRepository(freshStore)
        val initial = isolatedPrefs.preferencesFlow.first()
        assertEquals("en", initial.appLanguage)

        isolatedPrefs.setLanguage("bn")
        isolatedPrefs.setAppThemeMode(AppThemeMode.AMOLED)
        isolatedPrefs.setKeepScreenOn(true)

        val updated = isolatedPrefs.preferencesFlow.first()
        assertEquals("bn", updated.appLanguage)
        assertEquals(AppThemeMode.AMOLED, updated.appThemeMode)
        assertTrue(updated.readerKeepScreenOn)
    }

    @Test
    fun `bilingual privacy policy, terms and telegram community strings load cleanly in en and bn`() {
        val enConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val enCtx = context.createConfigurationContext(enConfig)
        assertEquals("Privacy Policy", enCtx.getString(R.string.privacy_policy_title))
        assertEquals("Terms & Conditions", enCtx.getString(R.string.terms_title))
        assertTrue(enCtx.getString(R.string.privacy_s4_body).contains("https://t.me/+3Y-98ATTpaRhNjll"))

        val bnConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("bn"))
        }
        val bnCtx = context.createConfigurationContext(bnConfig)
        assertTrue(bnCtx.getString(R.string.privacy_policy_title).contains("প্রাইভেসি"))
        assertTrue(bnCtx.getString(R.string.terms_title).contains("টার্মস"))
        assertTrue(bnCtx.getString(R.string.privacy_s4_body).contains("https://t.me/+3Y-98ATTpaRhNjll"))
    }

    @Test
    fun `main activity launches compose root without missing ActivityResultRegistryOwner`() {
        org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .get()
            .let { activity ->
                assertNotNull(activity.window)
            }
    }

    @Test
    fun `category CRUD renames safely rejects duplicates and keeps books when deleted`() = runBlocking {
        val txtFile = File(tempDir, "cat_book.txt").apply { writeText("Hello World") }
        val imported = (libraryRepo.importDocument(Uri.fromFile(txtFile), takePersistablePermission = false)
            as com.example.data.repository.ImportOutcome.Imported).book

        val fiction = libraryRepo.createCategory("Fiction")
        assertNotNull(fiction)
        val duplicate = libraryRepo.createCategory("fiction")
        assertEquals(fiction!!.id, duplicate?.id)

        val sciFi = libraryRepo.createCategory("Sci-Fi")
        assertNotNull(sciFi)
        assertFalse(libraryRepo.renameCategory(sciFi!!.id, "Fiction"))
        assertTrue(libraryRepo.renameCategory(sciFi.id, "Speculative"))

        libraryRepo.updateBookMetadata(
            bookId = imported.id,
            title = imported.title,
            author = "Author",
            description = "",
            publisher = "",
            language = "en",
            categoryName = "Speculative",
            tagNames = emptyList(),
            collectionIds = emptyList()
        )
        assertEquals(sciFi.id, libraryRepo.getBookById(imported.id)?.categoryId)

        libraryRepo.deleteCategory(sciFi.id)
        val bookAfterDelete = libraryRepo.getBookById(imported.id)
        assertNotNull(bookAfterDelete)
        assertNull(bookAfterDelete!!.categoryId)
    }

    @Test
    fun `cover replacement rejects invalid images cleanly and restore default clears cover without changing book id`() = runBlocking {
        val txtFile = File(tempDir, "cover_book.txt").apply { writeText("Book body") }
        val imported = (libraryRepo.importDocument(Uri.fromFile(txtFile), takePersistablePermission = false)
            as com.example.data.repository.ImportOutcome.Imported).book

        val badCover = File(tempDir, "not_an_image.txt").apply { writeText("corrupt image bytes") }
        assertFalse(libraryRepo.setBookCover(imported.id, Uri.fromFile(badCover)))
        assertEquals(imported.id, libraryRepo.getBookById(imported.id)?.id)

        val validPngBytes = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            android.util.Base64.DEFAULT
        )
        val goodCover = File(tempDir, "cover.png").apply { writeBytes(validPngBytes) }

        assertTrue(libraryRepo.setBookCover(imported.id, Uri.fromFile(goodCover)))
        val withCover = libraryRepo.getBookById(imported.id)!!
        assertEquals(imported.id, withCover.id)
        assertNotNull(withCover.coverUri)
        assertTrue(File(withCover.coverUri!!).exists())

        libraryRepo.restoreDefaultCover(imported.id)
        val restored = libraryRepo.getBookById(imported.id)!!
        assertEquals(imported.id, restored.id)
        assertNull(restored.coverUri)
    }

    @Test
    fun `epub parser loads all chapters even when large images exceed image budget`() {
        val epubFile = File(tempDir, "illustrated_large.epub")
        ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray()
            )
            zos.closeEntry()

            // Several large uncompressed image entries totaling > 25MB (exceeding old 24MB shared cap)
            zos.setLevel(0)
            val chunk = ByteArray(9 * 1024 * 1024) { 0x42 }
            for (i in 1..3) {
                zos.putNextEntry(ZipEntry("OEBPS/images/big_$i.png"))
                zos.write(chunk)
                zos.closeEntry()
            }
            zos.setLevel(6)

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Big Atlas</dc:title></metadata><manifest><item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="ch1"/><itemref idref="ch2"/></spine></package>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write("<html><body><h1>Chapter 1</h1><p>Start.</p></body></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/ch2.xhtml"))
            zos.write("<html><body><h1>Chapter 2</h1><p>Ending chapter preserved.</p></body></html>".toByteArray())
            zos.closeEntry()
        }

        val parsed = EpubParser.parse(context.contentResolver, Uri.fromFile(epubFile))
        assertTrue(parsed.isSuccess)
        val epub = parsed.getOrThrow()
        assertEquals(2, epub.chapters.size)
        assertTrue(epub.chapters[1].paragraphs.any { it.toString().contains("Ending chapter preserved.") })
    }

    private suspend fun awaitMainCondition(timeoutMs: Long = 5000L, condition: suspend () -> Boolean) {
        val mainShadow = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            mainShadow.idle()
            delay(25)
        }
        mainShadow.idle()
    }

    @Test
    fun `external document opens without forced import and saves to library on request preserving position`() = runBlocking {
        val extFile = File(tempDir, "external_book.epub")
        ZipOutputStream(FileOutputStream(extFile)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray()
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>External Book</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""".toByteArray()
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/c1.xhtml"))
            zos.write("<html><body><h1>Ch 1</h1><p>P1</p><p>P2</p></body></html>".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/c2.xhtml"))
            zos.write("<html><body><h1>Ch 2</h1><p>PA</p><p>PB</p><p>PC</p></body></html>".toByteArray())
            zos.closeEntry()
        }
        val soundManager = PageTurnSoundManager(context, prefsRepo)
        val ttsManager = TtsReadAloudManager(context)
        val vm = ReaderViewModel(
            appContext = context,
            bookId = "",
            externalUri = Uri.fromFile(extFile).toString(),
            initialPageOverride = null,
            initialChapterOverride = null,
            libraryRepository = libraryRepo,
            preferencesRepository = prefsRepo,
            soundManager = soundManager,
            ttsManager = ttsManager
        )

        awaitMainCondition { vm.contentState.value !is ReaderContentState.Loading }
        assertTrue(vm.contentState.value is ReaderContentState.EpubReady)
        assertTrue(vm.isExternalUnsaved.value)
        assertEquals(0, db.libraryDao().getAllBooksSnapshot().size)

        vm.goToEpubChapter(1, playSound = false)
        vm.updateReflowableScrollOffset(2)
        vm.saveExternalBookToLibrary(
            title = "Saved External Essay",
            author = "Guest Writer",
            categoryName = "Essays"
        )

        awaitMainCondition { !vm.isExternalUnsaved.value }
        assertFalse(vm.isExternalUnsaved.value)
        val books = db.libraryDao().getAllBooksSnapshot()
        assertEquals(1, books.size)
        assertEquals("Saved External Essay", books[0].title)
        val progress = libraryRepo.getProgressForBook(books[0].id)
        assertNotNull(progress)
        assertEquals(1, progress!!.chapterIndex)
        assertEquals(2, progress.scrollOffset)
        soundManager.release()
        ttsManager.release()
    }

    @Test
    fun `reflowable scroll offset flushes cleanly and restores on reopen`() = runBlocking {
        val bookFile = File(tempDir, "long_text.txt").apply {
            writeText((1..30).joinToString("\n\n") { "Paragraph number $it with readable words." })
        }
        val imported = (libraryRepo.importDocument(Uri.fromFile(bookFile), takePersistablePermission = false)
            as com.example.data.repository.ImportOutcome.Imported).book
        val soundManager = PageTurnSoundManager(context, prefsRepo)
        val ttsManager = TtsReadAloudManager(context)

        val vm1 = ReaderViewModel(
            appContext = context,
            bookId = imported.id,
            externalUri = null,
            initialPageOverride = null,
            initialChapterOverride = null,
            libraryRepository = libraryRepo,
            preferencesRepository = prefsRepo,
            soundManager = soundManager,
            ttsManager = ttsManager
        )
        awaitMainCondition { vm1.contentState.value !is ReaderContentState.Loading }
        assertTrue(vm1.contentState.value is ReaderContentState.TextDocumentReady)

        vm1.updateReflowableScrollOffset(9)
        vm1.flushCurrentPosition()
        awaitMainCondition { libraryRepo.getProgressForBook(imported.id)?.scrollOffset == 9 }

        val saved = libraryRepo.getProgressForBook(imported.id)
        assertNotNull(saved)
        assertEquals(9, saved!!.scrollOffset)

        val vm2 = ReaderViewModel(
            appContext = context,
            bookId = imported.id,
            externalUri = null,
            initialPageOverride = null,
            initialChapterOverride = null,
            libraryRepository = libraryRepo,
            preferencesRepository = prefsRepo,
            soundManager = soundManager,
            ttsManager = ttsManager
        )
        awaitMainCondition { vm2.contentState.value !is ReaderContentState.Loading }
        val reopened = vm2.contentState.value as ReaderContentState.TextDocumentReady
        assertEquals(9, reopened.initialScrollOffset)
        soundManager.release()
        ttsManager.release()
    }

    @Test
    fun `pdf fit mode, scroll mode, reflowable pagination, and tap zone preferences persist and reset cleanly`() = runBlocking {
        assertEquals(PdfFitMode.FIT_PAGE, prefsRepo.preferencesFlow.first().readerPdfFitMode)
        assertEquals(PdfScrollMode.SINGLE_PAGE, prefsRepo.preferencesFlow.first().readerPdfScrollMode)
        assertEquals(ReflowablePaginationMode.PAGINATED, prefsRepo.preferencesFlow.first().readerReflowableMode)
        assertEquals(TapZoneMode.STANDARD, prefsRepo.preferencesFlow.first().readerTapZoneMode)

        prefsRepo.setReaderPdfFitMode(PdfFitMode.FIT_WIDTH)
        prefsRepo.setReaderPdfScrollMode(PdfScrollMode.CONTINUOUS_VERTICAL)
        prefsRepo.setReaderReflowableMode(ReflowablePaginationMode.CONTINUOUS_SCROLL)
        prefsRepo.setTapZoneMode(TapZoneMode.LEFT_HANDED)

        val updated = prefsRepo.preferencesFlow.first()
        assertEquals(PdfFitMode.FIT_WIDTH, updated.readerPdfFitMode)
        assertEquals(PdfScrollMode.CONTINUOUS_VERTICAL, updated.readerPdfScrollMode)
        assertEquals(ReflowablePaginationMode.CONTINUOUS_SCROLL, updated.readerReflowableMode)
        assertEquals(TapZoneMode.LEFT_HANDED, updated.readerTapZoneMode)

        prefsRepo.resetReadingPreferences()
        val reset = prefsRepo.preferencesFlow.first()
        assertEquals(PdfFitMode.FIT_PAGE, reset.readerPdfFitMode)
        assertEquals(PdfScrollMode.SINGLE_PAGE, reset.readerPdfScrollMode)
        assertEquals(ReflowablePaginationMode.PAGINATED, reset.readerReflowableMode)
        assertEquals(TapZoneMode.STANDARD, reset.readerTapZoneMode)
    }

    @Test
    fun `room MIGRATION_1_2 preserves existing v1 books and notes cleanly without data loss`() = runBlocking {
        val dbFile = File(tempDir, "legacy_v1_library.db")
        val legacyDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        legacyDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `description` TEXT NOT NULL, `publisher` TEXT NOT NULL, `language` TEXT NOT NULL, `categoryId` TEXT, `dateAddedEpochMs` INTEGER NOT NULL, `coverUri` TEXT, `originalFilename` TEXT NOT NULL, `formatName` TEXT NOT NULL, `fileUri` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `contentHash` TEXT NOT NULL, `readingProgress` REAL NOT NULL, `lastOpenedEpochMs` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `isFileMissing` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_books_categoryId` ON `books` (`categoryId`)")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_books_contentHash` ON `books` (`contentHash`)")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_books_lastOpenedEpochMs` ON `books` (`lastOpenedEpochMs`)")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_books_dateAddedEpochMs` ON `books` (`dateAddedEpochMs`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
        legacyDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        legacyDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_name` ON `collections` (`name`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `book_tag_cross_ref` (`bookId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`bookId`, `tagId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tag_cross_ref_tagId` ON `book_tag_cross_ref` (`tagId`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `book_collection_cross_ref` (`bookId` TEXT NOT NULL, `collectionId` TEXT NOT NULL, PRIMARY KEY(`bookId`, `collectionId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_book_collection_cross_ref_collectionId` ON `book_collection_cross_ref` (`collectionId`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `reading_progress` (`bookId` TEXT NOT NULL, `progressFraction` REAL NOT NULL, `pageIndex` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, `chapterIndex` INTEGER NOT NULL, `totalChapters` INTEGER NOT NULL, `scrollOffset` INTEGER NOT NULL, `locatorJson` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`bookId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `title` TEXT NOT NULL, `locatorJson` TEXT NOT NULL, `locatorDisplay` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `chapterIndex` INTEGER NOT NULL, `scrollOffset` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_createdAtEpochMs` ON `bookmarks` (`createdAtEpochMs`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `highlights` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `bookTitleSnapshot` TEXT NOT NULL, `selectedText` TEXT NOT NULL, `locatorJson` TEXT NOT NULL, `locatorDisplay` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `chapterIndex` INTEGER NOT NULL, `colorHex` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_bookId` ON `highlights` (`bookId`)")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_createdAtEpochMs` ON `highlights` (`createdAtEpochMs`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `bookId` TEXT, `bookTitleSnapshot` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `locatorJson` TEXT NOT NULL, `locatorDisplay` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `chapterIndex` INTEGER NOT NULL, `scrollOffset` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_bookId` ON `notes` (`bookId`)")
        legacyDb.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAtEpochMs` ON `notes` (`updatedAtEpochMs`)")
        legacyDb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        legacyDb.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, 'cbd993f1b2f342c315fdbb5b1dbbe117')")

        legacyDb.execSQL("INSERT INTO categories VALUES('cat-1', 'Bangla Classics', 0)")
        legacyDb.execSQL("INSERT INTO books VALUES('b-want', 'Want Book', 'A', '', '', 'bn', 'cat-1', 1000, NULL, 'want.txt', 'TXT', 'file:///want.txt', 100, 'h1', 0.0, 0, 0, 0, 1000)")
        legacyDb.execSQL("INSERT INTO books VALUES('b-read', 'Reading Book', 'B', '', '', 'bn', 'cat-1', 2000, NULL, 'read.txt', 'TXT', 'file:///read.txt', 200, 'h2', 0.45, 2500, 1, 0, 2500)")
        legacyDb.execSQL("INSERT INTO books VALUES('b-done', 'Done Book', 'C', '', '', 'bn', 'cat-1', 3000, NULL, 'done.txt', 'TXT', 'file:///done.txt', 300, 'h3', 1.0, 3500, 0, 0, 3500)")
        legacyDb.execSQL("INSERT INTO notes VALUES('n-1', 'b-read', 'Reading Book', 'Preserved Note', 'My v1 note body', '', 'Page 5', 4, 0, 0, 2600, 2600)")
        legacyDb.version = 1
        legacyDb.close()

        val migratedDb = Room.databaseBuilder(context, MyBooksDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MyBooksDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val books = migratedDb.libraryDao().getActiveBooksSnapshot().associateBy { it.id }
            assertEquals(3, books.size)
            assertEquals(ReadingStatus.WANT_TO_READ.name, books["b-want"]?.readingStatusName)
            assertEquals(ReadingStatus.CURRENTLY_READING.name, books["b-read"]?.readingStatusName)
            assertEquals(ReadingStatus.FINISHED.name, books["b-done"]?.readingStatusName)
            assertFalse(books["b-read"]!!.isDeleted)

            val notes = migratedDb.annotationDao().getAllNotesSnapshot()
            assertEquals(1, notes.size)
            assertEquals("My v1 note body", notes[0].content)

            migratedDb.libraryDao().insertReadingSession(
                ReadingSessionEntity(
                    id = "s-1",
                    bookId = "b-read",
                    bookTitleSnapshot = "Reading Book",
                    epochDay = LibraryRepository.localEpochDay(),
                    durationSeconds = 600,
                    pagesRead = 10,
                    startedAtEpochMs = 1000L,
                    endedAtEpochMs = 2000L,
                    isFocusTimer = true
                )
            )
            assertEquals(1, migratedDb.libraryDao().getAllReadingSessionsSnapshot().size)
        } finally {
            migratedDb.close()
        }
    }

    @Test
    fun `library supports reading status, soft delete trash restore, collection editing, and streak calculation`() = runBlocking {
        val bookFile = File(tempDir, "status_book.txt").apply { writeText("Paragraphs for reading tests.") }
        val imported = (libraryRepo.importDocument(Uri.fromFile(bookFile), takePersistablePermission = false)
            as com.example.data.repository.ImportOutcome.Imported).book

        libraryRepo.setReadingStatus(imported.id, ReadingStatus.FINISHED)
        assertEquals(ReadingStatus.FINISHED, libraryRepo.getBookById(imported.id)?.resolvedReadingStatus())

        val col = libraryRepo.createCollection("Favorites Shelf", "Initial desc")
        assertTrue(libraryRepo.updateCollection(col.id, "Must Reads", "Updated desc"))
        val updatedCol = libraryRepo.collections.first().first { it.id == col.id }
        assertEquals("Must Reads", updatedCol.name)
        assertEquals("Updated desc", updatedCol.description)
        libraryRepo.deleteCollection(col.id)
        assertTrue(libraryRepo.collections.first().none { it.id == col.id })

        libraryRepo.softDeleteBook(imported.id)
        assertTrue(libraryRepo.allBooksWithDetails.first().none { it.book.id == imported.id })
        assertTrue(libraryRepo.deletedBooksWithDetails.first().any { it.book.id == imported.id })

        libraryRepo.restoreBookFromTrash(imported.id)
        assertTrue(libraryRepo.allBooksWithDetails.first().any { it.book.id == imported.id })
        assertTrue(libraryRepo.deletedBooksWithDetails.first().none { it.book.id == imported.id })

        val now = 1_780_000_000_000L
        val today = LibraryRepository.localEpochDay(now)
        val sessions = listOf(
            ReadingSessionEntity("s1", imported.id, imported.title, today - 2, 900, 12, now - 172_800_000L, now - 172_000_000L, false),
            ReadingSessionEntity("s2", imported.id, imported.title, today - 1, 1200, 15, now - 86_400_000L, now - 85_200_000L, true),
            ReadingSessionEntity("s3", imported.id, imported.title, today, 600, 8, now - 600_000L, now, false)
        )
        val streak = LibraryRepository.calculateStreakSummary(sessions, dailyGoalMinutes = 15, nowEpochMs = now)
        assertEquals(10, streak.todayMinutes)
        assertEquals(45, streak.totalMinutes)
        assertEquals(3, streak.currentStreakDays)
        assertEquals(3, streak.bestStreakDays)
    }

    @Test
    fun `pdf text extractor extracts text from embedded content stream`() {
        val minimalPdf = """
            %PDF-1.4
            1 0 obj
            << /Length 49 >>
            stream
            BT /F1 12 Tf 72 720 Td (Hello MyBooks Reader!) Tj ET
            endstream
            endobj
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val extracted = PdfTextExtractor.extractTextFromBytes(minimalPdf)
        assertTrue("Expected extracted PDF text, got: $extracted", extracted.contains("Hello MyBooks Reader!"))
    }

    @Test
    fun `backup manager restores v1 backup json payload without v2 columns cleanly`() = runBlocking {
        val v1LibraryJson = """
            {
              "schemaVersion": 1,
              "books": [
                {
                  "id": "v1-book-1",
                  "title": "Legacy Book",
                  "author": "Omor",
                  "originalFilename": "legacy.txt",
                  "formatName": "TXT",
                  "fileUri": "file:///tmp/legacy.txt",
                  "fileSizeBytes": 120,
                  "contentHash": "hash-legacy-v1",
                  "readingProgress": 0.55
                }
              ],
              "preferences": {
                "appLanguage": "bn",
                "readerFontSizeSp": 20.0
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val digest = MessageDigest.getInstance("SHA-256").digest(v1LibraryJson)
            .joinToString("") { "%02x".format(it) }
        val v1ManifestJson = """
            {
              "schemaVersion": 1,
              "appId": "com.mybooks.omor.dev",
              "createdAtEpochMs": 1700000000000,
              "mode": "METADATA_ONLY",
              "bookCount": 1,
              "bookmarkCount": 0,
              "noteCount": 0,
              "highlightCount": 0,
              "sha256Checksums": {
                "library_data.json": "$digest"
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val v1Zip = File(tempDir, "legacy_v1_backup.zip")
        ZipOutputStream(FileOutputStream(v1Zip)).use { zos ->
            zos.putNextEntry(ZipEntry("library_data.json"))
            zos.write(v1LibraryJson)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(v1ManifestJson)
            zos.closeEntry()
        }

        val soundManager = PageTurnSoundManager(context, prefsRepo)
        val backupManager = BackupManager(context, db, prefsRepo) { soundManager.soundFile() }

        val inspected = backupManager.inspectAndValidateBackup(Uri.fromFile(v1Zip))
        assertTrue(inspected.isSuccess)
        val restore = backupManager.applyRestore(inspected.getOrThrow(), DuplicatePolicy.OVERWRITE_MATCHES)
        assertTrue(restore.isSuccess)

        val restoredBook = libraryRepo.getBookById("v1-book-1")
        assertNotNull(restoredBook)
        assertEquals(ReadingStatus.CURRENTLY_READING, restoredBook!!.resolvedReadingStatus())
        assertFalse(restoredBook.isDeleted)
        soundManager.release()
    }

    @Test
    fun `app enforces exclusions and keeps default language en`() = runBlocking {
        val freshStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir, "fresh_default_prefs.preferences_pb") }
        )
        val freshRepo = UserPreferencesRepository(freshStore)
        assertEquals("en", freshRepo.preferencesFlow.first().appLanguage)
        assertFalse(context.resources.getString(R.string.app_name).contains("Lock", ignoreCase = true))
        val pinResId = context.resources.getIdentifier("pin_lock", "string", context.packageName)
        val bioResId = context.resources.getIdentifier("biometric_lock", "string", context.packageName)
        assertEquals(0, pinResId)
        assertEquals(0, bioResId)
    }
}
