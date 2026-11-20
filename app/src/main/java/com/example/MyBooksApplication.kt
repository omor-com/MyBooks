package com.example

import android.app.Application
import com.example.data.audio.PageTurnSoundManager
import com.example.data.backup.BackupManager
import com.example.data.local.MyBooksDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.reader.TtsReadAloudManager
import com.example.data.repository.LibraryRepository

class AppContainer(application: Application) {
    val database: MyBooksDatabase = MyBooksDatabase.build(application)
    val preferencesRepository = UserPreferencesRepository(application)
    val libraryRepository = LibraryRepository(
        context = application,
        libraryDao = database.libraryDao(),
        annotationDao = database.annotationDao()
    )
    val pageTurnSoundManager = PageTurnSoundManager(
        context = application,
        preferencesRepository = preferencesRepository
    )
    val ttsReadAloudManager = TtsReadAloudManager(application)
    val backupManager = BackupManager(
        context = application,
        database = database,
        preferencesRepository = preferencesRepository,
        soundFileProvider = { pageTurnSoundManager.soundFile() }
    )
}

class MyBooksApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
