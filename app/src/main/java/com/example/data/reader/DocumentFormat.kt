package com.example.data.reader

enum class DocumentTier {
    CORE, EXTENDED, SPECIALIZED, IMAGE
}

enum class ReaderCategory {
    PDF, EPUB, REFLOWABLE_TEXT, IMAGE_COLLECTION
}

enum class DocumentFormat(
    val displayLabel: String,
    val tier: DocumentTier,
    val readerCategory: ReaderCategory,
    val extensions: Set<String>,
    val mimeTypes: Set<String>
) {
    PDF(
        displayLabel = "PDF",
        tier = DocumentTier.CORE,
        readerCategory = ReaderCategory.PDF,
        extensions = setOf("pdf"),
        mimeTypes = setOf("application/pdf")
    ),
    EPUB(
        displayLabel = "EPUB",
        tier = DocumentTier.CORE,
        readerCategory = ReaderCategory.EPUB,
        extensions = setOf("epub"),
        mimeTypes = setOf("application/epub+zip")
    ),
    TXT(
        displayLabel = "TXT",
        tier = DocumentTier.CORE,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("txt", "text", "log"),
        mimeTypes = setOf("text/plain")
    ),
    HTML(
        displayLabel = "HTML",
        tier = DocumentTier.CORE,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("html", "htm", "xhtml"),
        mimeTypes = setOf("text/html", "application/xhtml+xml")
    ),
    MD(
        displayLabel = "Markdown",
        tier = DocumentTier.CORE,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("md", "markdown"),
        mimeTypes = setOf("text/markdown", "text/x-markdown")
    ),
    DOCX(
        displayLabel = "DOCX",
        tier = DocumentTier.EXTENDED,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("docx"),
        mimeTypes = setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ),
    RTF(
        displayLabel = "RTF",
        tier = DocumentTier.EXTENDED,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("rtf"),
        mimeTypes = setOf("application/rtf", "text/rtf")
    ),
    ODT(
        displayLabel = "ODT",
        tier = DocumentTier.EXTENDED,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("odt"),
        mimeTypes = setOf("application/vnd.oasis.opendocument.text")
    ),
    FB2(
        displayLabel = "FB2",
        tier = DocumentTier.EXTENDED,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("fb2"),
        mimeTypes = setOf("application/x-fictionbook+xml", "text/xml")
    ),
    DOC(
        displayLabel = "DOC",
        tier = DocumentTier.EXTENDED,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("doc"),
        mimeTypes = setOf("application/msword")
    ),
    CBZ(
        displayLabel = "CBZ",
        tier = DocumentTier.SPECIALIZED,
        readerCategory = ReaderCategory.IMAGE_COLLECTION,
        extensions = setOf("cbz"),
        mimeTypes = setOf("application/x-cbz", "application/vnd.comicbook+zip")
    ),
    CBR(
        displayLabel = "CBR",
        tier = DocumentTier.SPECIALIZED,
        readerCategory = ReaderCategory.IMAGE_COLLECTION,
        extensions = setOf("cbr"),
        mimeTypes = setOf("application/x-cbr", "application/vnd.comicbook-rar")
    ),
    MOBI(
        displayLabel = "MOBI",
        tier = DocumentTier.SPECIALIZED,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("mobi", "prc"),
        mimeTypes = setOf("application/x-mobipocket-ebook")
    ),
    AZW3(
        displayLabel = "AZW3",
        tier = DocumentTier.SPECIALIZED,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("azw3", "kf8"),
        mimeTypes = setOf("application/vnd.amazon.ebook")
    ),
    XPS(
        displayLabel = "XPS",
        tier = DocumentTier.SPECIALIZED,
        readerCategory = ReaderCategory.REFLOWABLE_TEXT,
        extensions = setOf("xps", "oxps"),
        mimeTypes = setOf("application/vnd.ms-xpsdocument", "application/oxps")
    ),
    IMAGE(
        displayLabel = "Image",
        tier = DocumentTier.IMAGE,
        readerCategory = ReaderCategory.IMAGE_COLLECTION,
        extensions = setOf("jpg", "jpeg", "png", "webp", "tif", "tiff"),
        mimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/tiff")
    );

    companion object {
        fun fromName(name: String): DocumentFormat =
            enumValues<DocumentFormat>().firstOrNull { it.name == name } ?: TXT

        fun fromExtensionOrMime(filename: String, mimeType: String?): DocumentFormat? {
            val ext = filename.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                entries.firstOrNull { ext in it.extensions }?.let { return it }
            }
            val cleanMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
            if (!cleanMime.isNullOrEmpty()) {
                entries.firstOrNull { cleanMime in it.mimeTypes }?.let { return it }
            }
            return null
        }

        val safPickerMimeTypes: Array<String> = arrayOf(
            "application/pdf",
            "application/epub+zip",
            "text/plain",
            "text/html",
            "text/markdown",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/rtf",
            "text/rtf",
            "application/vnd.oasis.opendocument.text",
            "application/x-fictionbook+xml",
            "application/msword",
            "application/x-cbz",
            "application/vnd.comicbook+zip",
            "application/x-cbr",
            "application/x-mobipocket-ebook",
            "application/vnd.ms-xpsdocument",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/tiff",
            "application/octet-stream"
        )
    }
}
