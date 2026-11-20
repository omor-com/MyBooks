package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AnnotationDao
import com.example.data.local.dao.LibraryDao
import com.example.data.local.entity.BookCollectionCrossRef
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookTagCrossRef
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.HighlightEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.TagEntity

@Database(
    entities = [
        BookEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        CollectionEntity::class,
        BookTagCrossRef::class,
        BookCollectionCrossRef::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        ReadingSessionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class MyBooksDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        const val DATABASE_NAME = "mybooks_library.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN readingStatusName TEXT NOT NULL DEFAULT 'WANT_TO_READ'")
                db.execSQL("ALTER TABLE books ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN deletedAtEpochMs INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN estimatedTotalMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE books SET readingStatusName = 'FINISHED' WHERE readingProgress >= 0.99")
                db.execSQL("UPDATE books SET readingStatusName = 'CURRENTLY_READING' WHERE readingProgress > 0.0 AND readingProgress < 0.99")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reading_sessions` (
                        `id` TEXT NOT NULL,
                        `bookId` TEXT,
                        `bookTitleSnapshot` TEXT NOT NULL,
                        `epochDay` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `pagesRead` INTEGER NOT NULL,
                        `startedAtEpochMs` INTEGER NOT NULL,
                        `endedAtEpochMs` INTEGER NOT NULL,
                        `isFocusTimer` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId` ON `reading_sessions` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_epochDay` ON `reading_sessions` (`epochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_startedAtEpochMs` ON `reading_sessions` (`startedAtEpochMs`)")
            }
        }

        fun build(context: Context): MyBooksDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MyBooksDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
