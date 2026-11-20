package com.example.ui.navigation

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.IncomingIntentRequest
import com.example.R
import com.example.data.reader.DocumentFormat
import com.example.ui.screens.bookmarks.BookmarksNotesScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.reader.ReaderScreen
import com.example.ui.screens.settings.BackupRestoreScreen
import com.example.ui.screens.settings.LegalDocumentScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.MyBooksTheme
import com.example.ui.viewmodel.LibraryEvent
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.ReaderViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data object HomeRoute

@Serializable
data object LibraryRoute

@Serializable
data object BookmarksNotesRoute

@Serializable
data object SettingsRoute

@Serializable
data object BackupRestoreRoute

@Serializable
data class LegalDocumentRoute(val isTerms: Boolean)

@Serializable
data class ReaderRoute(
    val bookId: String,
    val externalUri: String? = null,
    val pageIndex: Int? = null,
    val chapterIndex: Int? = null
)

private data class TopDestination(
    val route: Any,
    val routeClass: kotlin.reflect.KClass<*>,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

private val TopDestinations = listOf(
    TopDestination(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Rounded.Home, "nav_home"),
    TopDestination(LibraryRoute, LibraryRoute::class, R.string.nav_library, Icons.AutoMirrored.Rounded.MenuBook, "nav_library"),
    TopDestination(BookmarksNotesRoute, BookmarksNotesRoute::class, R.string.nav_bookmarks, Icons.Rounded.Bookmark, "nav_bookmarks"),
    TopDestination(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Rounded.Settings, "nav_settings")
)

@Composable
fun MyBooksApp(
    container: AppContainer,
    incomingRequest: IncomingIntentRequest?,
    onConsumeIncomingRequest: () -> Unit
) {
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(
                    repository = container.libraryRepository,
                    preferencesRepository = container.preferencesRepository
                ) as T
            }
        }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    preferencesRepository = container.preferencesRepository,
                    libraryRepository = container.libraryRepository,
                    soundManager = container.pageTurnSoundManager,
                    backupManager = container.backupManager
                ) as T
            }
        }
    )

    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val focusTimerState by settingsViewModel.focusTimerState.collectAsStateWithLifecycle()

    val baseContext = LocalContext.current
    val activityRegistryOwner = LocalActivityResultRegistryOwner.current
        ?: findOwner<ActivityResultRegistryOwner>(baseContext)
    val backPressedOwner = LocalOnBackPressedDispatcherOwner.current
        ?: findOwner<OnBackPressedDispatcherOwner>(baseContext)

    val localizedContext = remember(baseContext, prefs.appLanguage) {
        applyLocale(baseContext, prefs.appLanguage)
    }

    val providedLocals = remember(localizedContext, activityRegistryOwner, backPressedOwner) {
        buildList<ProvidedValue<*>> {
            add(LocalContext provides localizedContext)
            add(LocalConfiguration provides localizedContext.resources.configuration)
            if (activityRegistryOwner != null) {
                add(LocalActivityResultRegistryOwner provides activityRegistryOwner)
            }
            if (backPressedOwner != null) {
                add(LocalOnBackPressedDispatcherOwner provides backPressedOwner)
            }
        }.toTypedArray()
    }

    CompositionLocalProvider(*providedLocals) {
        MyBooksTheme(
            themeMode = prefs.appThemeMode,
            isBangla = prefs.appLanguage == "bn"
        ) {
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }

            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    libraryViewModel.importDocuments(uris, openFirstOnSingleImport = false)
                }
            }

            LaunchedEffect(incomingRequest) {
                val req = incomingRequest ?: return@LaunchedEffect
                if (req.openExternallyWithoutImport && req.uris.size == 1) {
                    navController.navigate(
                        ReaderRoute(
                            bookId = "",
                            externalUri = req.uris.first().toString()
                        )
                    ) {
                        launchSingleTop = true
                    }
                } else if (req.uris.isNotEmpty()) {
                    libraryViewModel.importDocuments(req.uris, openFirstOnSingleImport = true)
                }
                onConsumeIncomingRequest()
            }

            LaunchedEffect(Unit) {
                libraryViewModel.events.collect { event ->
                    when (event) {
                        is LibraryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                        is LibraryEvent.OpenBook -> navController.navigate(
                            ReaderRoute(
                                bookId = event.bookId,
                                pageIndex = event.pageIndex,
                                chapterIndex = event.chapterIndex
                            )
                        )
                    }
                }
            }

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showTopChrome = TopDestinations.any { dest ->
                currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
            }
            val isInReader = currentDestination?.hierarchy?.any { it.hasRoute(ReaderRoute::class) } == true

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = if (isInReader) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                    bottomBar = {
                        if (showTopChrome && !useNavRail) {
                            NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                                TopDestinations.forEach { dest ->
                                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(dest.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(dest.labelRes),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        if (showTopChrome && useNavRail) {
                            NavigationRail(modifier = Modifier.testTag("navigation_rail")) {
                                TopDestinations.forEach { dest ->
                                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(dest.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(dest.labelRes),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = HomeRoute,
                                enterTransition = {
                                    if (prefs.readerReducedAnimation) EnterTransition.None else fadeIn(animationSpec = tween(180))
                                },
                                exitTransition = {
                                    if (prefs.readerReducedAnimation) ExitTransition.None else fadeOut(animationSpec = tween(140))
                                },
                                popEnterTransition = {
                                    if (prefs.readerReducedAnimation) EnterTransition.None else fadeIn(animationSpec = tween(180))
                                },
                                popExitTransition = {
                                    if (prefs.readerReducedAnimation) ExitTransition.None else fadeOut(animationSpec = tween(140))
                                }
                            ) {
                                composable<HomeRoute> {
                                    HomeScreen(
                                        state = libraryState,
                                        focusTimerState = focusTimerState,
                                        currentLanguage = prefs.appLanguage,
                                        onToggleLanguage = {
                                            settingsViewModel.setLanguage(if (prefs.appLanguage == "bn") "en" else "bn")
                                        },
                                        onImportClick = { importLauncher.launch(DocumentFormat.safPickerMimeTypes) },
                                        onOpenBook = { bookId ->
                                            navController.navigate(ReaderRoute(bookId = bookId))
                                        },
                                        onNavigateToLibrary = {
                                            navController.navigate(LibraryRoute) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onStartFocusTimer = settingsViewModel::startFocusTimer,
                                        onPauseFocusTimer = settingsViewModel::pauseFocusTimer,
                                        onResumeFocusTimer = settingsViewModel::resumeFocusTimer,
                                        onStopFocusTimer = settingsViewModel::stopAndSaveFocusTimer,
                                        onDismissBreakAlert = settingsViewModel::dismissBreakAlert
                                    )
                                }

                                composable<LibraryRoute> {
                                    LibraryScreen(
                                        state = libraryState,
                                        onSearchChange = libraryViewModel::setSearchQuery,
                                        onSubmitSearch = libraryViewModel::submitSearchQuery,
                                        onToggleSearchInsideBooks = libraryViewModel::toggleSearchInsideBooks,
                                        onClearSearchHistory = libraryViewModel::clearSearchHistory,
                                        onOpenBookAtLocation = { bookId, pageIndex, chapterIndex ->
                                            navController.navigate(
                                                ReaderRoute(
                                                    bookId = bookId,
                                                    pageIndex = pageIndex,
                                                    chapterIndex = chapterIndex
                                                )
                                            )
                                        },
                                        onToggleGridView = libraryViewModel::toggleGridView,
                                        onSelectFilterChip = libraryViewModel::setFilterChip,
                                        onSelectCategory = libraryViewModel::selectCategory,
                                        onSelectTag = libraryViewModel::selectTag,
                                        onSelectCollection = libraryViewModel::selectCollection,
                                        onSelectSort = libraryViewModel::setSortOption,
                                        onImportClick = { importLauncher.launch(DocumentFormat.safPickerMimeTypes) },
                                        onOpenBook = { bookId ->
                                            navController.navigate(ReaderRoute(bookId = bookId))
                                        },
                                        onToggleFavorite = { item -> libraryViewModel.toggleFavorite(item.book) },
                                        onSetReadingStatus = libraryViewModel::setReadingStatus,
                                        onRelocateFile = libraryViewModel::relocateBookFile,
                                        onUpdateMetadata = libraryViewModel::updateBookMetadata,
                                        onSetBookCover = libraryViewModel::setBookCover,
                                        onRestoreDefaultCover = libraryViewModel::restoreDefaultCover,
                                        onCreateCategory = libraryViewModel::createCategory,
                                        onRenameCategory = libraryViewModel::renameCategory,
                                        onDeleteCategory = libraryViewModel::deleteCategory,
                                        onCreateCollection = libraryViewModel::createCollection,
                                        onUpdateCollection = libraryViewModel::updateCollection,
                                        onDeleteCollection = libraryViewModel::deleteCollection,
                                        onSoftDeleteBook = libraryViewModel::softDeleteBook,
                                        onRestoreBookFromTrash = libraryViewModel::restoreBookFromTrash,
                                        onDeleteBookPermanently = libraryViewModel::removeBook,
                                        onDuplicateSkip = libraryViewModel::resolveDuplicateSkip,
                                        onDuplicateKeepBoth = libraryViewModel::resolveDuplicateKeepBoth,
                                        onDuplicateReplaceUri = libraryViewModel::resolveDuplicateReplaceUri
                                    )
                                }

                                composable<BookmarksNotesRoute> {
                                    BookmarksNotesScreen(
                                        state = libraryState,
                                        onOpenBookAtLocation = { bookId, pageIndex, chapterIndex ->
                                            navController.navigate(
                                                ReaderRoute(
                                                    bookId = bookId,
                                                    pageIndex = pageIndex,
                                                    chapterIndex = chapterIndex
                                                )
                                            )
                                        },
                                        onSaveNote = libraryViewModel::saveStandaloneNote,
                                        onDeleteBookmark = libraryViewModel::deleteBookmark,
                                        onDeleteNote = libraryViewModel::deleteNote,
                                        onDeleteHighlight = libraryViewModel::deleteHighlight,
                                        onExportAnnotations = libraryViewModel::exportAnnotations
                                    )
                                }

                                composable<SettingsRoute> {
                                    SettingsScreen(
                                        viewModel = settingsViewModel,
                                        onOpenBackupRestore = {
                                            navController.navigate(BackupRestoreRoute)
                                        },
                                        onOpenPrivacyPolicy = {
                                            navController.navigate(LegalDocumentRoute(isTerms = false))
                                        },
                                        onOpenTerms = {
                                            navController.navigate(LegalDocumentRoute(isTerms = true))
                                        }
                                    )
                                }

                                composable<BackupRestoreRoute> {
                                    BackupRestoreScreen(
                                        viewModel = settingsViewModel,
                                        allBooks = libraryState.allBooks,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<LegalDocumentRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<LegalDocumentRoute>()
                                    LegalDocumentScreen(
                                        isTerms = route.isTerms,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable<ReaderRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<ReaderRoute>()
                                    val readerVm: ReaderViewModel = viewModel(
                                        key = "${route.bookId}_${route.externalUri}_${route.pageIndex}_${route.chapterIndex}",
                                        factory = object : ViewModelProvider.Factory {
                                            @Suppress("UNCHECKED_CAST")
                                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                                return ReaderViewModel(
                                                    appContext = baseContext.applicationContext,
                                                    bookId = route.bookId,
                                                    externalUri = route.externalUri,
                                                    initialPageOverride = route.pageIndex,
                                                    initialChapterOverride = route.chapterIndex,
                                                    libraryRepository = container.libraryRepository,
                                                    preferencesRepository = container.preferencesRepository,
                                                    soundManager = container.pageTurnSoundManager,
                                                    ttsManager = container.ttsReadAloudManager
                                                ) as T
                                            }
                                        }
                                    )
                                    ReaderScreen(
                                        viewModel = readerVm,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun applyLocale(baseContext: Context, languageCode: String): Context {
    val locale = Locale.forLanguageTag(if (languageCode == "bn") "bn" else "en")
    Locale.setDefault(locale)
    val config = Configuration(baseContext.resources.configuration)
    config.setLocale(locale)
    val configContext = baseContext.createConfigurationContext(config)
    return object : ContextWrapper(baseContext) {
        override fun getResources(): Resources = configContext.resources
    }
}

private inline fun <reified T> findOwner(context: Context): T? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is T) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? T
}
