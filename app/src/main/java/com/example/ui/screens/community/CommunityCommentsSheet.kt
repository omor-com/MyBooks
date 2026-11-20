package com.example.ui.screens.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.community.BadgeType
import com.example.data.community.CommunityComment
import com.example.data.community.CommunityLinkValidator
import com.example.data.community.CommunityPost
import com.example.data.community.CommunityUser
import com.example.ui.components.CommunityAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.viewmodel.UiState
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityCommentsSheet(
    post: CommunityPost,
    commentsState: UiState<List<CommunityComment>>,
    currentUserUid: String,
    currentUserProfile: CommunityUser?,
    isTrustedAdmin: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onAuthorClick: (String, String, String, String) -> Unit,
    onOpenDriveLink: (String) -> Unit,
    onSendComment: (content: String, parentCommentId: String, replyToAuthorName: String) -> Unit,
    onDeleteOwnComment: (commentId: String) -> Unit,
    onReportComment: (comment: CommunityComment) -> Unit,
    onAdminHideComment: (comment: CommunityComment) -> Unit,
    onAdminDeleteComment: (comment: CommunityComment) -> Unit,
    onLoadMoreComments: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentText by remember(post.id) { mutableStateOf("") }
    var replyingTo by remember(post.id) { mutableStateOf<CommunityComment?>(null) }
    var pendingDeleteComment by remember { mutableStateOf<CommunityComment?>(null) }
    var pendingAdminAction by remember { mutableStateOf<Pair<CommunityComment, Boolean>?>(null) } // true=delete, false=hide
    val expandedReplies = remember(post.id) { mutableStateMapOf<String, Boolean>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 640.dp)
                .imePadding()
                .testTag("community_comments_sheet")
        ) {
            Text(
                text = stringResource(R.string.community_comments_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // Post summary header
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.clickable {
                            onAuthorClick(post.authorUid, post.authorName, post.authorPhotoUrl, post.authorBadge)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CommunityAvatar(
                            photoUrl = post.authorPhotoUrl,
                            displayName = post.authorName,
                            isAdmin = post.badge == BadgeType.RED_ADMIN,
                            size = 32.dp
                        )
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        VerifiedBadgeIcon(badgeType = post.badge, size = 15.dp)
                    }
                    Text(
                        text = post.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (post.driveLink.isNotBlank() && CommunityLinkValidator.isValidOptionalDriveLink(post.driveLink)) {
                        OutlinedButton(
                            onClick = { onOpenDriveLink(post.driveLink) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (post.bookTitle.isNotBlank()) {
                                    stringResource(R.string.community_drive_button_title, post.bookTitle)
                                } else {
                                    stringResource(R.string.community_drive_button_fallback)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            HorizontalDivider()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (commentsState) {
                    UiState.Loading -> {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = commentsState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is UiState.Success -> {
                        val allComments = commentsState.data
                        if (allComments.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.community_no_comments),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val topLevel = remember(allComments) {
                                val ids = allComments.map { it.id }.toSet()
                                allComments.filter { it.parentCommentId.isBlank() || it.parentCommentId !in ids }
                            }
                            val repliesByParent = remember(allComments) {
                                allComments
                                    .filter { it.parentCommentId.isNotBlank() }
                                    .groupBy { it.parentCommentId }
                            }

                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(topLevel, key = { it.id }) { comment ->
                                    val replies = repliesByParent[comment.id].orEmpty()
                                    val isExpanded = expandedReplies[comment.id] ?: (replies.size <= 2)

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CommentItemRow(
                                            comment = comment,
                                            isReply = false,
                                            isOwn = comment.authorUid == currentUserUid,
                                            canWrite = currentUserProfile?.isWriteBlocked != true,
                                            isTrustedAdmin = isTrustedAdmin,
                                            onAuthorClick = {
                                                onAuthorClick(
                                                    comment.authorUid,
                                                    comment.authorName,
                                                    comment.authorPhotoUrl,
                                                    comment.authorBadge
                                                )
                                            },
                                            onReplyClick = { replyingTo = comment },
                                            onDeleteOwnClick = { pendingDeleteComment = comment },
                                            onReportClick = { onReportComment(comment) },
                                            onAdminHideClick = { pendingAdminAction = comment to false },
                                            onAdminDeleteClick = { pendingAdminAction = comment to true }
                                        )

                                        if (replies.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(start = 36.dp)
                                                    .clickable { expandedReplies[comment.id] = !isExpanded }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = if (isExpanded) {
                                                        stringResource(R.string.community_replies_hide)
                                                    } else {
                                                        stringResource(R.string.community_replies_show, replies.size)
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            if (isExpanded) {
                                                replies.forEach { reply ->
                                                    CommentItemRow(
                                                        comment = reply,
                                                        isReply = true,
                                                        isOwn = reply.authorUid == currentUserUid,
                                                        canWrite = currentUserProfile?.isWriteBlocked != true,
                                                        isTrustedAdmin = isTrustedAdmin,
                                                        onAuthorClick = {
                                                            onAuthorClick(
                                                                reply.authorUid,
                                                                reply.authorName,
                                                                reply.authorPhotoUrl,
                                                                reply.authorBadge
                                                            )
                                                        },
                                                        onReplyClick = {
                                                            // Reply stays threaded cleanly under the top-level parent
                                                            replyingTo = comment.copy(authorName = reply.authorName)
                                                        },
                                                        onDeleteOwnClick = { pendingDeleteComment = reply },
                                                        onReportClick = { onReportComment(reply) },
                                                        onAdminHideClick = { pendingAdminAction = reply to false },
                                                        onAdminDeleteClick = { pendingAdminAction = reply to true }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (allComments.size >= 40) {
                                    item {
                                        OutlinedButton(
                                            onClick = onLoadMoreComments,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(stringResource(R.string.community_load_more_comments))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            if (currentUserProfile?.isWriteBlocked == true) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.community_restricted_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    replyingTo?.let { target ->
                        InputChip(
                            selected = true,
                            onClick = { replyingTo = null },
                            label = {
                                Text(
                                    text = stringResource(R.string.community_replying_to, target.authorName),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { if (it.length <= 1000) commentText = it },
                            placeholder = { Text(stringResource(R.string.community_comment_hint)) },
                            maxLines = 3,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("community_comment_input")
                        )
                        IconButton(
                            onClick = {
                                val target = replyingTo
                                onSendComment(
                                    commentText,
                                    target?.id.orEmpty(),
                                    target?.authorName.orEmpty()
                                )
                                commentText = ""
                                replyingTo = null
                            },
                            enabled = commentText.isNotBlank() && !isSubmitting,
                            modifier = Modifier.testTag("community_send_comment_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.community_send_comment),
                                tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteComment?.let { comment ->
        AlertDialog(
            onDismissRequest = { pendingDeleteComment = null },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.community_delete_comment_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteOwnComment(comment.id)
                        pendingDeleteComment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteComment = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingAdminAction?.let { (comment, isDelete) ->
        AlertDialog(
            onDismissRequest = { pendingAdminAction = null },
            title = { Text(stringResource(R.string.community_moderation_confirm_title)) },
            text = {
                Text(
                    if (isDelete) stringResource(R.string.community_delete_comment_admin)
                    else stringResource(R.string.community_hide_comment)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDelete) onAdminDeleteComment(comment) else onAdminHideComment(comment)
                        pendingAdminAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAdminAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CommentItemRow(
    comment: CommunityComment,
    isReply: Boolean,
    isOwn: Boolean,
    canWrite: Boolean,
    isTrustedAdmin: Boolean,
    onAuthorClick: () -> Unit,
    onReplyClick: () -> Unit,
    onDeleteOwnClick: () -> Unit,
    onReportClick: () -> Unit,
    onAdminHideClick: () -> Unit,
    onAdminDeleteClick: () -> Unit
) {
    val dateLabel = remember(comment.createdAt) {
        comment.createdAt?.toDate()?.let {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it)
        }.orEmpty()
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isReply) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isReply) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 28.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CommunityAvatar(
                        photoUrl = comment.authorPhotoUrl,
                        displayName = comment.authorName,
                        isAdmin = comment.badge == BadgeType.RED_ADMIN,
                        size = 28.dp
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = comment.authorName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            VerifiedBadgeIcon(badgeType = comment.badge, size = 14.dp)
                        }
                        if (dateLabel.isNotEmpty()) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canWrite) {
                        IconButton(onClick = onReplyClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Reply,
                                contentDescription = stringResource(R.string.community_reply_action),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (isOwn) {
                        IconButton(onClick = onDeleteOwnClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = onReportClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.Flag,
                                contentDescription = stringResource(R.string.community_report_comment),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    if (isTrustedAdmin && !isOwn) {
                        IconButton(onClick = onAdminHideClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.VisibilityOff,
                                contentDescription = stringResource(R.string.community_hide_comment),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(onClick = onAdminDeleteClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.community_delete_comment_admin),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            if (comment.replyToAuthorName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.community_replying_to, comment.replyToAuthorName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
