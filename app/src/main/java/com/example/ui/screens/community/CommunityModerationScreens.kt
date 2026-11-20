package com.example.ui.screens.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.community.BadgeType
import com.example.data.community.CommunityPost
import com.example.data.community.CommunityReport
import com.example.data.community.CommunityUser
import com.example.data.community.ModerationAuditLog
import com.example.data.community.PostVisibility
import com.example.data.community.ReportReason
import com.example.data.community.ReportStatus
import com.example.ui.components.CommunityAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch

@Composable
fun MemberProfileDialog(
    user: CommunityUser,
    isOwnProfile: Boolean,
    onDismiss: () -> Unit,
    onSaveOwnProfile: (String, String) -> Unit,
    onReportProfile: () -> Unit
) {
    var displayName by remember(user) { mutableStateOf(user.displayName) }
    var bio by remember(user) { mutableStateOf(user.bio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CommunityAvatar(
                    photoUrl = user.photoUrl,
                    displayName = user.displayName,
                    isAdmin = user.badge == BadgeType.RED_ADMIN,
                    size = 48.dp
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        VerifiedBadgeIcon(badgeType = user.badge, size = 18.dp)
                    }
                    Text(
                        text = stringResource(R.string.community_profile_uid, user.uid.take(12)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isOwnProfile) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= 60) displayName = it },
                        label = { Text(stringResource(R.string.library_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 240) bio = it },
                        label = { Text(stringResource(R.string.library_field_description)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = user.bio.ifBlank { "MyBooks Community Member" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (isOwnProfile) {
                Button(
                    onClick = { onSaveOwnProfile(displayName, bio) },
                    enabled = displayName.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
        dismissButton = {
            if (isOwnProfile) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            } else {
                TextButton(onClick = onReportProfile) {
                    Text(stringResource(R.string.community_report_profile), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
fun ReportTargetDialog(
    targetType: String,
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String) -> Unit
) {
    var selectedReason by remember { mutableStateOf(ReportReason.SPAM) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (targetType == "user") R.string.community_report_profile else R.string.community_report_post
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason })
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(reasonLabelRes(reason)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 500) details = it },
                    label = { Text(stringResource(R.string.community_report_details_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun reasonLabelRes(reason: ReportReason): Int = when (reason) {
    ReportReason.HARASSMENT -> R.string.reason_harassment
    ReportReason.INSULTS -> R.string.reason_insults
    ReportReason.SPAM -> R.string.reason_spam
    ReportReason.MALICIOUS_LINK -> R.string.reason_malicious_link
    ReportReason.IMPERSONATION -> R.string.reason_impersonation
    ReportReason.HARMFUL_CONTENT -> R.string.reason_harmful_content
    ReportReason.UNLAWFUL_CONTENT -> R.string.reason_unlawful_content
    ReportReason.OTHER -> R.string.reason_other
}

@Composable
fun CommunityRulesDialog(
    isBangla: Boolean,
    onDismiss: () -> Unit
) {
    val rulesList = if (isBangla) {
        listOf(
            "১. কোনো ব্যক্তি বা সদস্যকে হয়রানি, কটূক্তি বা অপমান করা যাবে না।",
            "২. স্প্যাম, প্রতারণামূলক প্রচারণা বা সন্দেহজনক/ক্ষতিকর লিংক পোস্ট করা নিষিদ্ধ।",
            "৩. অন্য কারও বা অফিশিয়াল অ্যাডমিনের পরিচয় নকল করা যাবে না।",
            "৪. শুধুমাত্র বৈধ ও নিরাপদ Google Drive লিংক শেয়ার করুন; ফাইল অ্যাক্সেস শেয়ারকারীর পারমিশনের ওপর নির্ভরশীল।",
            "৫. আপত্তিকর পোস্ট বা প্রোফাইল রিপোর্ট করলে অ্যাডমিন রিভিউ করে পোস্ট সরানো বা অ্যাকাউন্ট সীমিত করতে পারবেন।"
        )
    } else {
        listOf(
            "1. No harassment, personal insults, bullying, or mocking of any member.",
            "2. No spam, deceptive promotion, malware, or suspicious external links.",
            "3. No impersonation of other members or the official MyBooks administrator.",
            "4. Only share safe, lawful Google Drive links; file availability depends on the owner's sharing settings.",
            "5. Reported posts and profiles are reviewed by the administrator; violations may result in removal or account restriction."
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.community_rules_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.community_rules_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                rulesList.forEach { rule ->
                    Text(text = rule, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminModerationModal(
    viewModel: CommunityViewModel,
    onDismiss: () -> Unit
) {
    val reportsState by viewModel.adminReportsState.collectAsStateWithLifecycle()
    val usersState by viewModel.adminUsersState.collectAsStateWithLifecycle()
    val auditState by viewModel.adminAuditLogsState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var inspectedPost by remember { mutableStateOf<CommunityPost?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp)
        ) {
            Text(
                text = stringResource(R.string.community_admin_panel),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.community_admin_reports)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.community_admin_users)) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.community_admin_audit)) })
            }

            when (selectedTab) {
                0 -> {
                    val list = (reportsState as? UiState.Success<List<CommunityReport>>)?.data.orEmpty()
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(list, key = { index, report -> report.id.ifBlank { "report_$index" } }) { _, report ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${report.targetType.uppercase()} • ${report.reason} • ${report.status}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (report.targetSummary.isNotBlank()) {
                                        Text(report.targetSummary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (report.details.isNotBlank()) {
                                        Text(report.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (report.targetType == "post") {
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    inspectedPost = viewModel.adminInspectPost(report.targetId)
                                                }
                                            }) {
                                                Text("Inspect")
                                            }
                                            OutlinedButton(onClick = {
                                                viewModel.adminSetPostVisibility(report.targetId, PostVisibility.HIDDEN, "Hidden via report ${report.id}")
                                                viewModel.adminUpdateReport(report.id, ReportStatus.ACTION_TAKEN, "Post hidden")
                                            }) {
                                                Text(stringResource(R.string.community_hide_post))
                                            }
                                        }
                                        TextButton(onClick = {
                                            viewModel.adminUpdateReport(report.id, ReportStatus.REVIEWED, "Reviewed")
                                        }) {
                                            Text(stringResource(R.string.community_mark_reviewed))
                                        }
                                        TextButton(onClick = {
                                            viewModel.adminUpdateReport(report.id, ReportStatus.DISMISSED, "Dismissed")
                                        }) {
                                            Text(stringResource(R.string.community_dismiss_report))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val users = (usersState as? UiState.Success<List<CommunityUser>>)?.data.orEmpty()
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(users, key = { index, member -> member.uid.ifBlank { member.id.ifBlank { "user_$index" } } }) { _, member ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CommunityAvatar(member.photoUrl, member.displayName, member.badge == BadgeType.RED_ADMIN, size = 36.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(member.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                VerifiedBadgeIcon(member.badge, size = 16.dp)
                                            }
                                            Text(member.uid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (member.badge != BadgeType.RED_ADMIN) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val hasBlue = member.badge == BadgeType.BLUE
                                            OutlinedButton(onClick = {
                                                viewModel.adminToggleBlueBadge(member.uid, !hasBlue)
                                            }) {
                                                Text(
                                                    stringResource(
                                                        if (hasBlue) R.string.community_revoke_blue_badge else R.string.community_grant_blue_badge
                                                    )
                                                )
                                            }
                                            OutlinedButton(onClick = {
                                                viewModel.adminToggleUserRestriction(
                                                    member.uid,
                                                    !member.restricted,
                                                    if (member.restricted) "" else "Restricted by moderation"
                                                )
                                            }) {
                                                Text(
                                                    stringResource(
                                                        if (member.restricted) R.string.community_unrestrict_user else R.string.community_restrict_user
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val logs = (auditState as? UiState.Success<List<ModerationAuditLog>>)?.data.orEmpty()
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(logs, key = { index, log -> log.id.ifBlank { "log_$index" } }) { _, log ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("${log.action} → ${log.targetType}:${log.targetId.take(12)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    if (log.note.isNotBlank()) {
                                        Text(log.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedPost?.let { post ->
        AlertDialog(
            onDismissRequest = { inspectedPost = null },
            title = { Text("${post.authorName} (${post.visibility})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(post.content)
                    if (post.driveLink.isNotBlank()) {
                        Text(post.driveLink, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                val hidden = post.postVisibility != PostVisibility.VISIBLE
                Button(onClick = {
                    viewModel.adminSetPostVisibility(
                        post.id,
                        if (hidden) PostVisibility.VISIBLE else PostVisibility.HIDDEN,
                        if (hidden) "Restored by admin" else "Hidden by admin"
                    )
                    inspectedPost = null
                }) {
                    Text(stringResource(if (hidden) R.string.community_restore_post else R.string.community_hide_post))
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedPost = null }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}
