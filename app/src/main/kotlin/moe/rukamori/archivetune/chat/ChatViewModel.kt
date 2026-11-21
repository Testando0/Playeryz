package moe.rukamori.archivetune.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ChatUiState(
    val loggedIn: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val registerMode: Boolean = false,
    val conversations: List<ChatConversation> = emptyList(),
    val selected: ChatConversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
internal class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(loggedIn = repository.currentToken() != null))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        if (repository.currentToken() != null) refresh()
        repository.connect { message ->
            val current = _state.value
            if (current.selected != null && current.messages.none { it.id == message.id }) {
                _state.value = current.copy(messages = current.messages + message)
            }
        }
    }

    fun updateUsername(value: String) { _state.value = _state.value.copy(username = value, error = null) }
    fun updateDisplayName(value: String) { _state.value = _state.value.copy(displayName = value, error = null) }
    fun updatePassword(value: String) { _state.value = _state.value.copy(password = value, error = null) }
    fun updateDraft(value: String) { _state.value = _state.value.copy(draft = value) }
    fun toggleRegister() { _state.value = _state.value.copy(registerMode = !_state.value.registerMode, error = null) }

    fun authenticate() {
        val snapshot = _state.value
        if (snapshot.username.isBlank() || snapshot.password.length < 8) {
            _state.value = snapshot.copy(error = "Informe usuário e uma senha com pelo menos 8 caracteres")
            return
        }
        viewModelScope.launch {
            _state.value = snapshot.copy(loading = true, error = null)
            runCatching {
                if (snapshot.registerMode) repository.register(snapshot.username, snapshot.displayName.ifBlank { snapshot.username }, snapshot.password)
                else repository.login(snapshot.username, snapshot.password)
            }.onSuccess { user ->
                _state.value = ChatUiState(loggedIn = true, username = user.username, displayName = user.displayName)
                repository.connect(::onRealtimeMessage)
                refresh()
            }.onFailure { error ->
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Não foi possível conectar ao chat")
            }
        }
    }

    fun logout() { repository.clearToken(); _state.value = ChatUiState() }

    fun refresh() {
        if (repository.currentToken() == null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.conversations() }.onSuccess { conversations ->
                val selected = _state.value.selected?.let { old -> conversations.firstOrNull { it.id == old.id } } ?: conversations.firstOrNull()
                _state.value = _state.value.copy(loading = false, conversations = conversations, selected = selected)
                selected?.let(::selectConversation)
            }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Não foi possível carregar as conversas") }
        }
    }

    fun selectConversation(conversation: ChatConversation) {
        _state.value = _state.value.copy(selected = conversation, loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.messages(conversation.id) }.onSuccess { messages ->
                _state.value = _state.value.copy(messages = messages, loading = false)
            }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Não foi possível carregar o histórico") }
        }
    }

    fun send() {
        val snapshot = _state.value
        val conversation = snapshot.selected ?: return
        val body = snapshot.draft.trim()
        if (body.isEmpty()) return
        _state.value = snapshot.copy(draft = "")
        viewModelScope.launch {
            runCatching { repository.sendMessage(conversation.id, body) }.onSuccess { message ->
                if (_state.value.messages.none { it.id == message.id }) _state.value = _state.value.copy(messages = _state.value.messages + message)
            }.onFailure { _state.value = _state.value.copy(draft = body, error = it.message ?: "Não foi possível enviar") }
        }
    }

    private fun onRealtimeMessage(message: ChatMessage) {
        val current = _state.value
        if (current.messages.none { it.id == message.id }) _state.value = current.copy(messages = current.messages + message)
    }
}
