package com.example.data.community

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object CommunityConstants {
    const val DEFAULT_USER_AVATAR_URL =
        "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png"
    const val OFFICIAL_ADMIN_AVATAR_URL =
        "https://pockethostbd.github.io/drive-one/picture/mybooks%20communityadminprofilepicture.png"
    const val OFFICIAL_ADMIN_EMAIL = "muhammedomor.dev@gmail.com"
}

enum class BadgeType(val firestoreValue: String) {
    NONE("none"),
    BLUE("blue"),
    RED_ADMIN("red_admin");

    companion object {
        fun fromRaw(raw: String?): BadgeType =
            entries.firstOrNull { it.firestoreValue == raw } ?: NONE
    }
}

enum class AccountStatus(val firestoreValue: String) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    BANNED("banned"),
    DEACTIVATED("deactivated");

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.firestoreValue == raw } ?: ACTIVE
    }
}

enum class PostVisibility(val firestoreValue: String) {
    VISIBLE("visible"),
    HIDDEN("hidden"),
    REMOVED("removed");

    companion object {
        fun fromRaw(raw: String?): PostVisibility =
            entries.firstOrNull { it.firestoreValue == raw } ?: VISIBLE
    }
}

enum class ReportReason(val firestoreValue: String) {
    HARASSMENT("harassment"),
    INSULTS("insults"),
    SPAM("spam"),
    MALICIOUS_LINK("malicious_link"),
    IMPERSONATION("impersonation"),
    HARMFUL_CONTENT("harmful_content"),
    UNLAWFUL_CONTENT("unlawful_content"),
    OTHER("other");

    companion object {
        fun fromRaw(raw: String?): ReportReason =
            entries.firstOrNull { it.firestoreValue == raw } ?: OTHER
    }
}

enum class ReportStatus(val firestoreValue: String) {
    PENDING("pending"),
    REVIEWED("reviewed"),
    ACTION_TAKEN("action_taken"),
    DISMISSED("dismissed");

    companion object {
        fun fromRaw(raw: String?): ReportStatus =
            entries.firstOrNull { it.firestoreValue == raw } ?: PENDING
    }
}

object CommunityLinkValidator {
    private val driveRegex = Regex("^https://(drive|docs)\\.google\\.com/.*$")

    fun isValidOptionalDriveLink(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return true
        return trimmed.length <= 500 && driveRegex.matches(trimmed)
    }

    fun isValidOptionalBookTitle(driveLink: String?, bookTitle: String?): Boolean {
        val cleanLink = driveLink?.trim().orEmpty()
        val cleanTitle = bookTitle?.trim().orEmpty()
        if (cleanLink.isEmpty()) return cleanTitle.length <= 120
        return cleanTitle.isNotEmpty() && cleanTitle.length <= 120
    }
}

@Keep
@IgnoreExtraProperties
data class CommunityUser(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String = CommunityConstants.DEFAULT_USER_AVATAR_URL,
    val bio: String = "",
    val badgeType: String = BadgeType.NONE.firestoreValue,
    val accountStatus: String = AccountStatus.ACTIVE.firestoreValue,
    val restricted: Boolean = false,
    val restrictionReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val badge: BadgeType get() = BadgeType.fromRaw(badgeType)

    val effectiveStatus: AccountStatus
        get() {
            val parsed = AccountStatus.fromRaw(accountStatus)
            return if (parsed != AccountStatus.ACTIVE) parsed
            else if (restricted) AccountStatus.SUSPENDED
            else AccountStatus.ACTIVE
        }

    val isWriteBlocked: Boolean
        get() = restricted || effectiveStatus != AccountStatus.ACTIVE
}

@Keep
@IgnoreExtraProperties
data class CommunityPost(
    @DocumentId val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = CommunityConstants.DEFAULT_USER_AVATAR_URL,
    val authorBadge: String = BadgeType.NONE.firestoreValue,
    val content: String = "",
    val driveLink: String = "",
    val bookTitle: String = "",
    val likeCount: Long = 0L,
    val visibility: String = PostVisibility.VISIBLE.firestoreValue,
    val moderationNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val badge: BadgeType get() = BadgeType.fromRaw(authorBadge)
    val postVisibility: PostVisibility get() = PostVisibility.fromRaw(visibility)
}

@Keep
@IgnoreExtraProperties
data class PostReaction(
    @DocumentId val id: String = "",
    val uid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUserEntry(
    @DocumentId val id: String = "",
    val blockedUid: String = "",
    val displayName: String = "",
    val photoUrl: String = CommunityConstants.DEFAULT_USER_AVATAR_URL,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommunityComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = CommunityConstants.DEFAULT_USER_AVATAR_URL,
    val authorBadge: String = BadgeType.NONE.firestoreValue,
    val parentCommentId: String = "",
    val replyToAuthorName: String = "",
    val content: String = "",
    val visibility: String = PostVisibility.VISIBLE.firestoreValue,
    val moderationNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val badge: BadgeType get() = BadgeType.fromRaw(authorBadge)
    val commentVisibility: PostVisibility get() = PostVisibility.fromRaw(visibility)
}

@Keep
@IgnoreExtraProperties
data class CommunityReport(
    @DocumentId val id: String = "",
    val reporterUid: String = "",
    val targetType: String = "post",
    val targetId: String = "",
    val parentPostId: String = "",
    val targetSummary: String = "",
    val reason: String = ReportReason.OTHER.firestoreValue,
    val details: String = "",
    val status: String = ReportStatus.PENDING.firestoreValue,
    val reviewedBy: String = "",
    val moderationAction: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val reportReason: ReportReason get() = ReportReason.fromRaw(reason)
    val reportStatus: ReportStatus get() = ReportStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class ModerationAuditLog(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
