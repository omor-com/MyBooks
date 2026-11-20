package com.example.ui.screens.community

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.community.BadgeType
import com.example.data.community.CommunityLinkValidator
import com.example.data.community.CommunityPost
import com.example.data.community.CommunityRepository
import com.example.data.community.CommunityUser
import com.example.data.community.GoogleAuthHelper
import com.example.data.community.ReportReason
import com.example.ui.components.CommunityAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.viewmodel.CommunityEvent
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.text.DateFormat

@Composable
fun CommunityRouteScreen(
    repository: CommunityRepository,
    isBangla: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isOnline by remember { mutableStateOf(checkNetworkAvailable(context)) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                isOnline = checkNetworkAvailable(context)
            }
        }
        cm?.registerDefaultNetworkCallback(callback)
        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isOnline) {
        if (isOnline && Firebase.auth.currentUser == null) {
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (!isOnline && user == null) {
        CommunityOfflineView(modifier = modifier)
    } else if (user == null) {
        CommunitySignInView(
            isOnline = isOnline,
            isSigningIn = isSigningIn,
            errorMessage = authError,
            onSignInClick = {
                isSigningIn = true
                authError = null
                GoogleAuthHelper.onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                        isSigningIn = false
                        currentUser = Firebase.auth.currentUser
                    },
                    onAuthError = { err ->
                        isSigningIn = false
                        authError = err
                    },
                    onAuthCancelled = {
                        isSigningIn = false
                    },
                    scope = scope
                )
            },
            modifier = modifier
        )
    } else {
        key(user.uid) {
            val vm: CommunityViewModel = viewModel(
                key = user.uid,
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return CommunityViewModel(repository, user.uid) as T
                    }
                }
            )
            AuthenticatedCommunityFeed(
                firebaseUser = user,
                viewModel = vm,
                isOnline = isOnline,
                isBangla = isBangla,
                onSignOut = {
                    GoogleAuthHelper.signOut(credentialManager, scope) {
                        currentUser = null
                    }
                },
                modifier = modifier
            )
        }
    }
}

private fun checkNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
private fun CommunityOfflineView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("community_offline_view"),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.community_offline_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.community_offline_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommunitySignInView(
    isOnline: Boolean,
    isSigningIn: Boolean,
    errorMessage: String?,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("community_signin_view"),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.community_sign_in_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.community_sign_in_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onSignInClick,
                    enabled = isOnline && !isSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("community_google_signin_button")
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.community_sign_in_button))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedCommunityFeed(
    firebaseUser: FirebaseUser,
    viewModel: CommunityViewModel,
    isOnline: Boolean,
    isBangla: Boolean,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val profile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val isTrustedAdmin by viewModel.isTrustedAdmin.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showAdminSheet by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<CommunityUser?>(null) }
    var reportTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) } // type, id, summary

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CommunityEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("community_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.community_title), fontWeight = FontWeight.Bold)
                        profile?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = it.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                VerifiedBadgeIcon(badgeType = it.badge, size = 14.dp)
                            }
                        }
                    }
                },
                actions = {
                    if (isTrustedAdmin) {
                        IconButton(
                            onClick = { showAdminSheet = true },
                            modifier = Modifier.testTag("community_admin_button")
                        ) {
                            Icon(
                                Icons.Rounded.AdminPanelSettings,
                                contentDescription = stringResource(R.string.community_admin_panel),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = { showRulesDialog = true }, modifier = Modifier.testTag("community_rules_button")) {
                        Icon(Icons.Rounded.Gavel, contentDescription = stringResource(R.string.community_rules_title))
                    }
                    IconButton(
                        onClick = {
                            profile?.let { selectedProfile = it }
                        },
                        modifier = Modifier.testTag("community_own_profile_button")
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = stringResource(R.string.community_profile_title))
                    }
                    IconButton(onClick = onSignOut, modifier = Modifier.testTag("community_signout_button")) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = stringResource(R.string.community_sign_out))
                    }
                }
            )
        },
        floatingActionButton = {
            if (profile?.restricted != true) {
                FloatingActionButton(
                    onClick = { showCreatePostDialog = true },
                    modifier = Modifier.testTag("community_create_post_fab")
                ) {
                    Icon(Icons.Rounded.AddComment, contentDescription = stringResource(R.string.community_create_post))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isOnline) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            text = stringResource(R.string.community_offline_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            if (profile?.restricted == true) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            text = stringResource(R.string.community_restricted_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            when (val st = feedState) {
                UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(st.message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.refreshProfileAndRole() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    if (st.data.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.community_empty_feed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(st.data, key = { it.id }) { post ->
                                CommunityPostCard(
                                    post = post,
                                    isOwnPost = post.authorUid == firebaseUser.uid,
                                    onAuthorClick = {
                                        scope.launch {
                                            selectedProfile = viewModel.loadUserProfile(post.authorUid)
                                                ?: CommunityUser(
                                                    id = post.authorUid,
                                                    uid = post.authorUid,
                                                    displayName = post.authorName,
                                                    photoUrl = post.authorPhotoUrl,
                                                    badgeType = post.authorBadge
                                                )
                                        }
                                    },
                                    onOpenDriveLink = { url ->
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        } catch (_: ActivityNotFoundException) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_no_browser_error))
                                            }
                                        }
                                    },
                                    onDeleteOwn = { viewModel.deleteOwnPost(post.id) },
                                    onReportPost = {
                                        reportTarget = Triple("post", post.id, post.content.take(100))
                                    }
                                )
                            }
                            if (st.data.size >= 25) {
                                item {
                                    OutlinedButton(
                                        onClick = { viewModel.loadMorePosts() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.community_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePostDialog) {
        var content by remember { mutableStateOf("") }
        var driveLink by remember { mutableStateOf("") }
        val isDriveValid = CommunityLinkValidator.isValidOptionalDriveLink(driveLink)

        AlertDialog(
            onDismissRequest = { showCreatePostDialog = false },
            title = { Text(stringResource(R.string.community_create_post)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { if (it.length <= 2000) content = it },
                        label = { Text(stringResource(R.string.community_post_hint)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("community_post_content_input")
                    )
                    OutlinedTextField(
                        value = driveLink,
                        onValueChange = { driveLink = it },
                        label = { Text(stringResource(R.string.community_drive_link_hint)) },
                        isError = !isDriveValid,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("community_post_drive_input")
                    )
                    if (!isDriveValid) {
                        Text(
                            text = stringResource(R.string.community_drive_link_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (driveLink.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.community_drive_link_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPost(content, driveLink)
                        showCreatePostDialog = false
                    },
                    enabled = content.isNotBlank() && isDriveValid && !isSubmitting,
                    modifier = Modifier.testTag("community_publish_post_button")
                ) {
                    Text(stringResource(R.string.community_publish_post))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePostDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    selectedProfile?.let { targetUser ->
        MemberProfileDialog(
            user = targetUser,
            isOwnProfile = targetUser.uid == firebaseUser.uid,
            onDismiss = { selectedProfile = null },
            onSaveOwnProfile = { newName, newBio ->
                viewModel.updateOwnProfile(newName, newBio)
                selectedProfile = null
            },
            onReportProfile = {
                selectedProfile = null
                reportTarget = Triple("user", targetUser.uid, targetUser.displayName)
            }
        )
    }

    reportTarget?.let { (type, id, summary) ->
        ReportTargetDialog(
            targetType = type,
            onDismiss = { reportTarget = null },
            onSubmit = { reason: ReportReason, details: String ->
                viewModel.submitReport(type, id, summary, reason, details)
                reportTarget = null
            }
        )
    }

    if (showRulesDialog) {
        CommunityRulesDialog(
            isBangla = isBangla,
            onDismiss = { showRulesDialog = false }
        )
    }

    if (showAdminSheet && isTrustedAdmin) {
        AdminModerationModal(
            viewModel = viewModel,
            onDismiss = { showAdminSheet = false }
        )
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    isOwnPost: Boolean,
    onAuthorClick: () -> Unit,
    onOpenDriveLink: (String) -> Unit,
    onDeleteOwn: () -> Unit,
    onReportPost: () -> Unit
) {
    val isAdminAuthor = post.badge == BadgeType.RED_ADMIN
    val formattedDate = remember(post.createdAt) {
        post.createdAt?.toDate()?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it) }.orEmpty()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onAuthorClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CommunityAvatar(
                        photoUrl = post.authorPhotoUrl,
                        displayName = post.authorName,
                        isAdmin = isAdminAuthor,
                        size = 40.dp
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            VerifiedBadgeIcon(badgeType = post.badge, size = 16.dp)
                        }
                        if (formattedDate.isNotEmpty()) {
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row {
                    if (isOwnPost) {
                        IconButton(onClick = onDeleteOwn) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                        }
                    } else {
                        IconButton(onClick = onReportPost) {
                            Icon(Icons.Rounded.Flag, contentDescription = stringResource(R.string.community_report_post))
                        }
                    }
                }
            }

            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge
            )

            if (post.driveLink.isNotBlank() && CommunityLinkValidator.isValidOptionalDriveLink(post.driveLink)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDriveLink(post.driveLink) }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(R.string.community_open_drive_link),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = stringResource(R.string.community_drive_link_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
