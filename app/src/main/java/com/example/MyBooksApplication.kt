package com.example

import android.app.Application
import com.example.data.audio.PageTurnSoundManager
import com.example.data.backup.BackupManager
import com.example.data.community.CommunityRepository
import com.example.data.local.MyBooksDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LibraryRepository
import com.google.firebase.firestore.FirebaseFirestore

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
    val backupManager = BackupManager(
        context = application,
        database = database,
        preferencesRepository = preferencesRepository,
        soundFileProvider = { pageTurnSoundManager.soundFile() }
    )
    val firestoreDb: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(application.getString(R.string.firestore_database_id))
    }
    val communityRepository: CommunityRepository by lazy {
        CommunityRepository(firestoreDb)
    }
}

class MyBooksApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
