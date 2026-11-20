package com.example.data.community

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CommunityRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val COL_USERS = "community_users"
        const val COL_ADMINS = "admins"
        const val COL_POSTS = "community_posts"
        const val SUBCOL_COMMENTS = "comments"
        const val COL_REPORTS = "community_reports"
        const val COL_AUDIT_LOGS = "moderation_audit_logs"
    }

    private fun requireUser(): FirebaseUser {
        return auth.currentUser
            ?: throw IllegalStateException("Google Sign-In is required to access Community.")
    }

    suspend fun verifyIsTrustedAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            val tokenResult = user.getIdToken(false).await()
            val hasAdminClaim = tokenResult.claims["admin"] == true
            val isVerifiedAdminEmail = user.isEmailVerified &&
                user.email.equals(CommunityConstants.OFFICIAL_ADMIN_EMAIL, ignoreCase = false)
            val hasAdminMarker = try {
                db.collection(COL_ADMINS).document(user.uid).get().await().exists()
            } catch (_: Exception) {
                false
            }
            hasAdminClaim || isVerifiedAdminEmail || hasAdminMarker
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureCurrentUserProfile(): Result<CommunityUser> {
        val user = try {
            requireUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docPath = "$COL_USERS/${user.uid}"
        return try {
            val ref = db.collection(COL_USERS).document(user.uid)
            val snap = ref.get().await()
            if (snap.exists()) {
                val existing = snap.safeToUser() ?: CommunityUser(id = user.uid, uid = user.uid)
                return Result.success(existing)
            }

            val isBootstrappedAdmin = user.isEmailVerified &&
                user.email == CommunityConstants.OFFICIAL_ADMIN_EMAIL
            val badge = if (isBootstrappedAdmin) BadgeType.RED_ADMIN else BadgeType.NONE
            val defaultAvatar = if (isBootstrappedAdmin) {
                CommunityConstants.OFFICIAL_ADMIN_AVATAR_URL
            } else {
                CommunityConstants.DEFAULT_USER_AVATAR_URL
            }
            val displayName = user.displayName?.trim()?.take(60)?.ifBlank { "MyBooks Reader" }
                ?: "MyBooks Reader"

            val payload = mapOf(
                "uid" to user.uid,
                "displayName" to displayName,
                "photoUrl" to defaultAvatar,
                "bio" to "",
                "badgeType" to badge.firestoreValue,
                "accountStatus" to AccountStatus.ACTIVE.firestoreValue,
                "restricted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            val created = ref.get().await().safeToUser()
                ?: CommunityUser(
                    id = user.uid,
                    uid = user.uid,
                    displayName = displayName,
                    photoUrl = defaultAvatar,
                    badgeType = badge.firestoreValue,
                    accountStatus = AccountStatus.ACTIVE.firestoreValue
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<CommunityUser?> = flow {
        requireUser()
        val path = "$COL_USERS/$userId"
        emitAll(
            db.collection(COL_USERS).document(userId)
                .snapshots()
                .map { snap -> snap.safeToUser() }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserProfile(userId: String): Result<CommunityUser?> {
        val path = "$COL_USERS/$userId"
        return try {
            requireUser()
            val snap = db.collection(COL_USERS).document(userId).get().await()
            Result.success(snap.safeToUser())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun updateOwnProfile(displayName: String, bio: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_USERS/$uid"
        return try {
            val cleanName = displayName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty" }
            val cleanBio = bio.trim().take(300)
            db.collection(COL_USERS).document(uid)
                .update(
                    mapOf(
                        "displayName" to cleanName,
                        "bio" to cleanBio,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeVisibleFeed(limitCount: Long = 25L): Flow<List<CommunityPost>> = flow {
        val uid = requireUser().uid
        val primaryQuery = db.collection(COL_POSTS)
            .whereEqualTo("visibility", PostVisibility.VISIBLE.firestoreValue)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limitCount)

        emitAll(
            primaryQuery.snapshots()
                .map { snapshot -> snapshot.safeToPosts() }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (fsError?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        val fallbackQuery = db.collection(COL_POSTS)
                            .whereEqualTo("visibility", PostVisibility.VISIBLE.firestoreValue)
                            .limit((limitCount * 2L).coerceAtMost(100L))
                        emitAll(
                            fallbackQuery.snapshots()
                                .map { snap ->
                                    snap.safeToPosts()
                                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                                        .take(limitCount.toInt())
                                }
                                .catch { fallbackErr ->
                                    val fbFsErr = generateSequence(fallbackErr) { it.cause }
                                        .filterIsInstance<FirebaseFirestoreException>()
                                        .firstOrNull()
                                    if (fbFsErr == null && fallbackErr is CancellationException) throw fallbackErr
                                    val toLog = fbFsErr ?: (fallbackErr as? Exception)
                                    if (toLog != null && auth.currentUser?.uid == uid) {
                                        handleFirestoreError(toLog, OperationType.LIST, COL_POSTS)
                                    }
                                    throw fallbackErr
                                }
                        )
                    } else {
                        val toLog = fsError ?: (error as? Exception)
                        if (toLog != null && auth.currentUser?.uid == uid) {
                            handleFirestoreError(toLog, OperationType.LIST, COL_POSTS)
                        }
                        throw error
                    }
                }
        )
    }

    suspend fun getVisiblePostsByUser(userId: String, limitCount: Long = 20L): Result<List<CommunityPost>> {
        return try {
            requireUser()
            val primary = db.collection(COL_POSTS)
                .whereEqualTo("authorUid", userId)
                .whereEqualTo("visibility", PostVisibility.VISIBLE.firestoreValue)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limitCount)
            val docs = try {
                primary.get().await().safeToPosts()
            } catch (e: Exception) {
                val fsErr = generateSequence<Throwable>(e) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    db.collection(COL_POSTS)
                        .whereEqualTo("authorUid", userId)
                        .whereEqualTo("visibility", PostVisibility.VISIBLE.firestoreValue)
                        .limit(50L)
                        .get()
                        .await()
                        .safeToPosts()
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                        .take(limitCount.toInt())
                } else {
                    throw e
                }
            }
            Result.success(docs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COL_POSTS)
            Result.failure(e)
        }
    }

    suspend fun createPost(
        authorProfile: CommunityUser,
        content: String,
        driveLink: String?,
        bookTitle: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val postId = UUID.randomUUID().toString().replace("-", "_")
        val path = "$COL_POSTS/$postId"
        return try {
            val cleanContent = content.trim()
            require(cleanContent.isNotEmpty() && cleanContent.length <= 2000) {
                "Post content must be between 1 and 2000 characters"
            }
            val cleanDriveLink = driveLink?.trim().orEmpty()
            require(CommunityLinkValidator.isValidOptionalDriveLink(cleanDriveLink)) {
                "Invalid Google Drive link"
            }
            val cleanBookTitle = bookTitle?.trim().orEmpty().take(120)
            require(CommunityLinkValidator.isValidOptionalBookTitle(cleanDriveLink, cleanBookTitle)) {
                "Book title is required when attaching a Google Drive link"
            }

            val payload = mutableMapOf<String, Any>(
                "authorUid" to uid,
                "authorName" to authorProfile.displayName.take(60).ifBlank { "MyBooks Reader" },
                "authorPhotoUrl" to authorProfile.photoUrl.ifBlank { CommunityConstants.DEFAULT_USER_AVATAR_URL },
                "authorBadge" to authorProfile.badge.firestoreValue,
                "content" to cleanContent,
                "visibility" to PostVisibility.VISIBLE.firestoreValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanDriveLink.isNotEmpty()) {
                payload["driveLink"] = cleanDriveLink
                payload["bookTitle"] = cleanBookTitle
            }

            db.collection(COL_POSTS).document(postId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editOwnPost(
        postId: String,
        content: String,
        driveLink: String?,
        bookTitle: String?
    ): Result<Unit> {
        val path = "$COL_POSTS/$postId"
        return try {
            requireUser()
            val cleanContent = content.trim()
            require(cleanContent.isNotEmpty() && cleanContent.length <= 2000) {
                "Post content must be between 1 and 2000 characters"
            }
            val cleanDriveLink = driveLink?.trim().orEmpty()
            require(CommunityLinkValidator.isValidOptionalDriveLink(cleanDriveLink)) {
                "Invalid Google Drive link"
            }
            val cleanBookTitle = bookTitle?.trim().orEmpty().take(120)
            require(CommunityLinkValidator.isValidOptionalBookTitle(cleanDriveLink, cleanBookTitle)) {
                "Book title is required when attaching a Google Drive link"
            }

            db.collection(COL_POSTS).document(postId).update(
                mapOf(
                    "content" to cleanContent,
                    "driveLink" to cleanDriveLink,
                    "bookTitle" to if (cleanDriveLink.isEmpty()) "" else cleanBookTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteOwnPost(postId: String): Result<Unit> {
        val path = "$COL_POSTS/$postId"
        return try {
            requireUser()
            db.collection(COL_POSTS).document(postId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- COMMENTS & REPLIES (Bounded per open post) ---

    fun observeCommentsForPost(postId: String, limitCount: Long = 50L): Flow<List<CommunityComment>> = flow {
        val uid = requireUser().uid
        val path = "$COL_POSTS/$postId/$SUBCOL_COMMENTS"
        val primaryQuery = db.collection(COL_POSTS)
            .document(postId)
            .collection(SUBCOL_COMMENTS)
            .whereEqualTo("visibility", PostVisibility.VISIBLE.firestoreValue)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(limitCount)

        emitAll(
            primaryQuery.snapshots()
                .map { snapshot -> snapshot.safeToComments() }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (fsError?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        val fallbackQuery = db.collection(COL_POSTS)
                            .document(postId)
                            .collection(SUBCOL_COMMENTS)
                            .whereEqualTo("visibility", PostVisibility.VISIBLE.firestoreValue)
                            .limit((limitCount * 2L).coerceAtMost(120L))
                        emitAll(
                            fallbackQuery.snapshots()
                                .map { snap ->
                                    snap.safeToComments()
                                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                                        .take(limitCount.toInt())
                                }
                                .catch { fallbackErr ->
                                    val fbFsErr = generateSequence(fallbackErr) { it.cause }
                                        .filterIsInstance<FirebaseFirestoreException>()
                                        .firstOrNull()
                                    if (fbFsErr == null && fallbackErr is CancellationException) throw fallbackErr
                                    val toLog = fbFsErr ?: (fallbackErr as? Exception)
                                    if (toLog != null && auth.currentUser?.uid == uid) {
                                        handleFirestoreError(toLog, OperationType.LIST, path)
                                    }
                                    throw fallbackErr
                                }
                        )
                    } else {
                        val toLog = fsError ?: (error as? Exception)
                        if (toLog != null && auth.currentUser?.uid == uid) {
                            handleFirestoreError(toLog, OperationType.LIST, path)
                        }
                        throw error
                    }
                }
        )
    }

    suspend fun addComment(
        authorProfile: CommunityUser,
        postId: String,
        content: String,
        parentCommentId: String = "",
        replyToAuthorName: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val commentId = UUID.randomUUID().toString().replace("-", "_")
        val path = "$COL_POSTS/$postId/$SUBCOL_COMMENTS/$commentId"
        return try {
            val cleanContent = content.trim()
            require(cleanContent.isNotEmpty() && cleanContent.length <= 1000) {
                "Comment must be between 1 and 1000 characters"
            }
            val payload = mutableMapOf<String, Any>(
                "postId" to postId,
                "authorUid" to uid,
                "authorName" to authorProfile.displayName.take(60).ifBlank { "MyBooks Reader" },
                "authorPhotoUrl" to authorProfile.photoUrl.ifBlank { CommunityConstants.DEFAULT_USER_AVATAR_URL },
                "authorBadge" to authorProfile.badge.firestoreValue,
                "content" to cleanContent,
                "visibility" to PostVisibility.VISIBLE.firestoreValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanParent = parentCommentId.trim().take(128)
            if (cleanParent.isNotEmpty()) {
                payload["parentCommentId"] = cleanParent
                val cleanReplyTo = replyToAuthorName.trim().take(60)
                if (cleanReplyTo.isNotEmpty()) {
                    payload["replyToAuthorName"] = cleanReplyTo
                }
            }

            db.collection(COL_POSTS)
                .document(postId)
                .collection(SUBCOL_COMMENTS)
                .document(commentId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteOwnComment(postId: String, commentId: String): Result<Unit> {
        val path = "$COL_POSTS/$postId/$SUBCOL_COMMENTS/$commentId"
        return try {
            requireUser()
            db.collection(COL_POSTS)
                .document(postId)
                .collection(SUBCOL_COMMENTS)
                .document(commentId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        targetSummary: String,
        reason: ReportReason,
        details: String,
        parentPostId: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val normalizedType = when (targetType) {
            "user", "profile" -> "profile"
            "comment" -> "comment"
            else -> "post"
        }
        val sanitizedTarget = targetId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val reportId = "${normalizedType}_${sanitizedTarget}_${uid}".take(128)
        val path = "$COL_REPORTS/$reportId"
        return try {
            val payload = mutableMapOf<String, Any>(
                "reporterUid" to uid,
                "targetType" to normalizedType,
                "targetId" to targetId.take(128),
                "reason" to reason.firestoreValue,
                "status" to ReportStatus.PENDING.firestoreValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanParentPost = parentPostId.trim().take(128)
            if (cleanParentPost.isNotEmpty()) payload["parentPostId"] = cleanParentPost
            val cleanSummary = targetSummary.trim().take(200)
            if (cleanSummary.isNotEmpty()) payload["targetSummary"] = cleanSummary
            val cleanDetails = details.trim().take(500)
            if (cleanDetails.isNotEmpty()) payload["details"] = cleanDetails

            val existing = db.collection(COL_REPORTS).document(reportId).get().await()
            if (existing.exists()) {
                return Result.failure(IllegalStateException("DUPLICATE_REPORT"))
            }
            db.collection(COL_REPORTS).document(reportId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- ADMIN MODERATION OPERATIONS (Enforced server-side by firestore.rules) ---

    fun observeAllReportsForAdmin(): Flow<List<CommunityReport>> = flow {
        val uid = requireUser().uid
        emitAll(
            db.collection(COL_REPORTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { it.safeToReports() }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, COL_REPORTS)
                    }
                    throw error
                }
        )
    }

    fun observeAllUsersForAdmin(): Flow<List<CommunityUser>> = flow {
        val uid = requireUser().uid
        emitAll(
            db.collection(COL_USERS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { it.safeToUsers() }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, COL_USERS)
                    }
                    throw error
                }
        )
    }

    fun observeAuditLogsForAdmin(): Flow<List<ModerationAuditLog>> = flow {
        val uid = requireUser().uid
        emitAll(
            db.collection(COL_AUDIT_LOGS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { it.safeToAuditLogs() }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, COL_AUDIT_LOGS)
                    }
                    throw error
                }
        )
    }

    suspend fun adminUpdateReportStatus(
        reportId: String,
        newStatus: ReportStatus,
        actionNote: String
    ): Result<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_REPORTS/$reportId"
        return try {
            db.collection(COL_REPORTS).document(reportId).update(
                mapOf(
                    "status" to newStatus.firestoreValue,
                    "reviewedBy" to adminUid,
                    "moderationAction" to actionNote.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(adminUid, "report_${newStatus.firestoreValue}", "report", reportId, actionNote)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminSetPostVisibility(
        postId: String,
        visibility: PostVisibility,
        note: String
    ): Result<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_POSTS/$postId"
        return try {
            db.collection(COL_POSTS).document(postId).update(
                mapOf(
                    "visibility" to visibility.firestoreValue,
                    "moderationNote" to note.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(adminUid, "post_${visibility.firestoreValue}", "post", postId, note)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminDeletePost(postId: String, note: String): Result<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_POSTS/$postId"
        return try {
            db.collection(COL_POSTS).document(postId).delete().await()
            writeAuditLog(adminUid, "delete_post", "post", postId, note)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun adminSetCommentVisibility(
        postId: String,
        commentId: String,
        visibility: PostVisibility,
        note: String
    ): Result<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_POSTS/$postId/$SUBCOL_COMMENTS/$commentId"
        return try {
            db.collection(COL_POSTS)
                .document(postId)
                .collection(SUBCOL_COMMENTS)
                .document(commentId)
                .update(
                    mapOf(
                        "visibility" to visibility.firestoreValue,
                        "moderationNote" to note.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            writeAuditLog(adminUid, "comment_${visibility.firestoreValue}", "comment", commentId, note)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteComment(postId: String, commentId: String, note: String): Result<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_POSTS/$postId/$SUBCOL_COMMENTS/$commentId"
        return try {
            db.collection(COL_POSTS)
                .document(postId)
                .collection(SUBCOL_COMMENTS)
                .document(commentId)
                .delete()
                .await()
            writeAuditLog(adminUid, "delete_comment", "comment", commentId, note)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun adminSetUserBlueBadge(targetUid: String, grantBlue: Boolean): Result<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_USERS/$targetUid"
        return try {
            val badge = if (grantBlue) BadgeType.BLUE.firestoreValue else BadgeType.NONE.firestoreValue
            db.collection(COL_USERS).document(targetUid).update(
                mapOf(
                    "badgeType" to badge,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                adminUid,
                if (grantBlue) "grant_blue_badge" else "revoke_blue_badge",
                "profile",
                targetUid,
                ""
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminSetUserAccountStatus(
        targetUid: String,
        status: AccountStatus,
        reason: String
    ): Result<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_USERS/$targetUid"
        return try {
            val isRestricted = status != AccountStatus.ACTIVE
            db.collection(COL_USERS).document(targetUid).update(
                mapOf(
                    "accountStatus" to status.firestoreValue,
                    "restricted" to isRestricted,
                    "restrictionReason" to reason.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                adminUid,
                "set_status_${status.firestoreValue}",
                "profile",
                targetUid,
                reason
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun getPostByIdForAdmin(postId: String): Result<CommunityPost?> {
        val path = "$COL_POSTS/$postId"
        return try {
            requireUser()
            val snap = db.collection(COL_POSTS).document(postId).get().await()
            Result.success(snap.safeToPost())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun getCommentByIdForAdmin(postId: String, commentId: String): Result<CommunityComment?> {
        val path = "$COL_POSTS/$postId/$SUBCOL_COMMENTS/$commentId"
        return try {
            requireUser()
            val snap = db.collection(COL_POSTS)
                .document(postId)
                .collection(SUBCOL_COMMENTS)
                .document(commentId)
                .get()
                .await()
            Result.success(snap.safeToComment())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    private suspend fun writeAuditLog(
        adminUid: String,
        action: String,
        targetType: String,
        targetId: String,
        note: String
    ) {
        val logId = UUID.randomUUID().toString().replace("-", "_")
        val payload = mutableMapOf<String, Any>(
            "adminUid" to adminUid,
            "action" to action.take(80),
            "targetType" to targetType,
            "targetId" to targetId.take(128),
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (note.isNotBlank()) payload["note"] = note.take(300)
        db.collection(COL_AUDIT_LOGS).document(logId).set(payload).await()
    }

    private fun DocumentSnapshot.safeToUser(): CommunityUser? {
        if (!exists()) return null
        return try {
            toObject(CommunityUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.let { user ->
                    val resolvedUid = user.uid.ifBlank { id }
                    user.copy(
                        id = user.id.ifBlank { resolvedUid },
                        uid = resolvedUid,
                        displayName = user.displayName.ifBlank { "MyBooks Reader" },
                        photoUrl = user.photoUrl.ifBlank { CommunityConstants.DEFAULT_USER_AVATAR_URL },
                        accountStatus = user.accountStatus.ifBlank { AccountStatus.ACTIVE.firestoreValue }
                    )
                }
        } catch (_: Exception) {
            CommunityUser(
                id = id,
                uid = getString("uid")?.takeIf { it.isNotBlank() } ?: id,
                displayName = getString("displayName")?.takeIf { it.isNotBlank() } ?: "MyBooks Reader",
                photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() } ?: CommunityConstants.DEFAULT_USER_AVATAR_URL,
                bio = getString("bio").orEmpty(),
                badgeType = getString("badgeType") ?: BadgeType.NONE.firestoreValue,
                accountStatus = getString("accountStatus") ?: AccountStatus.ACTIVE.firestoreValue,
                restricted = (get("restricted") as? Boolean) ?: false,
                restrictionReason = getString("restrictionReason").orEmpty()
            )
        }
    }

    private fun DocumentSnapshot.safeToPost(): CommunityPost? {
        if (!exists()) return null
        return try {
            toObject(CommunityPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.let { post ->
                    post.copy(
                        id = post.id.ifBlank { id },
                        authorName = post.authorName.ifBlank { "MyBooks Reader" },
                        authorPhotoUrl = post.authorPhotoUrl.ifBlank { CommunityConstants.DEFAULT_USER_AVATAR_URL }
                    )
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.safeToComment(): CommunityComment? {
        if (!exists()) return null
        return try {
            toObject(CommunityComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.let { comment ->
                    comment.copy(
                        id = comment.id.ifBlank { id },
                        authorName = comment.authorName.ifBlank { "MyBooks Reader" },
                        authorPhotoUrl = comment.authorPhotoUrl.ifBlank { CommunityConstants.DEFAULT_USER_AVATAR_URL }
                    )
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun QuerySnapshot.safeToPosts(): List<CommunityPost> =
        documents.mapNotNull { it.safeToPost() }

    private fun QuerySnapshot.safeToComments(): List<CommunityComment> =
        documents.mapNotNull { it.safeToComment() }

    private fun QuerySnapshot.safeToUsers(): List<CommunityUser> =
        documents.mapNotNull { it.safeToUser() }

    private fun QuerySnapshot.safeToReports(): List<CommunityReport> =
        documents.mapNotNull { doc ->
            try {
                doc.toObject(CommunityReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.let { report -> report.copy(id = report.id.ifBlank { doc.id }) }
            } catch (_: Exception) {
                null
            }
        }

    private fun QuerySnapshot.safeToAuditLogs(): List<ModerationAuditLog> =
        documents.mapNotNull { doc ->
            try {
                doc.toObject(ModerationAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.let { log -> log.copy(id = log.id.ifBlank { doc.id }) }
            } catch (_: Exception) {
                null
            }
        }
}
