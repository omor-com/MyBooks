package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.community.CommunityLinkValidator
import com.example.data.community.CommunityPost
import com.example.data.community.CommunityReport
import com.example.data.community.CommunityRepository
import com.example.data.community.CommunityUser
import com.example.data.community.ModerationAuditLog
import com.example.data.community.PostVisibility
import com.example.data.community.ReportReason
import com.example.data.community.ReportStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface CommunityEvent {
    data class Message(val text: String) : CommunityEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel(
    private val repository: CommunityRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _pageLimit = MutableStateFlow(25L)
    private val _retryTrigger = MutableStateFlow(0)
    private val _isTrustedAdmin = MutableStateFlow(false)
    val isTrustedAdmin: StateFlow<Boolean> = _isTrustedAdmin.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _events = MutableSharedFlow<CommunityEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val currentUserProfile: StateFlow<CommunityUser?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", fsError ?: error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val feedState: StateFlow<UiState<List<CommunityPost>>> = combine(_pageLimit, _retryTrigger) { limit, _ ->
        limit
    }
        .flatMapLatest { limit ->
            repository.observeVisibleFeed(limit)
                .map<List<CommunityPost>, UiState<List<CommunityPost>>> { UiState.Success(it) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val root = fsError ?: error
                    Log.w(TAG, "Error observing feed", root)
                    emit(UiState.Error(root.localizedMessage ?: root.message ?: "Failed to load community feed"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminReportsState: StateFlow<UiState<List<CommunityReport>>> = _isTrustedAdmin
        .flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(UiState.Success(emptyList()))
            else repository.observeAllReportsForAdmin()
                .map<List<CommunityReport>, UiState<List<CommunityReport>>> { UiState.Success(it) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(UiState.Error((fsError ?: error).message ?: "Failed to load reports"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminUsersState: StateFlow<UiState<List<CommunityUser>>> = _isTrustedAdmin
        .flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(UiState.Success(emptyList()))
            else repository.observeAllUsersForAdmin()
                .map<List<CommunityUser>, UiState<List<CommunityUser>>> { UiState.Success(it) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(UiState.Error((fsError ?: error).message ?: "Failed to load users"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminAuditLogsState: StateFlow<UiState<List<ModerationAuditLog>>> = _isTrustedAdmin
        .flatMapLatest { isAdmin ->
            if (!isAdmin) flowOf(UiState.Success(emptyList()))
            else repository.observeAuditLogsForAdmin()
                .map<List<ModerationAuditLog>, UiState<List<ModerationAuditLog>>> { UiState.Success(it) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(UiState.Error((fsError ?: error).message ?: "Failed to load audit logs"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        refreshProfileAndRole(triggerFeedReload = false)
    }

    fun loadMorePosts() {
        _pageLimit.value = (_pageLimit.value + 25L).coerceAtMost(200L)
    }

    fun refreshProfileAndRole(triggerFeedReload: Boolean = true) {
        if (triggerFeedReload) {
            _retryTrigger.value += 1
        }
        viewModelScope.launch {
            try {
                repository.ensureCurrentUserProfile()
                _isTrustedAdmin.value = repository.verifyIsTrustedAdmin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to refresh Community profile/role", e)
            }
        }
    }

    fun createPost(content: String, driveLink: String) {
        val profile = currentUserProfile.value ?: return
        if (!CommunityLinkValidator.isValidOptionalDriveLink(driveLink)) {
            viewModelScope.launch {
                _events.emit(CommunityEvent.Message("Invalid Google Drive link format"))
            }
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createPost(profile, content, driveLink)
            _isSubmitting.value = false
            res.onSuccess {
                _events.emit(CommunityEvent.Message("Post published"))
            }.onFailure { err ->
                _events.emit(CommunityEvent.Message(err.localizedMessage ?: "Failed to publish post"))
            }
        }
    }

    fun deleteOwnPost(postId: String) {
        viewModelScope.launch {
            repository.deleteOwnPost(postId)
                .onSuccess { _events.emit(CommunityEvent.Message("Post deleted")) }
                .onFailure { _events.emit(CommunityEvent.Message(it.localizedMessage ?: "Failed to delete post")) }
        }
    }

    fun updateOwnProfile(displayName: String, bio: String) {
        viewModelScope.launch {
            repository.updateOwnProfile(displayName, bio)
                .onSuccess { _events.emit(CommunityEvent.Message("Profile updated")) }
                .onFailure { _events.emit(CommunityEvent.Message(it.localizedMessage ?: "Failed to update profile")) }
        }
    }

    fun submitReport(
        targetType: String,
        targetId: String,
        targetSummary: String,
        reason: ReportReason,
        details: String
    ) {
        viewModelScope.launch {
            repository.submitReport(targetType, targetId, targetSummary, reason, details)
                .onSuccess {
                    _events.emit(CommunityEvent.Message("Report submitted for moderation review."))
                }
                .onFailure { err ->
                    val msg = if (err.message == "DUPLICATE_REPORT") {
                        "You have already reported this item."
                    } else {
                        err.localizedMessage ?: "Failed to submit report"
                    }
                    _events.emit(CommunityEvent.Message(msg))
                }
        }
    }

    suspend fun loadUserProfile(uid: String): CommunityUser? {
        return repository.getUserProfile(uid).getOrNull()
    }

    // --- Admin Actions ---
    fun adminUpdateReport(reportId: String, status: ReportStatus, note: String) {
        viewModelScope.launch {
            repository.adminUpdateReportStatus(reportId, status, note)
                .onSuccess { _events.emit(CommunityEvent.Message("Report updated")) }
                .onFailure { _events.emit(CommunityEvent.Message(it.localizedMessage ?: "Admin action failed")) }
        }
    }

    fun adminSetPostVisibility(postId: String, visibility: PostVisibility, note: String) {
        viewModelScope.launch {
            repository.adminSetPostVisibility(postId, visibility, note)
                .onSuccess { _events.emit(CommunityEvent.Message("Post visibility updated to ${visibility.firestoreValue}")) }
                .onFailure { _events.emit(CommunityEvent.Message(it.localizedMessage ?: "Admin action failed")) }
        }
    }

    fun adminToggleBlueBadge(targetUid: String, grantBlue: Boolean) {
        viewModelScope.launch {
            repository.adminSetUserBlueBadge(targetUid, grantBlue)
                .onSuccess { _events.emit(CommunityEvent.Message("Verification badge updated")) }
                .onFailure { _events.emit(CommunityEvent.Message(it.localizedMessage ?: "Badge update failed")) }
        }
    }

    fun adminToggleUserRestriction(targetUid: String, restrict: Boolean, reason: String) {
        viewModelScope.launch {
            repository.adminSetUserRestriction(targetUid, restrict, reason)
                .onSuccess { _events.emit(CommunityEvent.Message("User restriction updated")) }
                .onFailure { _events.emit(CommunityEvent.Message(it.localizedMessage ?: "Restriction update failed")) }
        }
    }

    suspend fun adminInspectPost(postId: String): CommunityPost? {
        return repository.getPostByIdForAdmin(postId).getOrNull()
    }

    private companion object {
        const val TAG = "CommunityVM"
    }
}
