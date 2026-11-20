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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.example.data.community.AccountStatus
import com.example.data.community.BadgeType
import com.example.data.community.CommunityLinkValidator
import com.example.data.community.CommunityPost
import com.example.data.community.CommunityRepository
import com.example.data.community.CommunityUser
import com.example.data.community.GoogleAuthHelper
import com.example.data.community.PostVisibility
import com.example.data.community.ReportReason
import com.example.ui.components.CommunityAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.viewmodel.CommunityEvent
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat

private data class PendingReportTarget(
    val targetType: String,
    val targetId: String,
    val summary: String,
    val parentPostId: String = ""
)

@Composable
fun CommunityRouteScreen(
    repository: CommunityRepository?,
    firebaseAuth: FirebaseAuth?,
    isBangla: Boolean,
    modifier: Modifier = Modifier
) {
    if (repository == null || firebaseAuth == null) {
        CommunityUnavailableView(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }

    var currentUser by remember(firebaseAuth) { mutableStateOf(firebaseAuth.currentUser) }
    var isOnline by remember { mutableStateOf(checkNetworkAvailable(context)) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
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
        try {
            cm?.registerDefaultNetworkCallback(callback)
        } catch (_: SecurityException) {
            isOnline = checkNetworkAvailable(context)
        } catch (_: Throwable) {
            isOnline = checkNetworkAvailable(context)
        }
        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(isOnline, firebaseAuth) {
        if (isOnline && firebaseAuth.currentUser == null) {
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                auth = firebaseAuth,
                onAuthSuccess = { currentUser = firebaseAuth.currentUser },
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
                    auth = firebaseAuth,
                    onAuthSuccess = {
                        isSigningIn = false
                        currentUser = firebaseAuth.currentUser
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
                    GoogleAuthHelper.signOut(credentialManager, firebaseAuth, scope) {
                        currentUser = null
                    }
                },
                modifier = modifier
            )
        }
    }
}

private fun checkNetworkAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: SecurityException) {
        true
    } catch (_: Throwable) {
        false
    }
}

@Composable
private fun CommunityUnavailableView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("community_unavailable_view"),
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
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.community_unavailable_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.community_unavailable_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
    val commentsState by viewModel.activePostCommentsState.collectAsStateWithLifecycle()
    val isTrustedAdmin by viewModel.isTrustedAdmin.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    var showCreatePostDialog by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<CommunityPost?>(null) }
    var pendingDeletePost by remember { mutableStateOf<CommunityPost?>(null) }
    var activePostForComments by remember { mutableStateOf<CommunityPost?>(null) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showAdminSheet by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<CommunityUser?>(null) }
    var reportTarget by remember { mutableStateOf<PendingReportTarget?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CommunityEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val openAuthorProfile: (String, String, String, String) -> Unit = { uid, fallbackName, fallbackPhoto, fallbackBadge ->
        scope.launch {
            selectedProfile = viewModel.loadUserProfile(uid)
                ?: CommunityUser(
                    id = uid,
                    uid = uid,
                    displayName = fallbackName,
                    photoUrl = fallbackPhoto,
                    badgeType = fallbackBadge
                )
        }
    }

    val openDriveUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_no_browser_error))
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
                        Text(
                            text = stringResource(R.string.community_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                    IconButton(
                        onClick = { viewModel.refreshProfileAndRole() },
                        modifier = Modifier.testTag("community_refresh_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
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
            if (profile?.isWriteBlocked != true) {
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
            profile?.takeIf { it.isWriteBlocked }?.let { blockedProfile ->
                val noticeRes = when (blockedProfile.effectiveStatus) {
                    AccountStatus.BANNED -> R.string.community_status_notice_banned
                    AccountStatus.DEACTIVATED -> R.string.community_status_notice_deactivated
                    AccountStatus.SUSPENDED -> R.string.community_status_notice_suspended
                    AccountStatus.ACTIVE -> R.string.community_restricted_banner
                }
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(noticeRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (blockedProfile.restrictionReason.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.community_status_reason_prefix, blockedProfile.restrictionReason),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        viewModel.refreshProfileAndRole()
                        delay(450)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
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
                                itemsIndexed(
                                    items = st.data,
                                    key = { index, post -> post.id.ifBlank { "post_$index" } }
                                ) { _, post ->
                                    CommunityPostCard(
                                        post = post,
                                        isOwnPost = post.authorUid == firebaseUser.uid,
                                        canEditOwn = profile?.isWriteBlocked != true,
                                        onAuthorClick = {
                                            openAuthorProfile(
                                                post.authorUid,
                                                post.authorName,
                                                post.authorPhotoUrl,
                                                post.authorBadge
                                            )
                                        },
                                        onOpenDriveLink = openDriveUrl,
                                        onOpenComments = {
                                            activePostForComments = post
                                            viewModel.openPostComments(post.id)
                                        },
                                        onEditOwn = { editingPost = post },
                                        onDeleteOwn = { pendingDeletePost = post },
                                        onReportPost = {
                                            reportTarget = PendingReportTarget(
                                                targetType = "post",
                                                targetId = post.id,
                                                summary = post.content.take(100)
                                            )
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
    }

    if (showCreatePostDialog) {
        PostComposerDialog(
            initialPost = null,
            isSubmitting = isSubmitting,
            onDismiss = { showCreatePostDialog = false },
            onSubmit = { content, driveLink, bookTitle ->
                viewModel.createPost(content, driveLink, bookTitle)
                showCreatePostDialog = false
            }
        )
    }

    editingPost?.let { post ->
        PostComposerDialog(
            initialPost = post,
            isSubmitting = isSubmitting,
            onDismiss = { editingPost = null },
            onSubmit = { content, driveLink, bookTitle ->
                viewModel.editOwnPost(post.id, content, driveLink, bookTitle)
                editingPost = null
            }
        )
    }

    pendingDeletePost?.let { post ->
        AlertDialog(
            onDismissRequest = { pendingDeletePost = null },
            title = { Text(stringResource(R.string.community_delete_post_confirm_title)) },
            text = { Text(stringResource(R.string.community_delete_post_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOwnPost(post.id)
                        pendingDeletePost = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePost = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    activePostForComments?.let { post ->
        CommunityCommentsSheet(
            post = post,
            commentsState = commentsState,
            currentUserUid = firebaseUser.uid,
            currentUserProfile = profile,
            isTrustedAdmin = isTrustedAdmin,
            isSubmitting = isSubmitting,
            onDismiss = {
                activePostForComments = null
                viewModel.closePostComments()
            },
            onAuthorClick = openAuthorProfile,
            onOpenDriveLink = openDriveUrl,
            onSendComment = { text, parentId, replyToName ->
                viewModel.addComment(post.id, text, parentId, replyToName)
            },
            onDeleteOwnComment = { commentId ->
                viewModel.deleteOwnComment(post.id, commentId)
            },
            onReportComment = { comment ->
                reportTarget = PendingReportTarget(
                    targetType = "comment",
                    targetId = comment.id,
                    summary = "${comment.authorName}: ${comment.content.take(90)}",
                    parentPostId = post.id
                )
            },
            onAdminHideComment = { comment ->
                viewModel.adminSetCommentVisibility(post.id, comment.id, PostVisibility.HIDDEN, "Hidden by administrator")
            },
            onAdminDeleteComment = { comment ->
                viewModel.adminDeleteComment(post.id, comment.id, "Removed by administrator")
            },
            onLoadMoreComments = { viewModel.loadMoreComments() }
        )
    }

    selectedProfile?.let { targetUser ->
        MemberProfileDialog(
            user = targetUser,
            isOwnProfile = targetUser.uid == firebaseUser.uid,
            loadMemberPosts = viewModel::loadUserVisiblePosts,
            onDismiss = { selectedProfile = null },
            onSaveOwnProfile = { newName, newBio ->
                viewModel.updateOwnProfile(newName, newBio)
                selectedProfile = null
            },
            onSignOutOwnProfile = {
                selectedProfile = null
                onSignOut()
            },
            onReportProfile = {
                selectedProfile = null
                reportTarget = PendingReportTarget(
                    targetType = "profile",
                    targetId = targetUser.uid,
                    summary = targetUser.displayName
                )
            }
        )
    }

    reportTarget?.let { target ->
        ReportTargetDialog(
            targetType = target.targetType,
            onDismiss = { reportTarget = null },
            onSubmit = { reason: ReportReason, details: String ->
                viewModel.submitReport(
                    targetType = target.targetType,
                    targetId = target.targetId,
                    targetSummary = target.summary,
                    reason = reason,
                    details = details,
                    parentPostId = target.parentPostId
                )
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
private fun PostComposerDialog(
    initialPost: CommunityPost?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (content: String, driveLink: String, bookTitle: String) -> Unit
) {
    val isEditing = initialPost != null
    var content by remember(initialPost) { mutableStateOf(initialPost?.content.orEmpty()) }
    var driveLink by remember(initialPost) { mutableStateOf(initialPost?.driveLink.orEmpty()) }
    var bookTitle by remember(initialPost) { mutableStateOf(initialPost?.bookTitle.orEmpty()) }

    val isDriveValid = CommunityLinkValidator.isValidOptionalDriveLink(driveLink)
    val isBookTitleValid = CommunityLinkValidator.isValidOptionalBookTitle(driveLink, bookTitle)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEditing) R.string.community_edit_post else R.string.community_create_post
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                if (driveLink.isNotBlank()) {
                    OutlinedTextField(
                        value = bookTitle,
                        onValueChange = { if (it.length <= 120) bookTitle = it },
                        label = { Text(stringResource(R.string.community_book_title_hint)) },
                        isError = !isBookTitleValid,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("community_post_book_title_input")
                    )
                    if (!isBookTitleValid) {
                        Text(
                            text = stringResource(R.string.community_book_title_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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
                onClick = { onSubmit(content, driveLink, if (driveLink.isBlank()) "" else bookTitle) },
                enabled = content.isNotBlank() && isDriveValid && isBookTitleValid && !isSubmitting,
                modifier = Modifier.testTag("community_publish_post_button")
            ) {
                Text(
                    stringResource(
                        if (isEditing) R.string.community_save_post else R.string.community_publish_post
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    isOwnPost: Boolean,
    canEditOwn: Boolean,
    onAuthorClick: () -> Unit,
    onOpenDriveLink: (String) -> Unit,
    onOpenComments: () -> Unit,
    onEditOwn: () -> Unit,
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
                        if (canEditOwn) {
                            IconButton(onClick = onEditOwn) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                        }
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
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDriveLink(post.driveLink) }
                        .testTag("community_post_drive_button")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (post.bookTitle.isNotBlank()) {
                                    stringResource(R.string.community_drive_button_title, post.bookTitle)
                                } else {
                                    stringResource(R.string.community_drive_button_fallback)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.community_drive_link_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onOpenComments,
                    modifier = Modifier.testTag("community_open_comments_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.community_comments_action))
                }
            }
        }
    }
}
