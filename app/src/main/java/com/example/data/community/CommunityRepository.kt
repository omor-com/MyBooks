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
                val existing = snap.toObject(CommunityUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: CommunityUser(uid = user.uid)
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
                "restricted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            val created = ref.get().await()
                .toObject(CommunityUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: CommunityUser(
                    id = user.uid,
                    uid = user.uid,
                    displayName = displayName,
                    photoUrl = defaultAvatar,
                    badgeType = badge.firestoreValue
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
                .map { snap ->
                    snap.toObject(CommunityUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
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
            Result.success(snap.toObject(CommunityUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
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
        emitAll(
            db.collection(COL_POSTS)
                .whereEqualTo("visibility", PostVisibility.VISIBLE.firestoreValue)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limitCount)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CommunityPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, COL_POSTS)
                    }
                    throw error
                }
        )
    }

    suspend fun createPost(
        authorProfile: CommunityUser,
        content: String,
        driveLink: String?
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

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        targetSummary: String,
        reason: ReportReason,
        details: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val sanitizedTarget = targetId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val reportId = "${targetType}_${sanitizedTarget}_${uid}".take(128)
        val path = "$COL_REPORTS/$reportId"
        return try {
            val payload = mutableMapOf<String, Any>(
                "reporterUid" to uid,
                "targetType" to targetType,
                "targetId" to targetId.take(128),
                "reason" to reason.firestoreValue,
                "status" to ReportStatus.PENDING.firestoreValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanSummary = targetSummary.trim().take(200)
            if (cleanSummary.isNotEmpty()) payload["targetSummary"] = cleanSummary
            val cleanDetails = details.trim().take(500)
            if (cleanDetails.isNotEmpty()) payload["details"] = cleanDetails

            // Deterministic document ID + create rule prevents duplicate reports for the same target
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
                .map { it.toObjects(CommunityReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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
                .map { it.toObjects(CommunityUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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
                .map { it.toObjects(ModerationAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    suspend fun adminSetUserRestriction(
        targetUid: String,
        restricted: Boolean,
        reason: String
    ): Result<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "$COL_USERS/$targetUid"
        return try {
            db.collection(COL_USERS).document(targetUid).update(
                mapOf(
                    "restricted" to restricted,
                    "restrictionReason" to reason.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                adminUid,
                if (restricted) "restrict_user" else "unrestrict_user",
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
            Result.success(snap.toObject(CommunityPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
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
}
