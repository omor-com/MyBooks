package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.audio.PageTurnSoundManager
import com.example.data.audio.SoundDownloadState
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupMode
import com.example.data.backup.DuplicatePolicy
import com.example.data.community.BadgeType
import com.example.data.community.CommunityConstants
import com.example.data.community.CommunityLinkValidator
import com.example.data.community.CommunityPost
import com.example.data.local.MyBooksDatabase
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.reader.DocumentFormat
import com.example.data.reader.DocumentValidator
import com.example.data.reader.EpubParser
import com.example.data.reader.ExtendedDocumentParser
import com.example.data.reader.SafeHtmlTextParser
import com.example.data.reader.TextBlock
import com.example.data.repository.LibraryRepository
import com.example.ui.theme.AppThemeMode
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
        val initial = prefsRepo.preferencesFlow.first()
        assertEquals("bn", initial.appLanguage)

        prefsRepo.setLanguage("en")
        prefsRepo.setAppThemeMode(AppThemeMode.AMOLED)
        prefsRepo.setKeepScreenOn(true)

        val updated = prefsRepo.preferencesFlow.first()
        assertEquals("en", updated.appLanguage)
        assertEquals(AppThemeMode.AMOLED, updated.appThemeMode)
        assertTrue(updated.readerKeepScreenOn)
    }

    @Test
    fun `community link validator and badge models enforce constraints`() {
        assertTrue(CommunityLinkValidator.isValidOptionalDriveLink(""))
        assertTrue(CommunityLinkValidator.isValidOptionalDriveLink("https://drive.google.com/file/d/12345/view"))
        assertTrue(CommunityLinkValidator.isValidOptionalDriveLink("https://docs.google.com/document/d/abc/edit"))
        assertFalse(CommunityLinkValidator.isValidOptionalDriveLink("http://drive.google.com/file/d/12345"))
        assertFalse(CommunityLinkValidator.isValidOptionalDriveLink("https://evil.example.com/payload.exe"))

        val post = CommunityPost(authorBadge = BadgeType.RED_ADMIN.firestoreValue)
        assertEquals(BadgeType.RED_ADMIN, post.badge)
        assertEquals("muhammedomor.dev@gmail.com", CommunityConstants.OFFICIAL_ADMIN_EMAIL)
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
}
