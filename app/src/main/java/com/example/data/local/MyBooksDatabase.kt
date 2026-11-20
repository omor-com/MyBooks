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
        NoteEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MyBooksDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        const val DATABASE_NAME = "mybooks_library.db"

        // Explicit non-destructive migration template for future schema versions
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Future additive schema changes belong here
            }
        }

        fun build(context: Context): MyBooksDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MyBooksDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}
