package com.veye.mobile.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veye.mobile.AppServices
import com.veye.mobile.cloud.CloudConfig
import com.veye.mobile.cloud.dto.MailItemDto
import com.veye.mobile.cloud.dto.TeamDetailDto
import com.veye.mobile.cloud.dto.TeamSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TeamViewModel(app: Application) : AndroidViewModel(app) {
  private val _teams = MutableStateFlow<List<TeamSummaryDto>>(emptyList())
  private val _selected = MutableStateFlow<TeamDetailDto?>(null)
  private val _mails = MutableStateFlow<List<MailItemDto>>(emptyList())
  private val _unread = MutableStateFlow(0)
  private val _loading = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)

  val teams: StateFlow<List<TeamSummaryDto>> = _teams
  val selected: StateFlow<TeamDetailDto?> = _selected
  val mails: StateFlow<List<MailItemDto>> = _mails
  val unread: StateFlow<Int> = _unread
  val loading: StateFlow<Boolean> = _loading
  val error: StateFlow<String?> = _error

  fun refresh() {
    if (!CloudConfig.isLoggedIn) {
      _teams.value = emptyList()
      _selected.value = null
      _mails.value = emptyList()
      _unread.value = 0
      return
    }
    viewModelScope.launch {
      _loading.value = true
      _error.value = null
      runCatching {
        _teams.value = AppServices.api().listTeams().items
        _mails.value = AppServices.api().listMail().items
        _unread.value = AppServices.api().unreadMailCount().unread_count
        _selected.value?.id?.let { id ->
          _selected.value = AppServices.api().getTeam(id)
        }
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }

  fun selectTeam(id: String) {
    viewModelScope.launch {
      _loading.value = true
      runCatching {
        _selected.value = AppServices.api().getTeam(id)
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }

  fun createTeam(name: String, description: String = "") {
    viewModelScope.launch {
      _loading.value = true
      runCatching {
        val team = AppServices.api().createTeam(
          com.veye.mobile.cloud.dto.TeamCreateRequest(name.trim(), description.trim()),
        )
        _selected.value = team
        refresh()
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }

  fun invite(query: String) {
    val teamId = _selected.value?.id ?: return
    viewModelScope.launch {
      runCatching {
        AppServices.api().inviteMember(teamId, com.veye.mobile.cloud.dto.TeamInviteRequest(query.trim()))
        _selected.value = AppServices.api().getTeam(teamId)
      }.onFailure {
        _error.value = it.message
      }
    }
  }

  fun kick(memberId: String) {
    val teamId = _selected.value?.id ?: return
    viewModelScope.launch {
      runCatching {
        AppServices.api().kickMember(teamId, memberId)
        _selected.value = AppServices.api().getTeam(teamId)
        refresh()
      }.onFailure {
        _error.value = it.message
      }
    }
  }

  fun acceptMail(mailId: String) {
    viewModelScope.launch {
      runCatching {
        AppServices.api().acceptMail(mailId)
        refresh()
      }.onFailure {
        _error.value = it.message
      }
    }
  }

  fun declineMail(mailId: String) {
    viewModelScope.launch {
      runCatching {
        AppServices.api().declineMail(mailId)
        refresh()
      }.onFailure {
        _error.value = it.message
      }
    }
  }

  fun markMailRead(mailId: String) {
    viewModelScope.launch {
      runCatching {
        AppServices.api().markMailRead(mailId)
        refresh()
      }.onFailure {
        _error.value = it.message
      }
    }
  }

  fun deleteMail(mailId: String) {
    viewModelScope.launch {
      runCatching {
        AppServices.api().deleteMail(mailId)
        refresh()
      }.onFailure {
        _error.value = it.message
      }
    }
  }

  fun leaveTeam() {
    val teamId = _selected.value?.id ?: return
    viewModelScope.launch {
      _loading.value = true
      runCatching {
        AppServices.api().leaveTeam(teamId)
        _selected.value = null
        refresh()
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }

  fun disbandTeam() {
    val teamId = _selected.value?.id ?: return
    viewModelScope.launch {
      _loading.value = true
      runCatching {
        AppServices.api().disbandTeam(teamId)
        _selected.value = null
        refresh()
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }

  fun uploadTeamAvatar(bytes: ByteArray, filename: String = "avatar.jpg") {
    val teamId = _selected.value?.id ?: return
    viewModelScope.launch {
      _loading.value = true
      runCatching {
        val part = okhttp3.MultipartBody.Part.createFormData(
          name = "avatar",
          filename = filename,
          body = bytes.toRequestBody("image/jpeg".toMediaType()),
        )
        _selected.value = AppServices.api().uploadTeamAvatar(teamId, part)
        refresh()
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }

  fun deleteTeamAvatar() {
    val teamId = _selected.value?.id ?: return
    viewModelScope.launch {
      _loading.value = true
      runCatching {
        _selected.value = AppServices.api().deleteTeamAvatar(teamId)
        refresh()
      }.onFailure {
        _error.value = it.message
      }
      _loading.value = false
    }
  }
}
