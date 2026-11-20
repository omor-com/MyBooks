package com.example

import android.app.Application
import android.content.Context
import com.example.data.audio.PageTurnSoundManager
import com.example.data.backup.BackupManager
import com.example.data.community.CommunityRepository
import com.example.data.local.MyBooksDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.reader.TtsReadAloudManager
import com.example.data.repository.LibraryRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseAvailability {
    fun ensureInitialized(context: Context): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                true
            } else {
                FirebaseApp.initializeApp(context) != null
            }
        } catch (_: Throwable) {
            false
        }
    }
}

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

    val firestoreDb: FirebaseFirestore? by lazy {
        if (!FirebaseAvailability.ensureInitialized(application)) return@lazy null
        try {
            FirebaseFirestore.getInstance(application.getString(R.string.firestore_database_id))
        } catch (_: Throwable) {
            null
        }
    }

    val firebaseAuth: FirebaseAuth? by lazy {
        if (!FirebaseAvailability.ensureInitialized(application)) return@lazy null
        try {
            FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    val communityRepository: CommunityRepository? by lazy {
        val db = firestoreDb ?: return@lazy null
        val auth = firebaseAuth ?: return@lazy null
        CommunityRepository(db = db, auth = auth)
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
