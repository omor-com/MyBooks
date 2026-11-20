package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.BookCollectionCrossRef
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookTagCrossRef
import com.example.data.local.entity.BookWithDetails
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Transaction
    @Query("SELECT * FROM books ORDER BY dateAddedEpochMs DESC")
    fun observeAllBooksWithDetails(): Flow<List<BookWithDetails>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBookWithDetails(bookId: String): Flow<BookWithDetails?>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookWithDetails(bookId: String): BookWithDetails?

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAllBooksSnapshot(): List<BookEntity>

    @Query("SELECT * FROM books WHERE contentHash = :hash AND contentHash != '' LIMIT 1")
    suspend fun findByContentHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE originalFilename = :filename AND fileSizeBytes = :size LIMIT 1")
    suspend fun findByFilenameAndSize(filename: String, size: Long): BookEntity?

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesSnapshot(): List<CategoryEntity>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsSnapshot(): List<TagEntity>

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections")
    suspend fun getAllCollectionsSnapshot(): List<CollectionEntity>

    @Query("SELECT * FROM book_tag_cross_ref")
    suspend fun getAllBookTagCrossRefs(): List<BookTagCrossRef>

    @Query("SELECT * FROM book_collection_cross_ref")
    suspend fun getAllBookCollectionCrossRefs(): List<BookCollectionCrossRef>

    @Query("SELECT * FROM reading_progress")
    suspend fun getAllProgressSnapshot(): List<ReadingProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId LIMIT 1")
    suspend fun getProgressForBook(bookId: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET isFavorite = :favorite, updatedAtEpochMs = :now WHERE id = :bookId")
    suspend fun setFavorite(bookId: String, favorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE books SET isFileMissing = :missing, updatedAtEpochMs = :now WHERE id = :bookId")
    suspend fun setFileMissing(bookId: String, missing: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE books SET fileUri = :newUri, originalFilename = :filename, fileSizeBytes = :sizeBytes, contentHash = :hash, isFileMissing = 0, updatedAtEpochMs = :now WHERE id = :bookId")
    suspend fun relocateBookFile(
        bookId: String,
        newUri: String,
        filename: String,
        sizeBytes: Long,
        hash: String,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE books SET readingProgress = :progress, lastOpenedEpochMs = :openedAt, updatedAtEpochMs = :openedAt WHERE id = :bookId")
    suspend fun updateBookProgressSummary(bookId: String, progress: Float, openedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadingProgress(progress: ReadingProgressEntity)

    @Transaction
    suspend fun saveReadingProgress(progress: ReadingProgressEntity, openedAt: Long) {
        upsertReadingProgress(progress)
        updateBookProgressSummary(progress.bookId, progress.progressFraction, openedAt)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollections(collections: List<CollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookTagCrossRef(ref: BookTagCrossRef)

    @Query("DELETE FROM book_tag_cross_ref WHERE bookId = :bookId")
    suspend fun clearBookTags(bookId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookCollectionCrossRef(ref: BookCollectionCrossRef)

    @Query("DELETE FROM book_collection_cross_ref WHERE bookId = :bookId")
    suspend fun clearBookCollections(bookId: String)

    @Transaction
    suspend fun replaceBookTagsAndCollections(
        bookId: String,
        tagIds: List<String>,
        collectionIds: List<String>
    ) {
        clearBookTags(bookId)
        tagIds.distinct().forEach { insertBookTagCrossRef(BookTagCrossRef(bookId, it)) }
        clearBookCollections(bookId)
        collectionIds.distinct().forEach { insertBookCollectionCrossRef(BookCollectionCrossRef(bookId, it)) }
    }

    @Query("DELETE FROM notes WHERE bookId = :bookId")
    suspend fun deleteNotesForBook(bookId: String)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)

    @Transaction
    suspend fun removeBook(bookId: String, deleteNotes: Boolean) {
        if (deleteNotes) {
            deleteNotesForBook(bookId)
        }
        deleteBookById(bookId)
    }
}
