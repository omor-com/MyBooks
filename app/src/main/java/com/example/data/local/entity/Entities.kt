package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDefault: Boolean = false
)

@Serializable
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String
)

@Serializable
@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["contentHash"]),
        Index(value = ["lastOpenedEpochMs"]),
        Index(value = ["dateAddedEpochMs"])
    ]
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val publisher: String = "",
    val language: String = "",
    val categoryId: String? = null,
    val dateAddedEpochMs: Long = System.currentTimeMillis(),
    val coverUri: String? = null,
    val originalFilename: String,
    val formatName: String,
    val fileUri: String,
    val fileSizeBytes: Long = 0L,
    val contentHash: String = "",
    val readingProgress: Float = 0f,
    val lastOpenedEpochMs: Long = 0L,
    val isFavorite: Boolean = false,
    val isFileMissing: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "book_tag_cross_ref",
    primaryKeys = ["bookId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["tagId"])]
)
data class BookTagCrossRef(
    val bookId: String,
    val tagId: String
)

@Serializable
@Entity(
    tableName = "book_collection_cross_ref",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["collectionId"])]
)
data class BookCollectionCrossRef(
    val bookId: String,
    val collectionId: String
)

@Serializable
@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val progressFraction: Float = 0f,
    val pageIndex: Int = 0,
    val totalPages: Int = 1,
    val chapterIndex: Int = 0,
    val totalChapters: Int = 1,
    val scrollOffset: Int = 0,
    val locatorJson: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["createdAtEpochMs"])]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val locatorJson: String,
    val locatorDisplay: String,
    val pageIndex: Int = 0,
    val chapterIndex: Int = 0,
    val scrollOffset: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["createdAtEpochMs"])]
)
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val bookTitleSnapshot: String = "",
    val selectedText: String,
    val locatorJson: String,
    val locatorDisplay: String,
    val pageIndex: Int = 0,
    val chapterIndex: Int = 0,
    val colorHex: String = "#FFB703",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["updatedAtEpochMs"])]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val bookId: String? = null,
    val bookTitleSnapshot: String = "",
    val title: String = "",
    val content: String,
    val locatorJson: String = "",
    val locatorDisplay: String = "",
    val pageIndex: Int = 0,
    val chapterIndex: Int = 0,
    val scrollOffset: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class BookWithDetails(
    @Embedded val book: BookEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookTagCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookCollectionCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "collectionId"
        )
    )
    val collections: List<CollectionEntity>,
    @Relation(parentColumn = "id", entityColumn = "bookId")
    val progress: ReadingProgressEntity?
)
