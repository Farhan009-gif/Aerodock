package com.example.groups

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the Groups feature. Constructed with no arguments (plain
 * `viewModel()` in GroupsRoot) — repositories are created internally and all
 * of them fail soft when Firebase is unavailable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModel : ViewModel() {

    private val authRepo = GroupsAuthRepository()
    private val repo = GroupsRepository()

    val firebaseAvailable: Boolean = GroupsFirebase.isAvailable

    private val _currentUser = MutableStateFlow(authRepo.currentUserSnapshot())
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)

    val groups: StateFlow<List<GroupRoom>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else repo.groupsFlow(user.uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isAdmin: StateFlow<Boolean> = _currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(false) else repo.isAdminFlow(user.uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** stats/global is only observed for admins — its rules deny everyone else. */
    val stats: StateFlow<GroupStats> = isAdmin
        .flatMapLatest { admin ->
            if (admin) repo.statsFlow() else flowOf(GroupStats())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupStats())

    val selectedGroup: StateFlow<GroupRoom?> = combine(groups, _selectedGroupId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<GroupMessage>> = _selectedGroupId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repo.messagesFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        var lastUpsertedUid: String? = null
        viewModelScope.launch {
            authRepo.currentUserFlow.collect { user ->
                _currentUser.value = user
                if (user == null) {
                    lastUpsertedUid = null
                } else if (user.uid != lastUpsertedUid) {
                    lastUpsertedUid = user.uid
                    repo.upsertCurrentUser(user)
                }
            }
        }
    }

    fun signIn(context: Context) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = authRepo.signInWithGoogle(context)
            _busy.value = false
            result.onFailure { error ->
                // A cancelled Google sheet is not an error worth showing.
                if (error !is GetCredentialCancellationException) {
                    _status.value = error.message ?: "Sign-in failed"
                }
            }
        }
    }

    fun signOut() {
        _selectedGroupId.value = null
        authRepo.signOut()
    }

    fun createGroup(name: String, onCreated: (String) -> Unit) {
        val user = _currentUser.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = repo.createGroup(user, name)
            _busy.value = false
            result
                .onSuccess { room -> onCreated(room.id) }
                .onFailure { error -> _status.value = error.message ?: "Could not create group" }
        }
    }

    fun joinGroup(code: String, onJoined: (String) -> Unit) {
        val user = _currentUser.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = repo.joinGroup(user, code)
            _busy.value = false
            result
                .onSuccess { room -> onJoined(room.id) }
                .onFailure { error -> _status.value = error.message ?: "Could not join group" }
        }
    }

    fun openGroup(groupId: String) {
        _selectedGroupId.value = groupId
    }

    fun closeGroup() {
        _selectedGroupId.value = null
    }

    fun sendMessage(text: String) {
        val user = _currentUser.value ?: return
        val groupId = _selectedGroupId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.sendMessage(user, groupId, text) }
                .onFailure { error -> _status.value = error.message ?: "Could not send" }
        }
    }

    fun clearStatus() {
        _status.value = null
    }
}
