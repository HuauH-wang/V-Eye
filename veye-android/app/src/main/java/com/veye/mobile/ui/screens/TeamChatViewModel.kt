package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.dto.ChatConversationDto
import com.veye.mobile.cloud.dto.ChatMessageDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TeamChatViewModel(app: Application) : AndroidViewModel(app) {
  private val _conversations = MutableStateFlow<List<ChatConversationDto>>(emptyList())
  private val _messages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
  private val _totalUnread = MutableStateFlow(0)
  private val _loading = MutableStateFlow(false)
  private val _sending = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)
  private var pollJob: Job? = null
  private var activeTeamId: String? = null

  val conversations: StateFlow<List<ChatConversationDto>> = _conversations
  val messages: StateFlow<List<ChatMessageDto>> = _messages
  val totalUnread: StateFlow<Int> = _totalUnread
  val loading: StateFlow<Boolean> = _loading
  val sending: StateFlow<Boolean> = _sending
  val error: StateFlow<String?> = _error

  fun refreshConversations() {
    if (!CloudConfig.isLoggedIn) {
      _conversations.value = emptyList()
      _totalUnread.value = 0
      return
    }
    viewModelScope.launch {
      runCatching {
        val data = AppServices.api().listChatConversations()
        _conversations.value = data.items
        _totalUnread.value = data.total_unread
      }.onFailure {
        _error.value = it.message
      }
    }
  }

  fun openChat(teamId: String) {
    activeTeamId = teamId
    pollJob?.cancel()
    viewModelScope.launch {
      _loading.value = true
      _error.value = null
      runCatching {
        _messages.value = AppServices.api().listChatMessages(teamId).items
        AppServices.api().markChatRead(teamId)
        refreshConversations()
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
    pollJob = viewModelScope.launch {
      while (isActive && activeTeamId == teamId) {
        delay(3000)
        pollNewMessages(teamId)
      }
    }
  }

  fun closeChat() {
    activeTeamId = null
    pollJob?.cancel()
    pollJob = null
  }

  private suspend fun pollNewMessages(teamId: String) {
    val last = _messages.value.lastOrNull()?.created_at ?: return
    runCatching {
      val incoming = AppServices.api().listChatMessages(teamId, since = last).items
      if (incoming.isNotEmpty()) {
        val ids = _messages.value.map { it.id }.toSet()
        _messages.value = (_messages.value + incoming.filter { it.id !in ids })
          .sortedBy { it.created_at }
        AppServices.api().markChatRead(teamId)
        refreshConversations()
      }
    }
  }

  fun sendMessage(teamId: String, text: String) {
    val body = text.trim()
    if (body.isEmpty()) return
    viewModelScope.launch {
      _sending.value = true
      _error.value = null
      runCatching {
        val msg = AppServices.api().sendChatMessage(teamId, com.veye.mobile.cloud.dto.ChatMessageCreateRequest(body))
        _messages.value = _messages.value + msg
        refreshConversations()
      }.onFailure {
        _error.value = it.message
      }
      _sending.value = false
    }
  }

  override fun onCleared() {
    pollJob?.cancel()
    super.onCleared()
  }
}
