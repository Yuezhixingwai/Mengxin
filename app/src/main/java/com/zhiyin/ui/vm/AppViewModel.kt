package com.zhiyin.ui.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhiyin.data.AccountApi
import com.zhiyin.data.AppSession
import com.zhiyin.data.EngineBus
import com.zhiyin.data.SettingsRepo
import com.zhiyin.logic.chat.ChatEngine
import com.zhiyin.logic.data.FriendManager
import com.zhiyin.logic.data.MsgRepo
import com.zhiyin.logic.data.PersonaManager
import com.zhiyin.service.MessageSyncScheduler
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app), ChatEngine.Listener {

    val loggedIn get() = AppSession.loggedIn

    val darkMode get() = SettingsRepo.darkMode

    val notifyEnabled get() = SettingsRepo.notifyEnabled

    val themeId get() = SettingsRepo.themeId

    fun setTheme(id: String) {
        SettingsRepo.setThemeId(getApplication(), id)
    }

    var friends by mutableStateOf<List<FriendManager.Friend>>(emptyList())
        private set

    var friendCount by mutableStateOf(0)
        private set

    var userInfo by mutableStateOf<AccountApi.UserInfo?>(null)
        private set

    var toastId by mutableLongStateOf(0L)
        private set
    var toastMessage by mutableStateOf<String?>(null)
        private set

    fun showToast(msg: String) {
        toastId += 1
        toastMessage = msg
        viewModelScope.launch {
            kotlinx.coroutines.delay(2400)
            if (toastMessage == msg) toastMessage = null
        }
    }

    init {
        SettingsRepo.loadDarkMode(app)
        SettingsRepo.loadNotifyEnabled(app)
        SettingsRepo.loadThemeId(app)
        EngineBus.register(this)
        if (AppSession.loggedIn.value) {
            loadFriends()
            refreshUser()
            maybeStartNotifyService()
        }
    }

    fun toggleDark() {
        SettingsRepo.setDarkMode(getApplication(), !SettingsRepo.darkMode.value)
    }

    fun loadFriends(onDone: (List<FriendManager.Friend>) -> Unit = {}) {
        val token = AppSession.token()
        if (token.isEmpty()) return
        FriendManager.getAll(token, object : FriendManager.Callback {
            override fun onResult(list: MutableList<FriendManager.Friend>?) {
                friends = list ?: emptyList()
                friendCount = friends.size
                onDone(friends)
            }

            override fun onError(err: String?) {}
        })
    }

    fun refreshUser(onDone: (AccountApi.UserInfo?) -> Unit = {}) {
        viewModelScope.launch {
            val r = AccountApi.getUserInfo()
            userInfo = r.getOrNull()
            onDone(userInfo)
        }
    }

    fun toggleNotify(enabled: Boolean) {
        SettingsRepo.setNotifyEnabled(getApplication(), enabled)
        maybeStartNotifyService()
    }

    fun maybeStartNotifyService() {
        if (AppSession.loggedIn.value && SettingsRepo.notifyEnabled.value) {
            MessageSyncScheduler.start(getApplication())
        } else {
            MessageSyncScheduler.stop(getApplication())
        }
    }

    suspend fun unreadAnnouncement(): AccountApi.Announcement? {
        val a = AccountApi.activeAnnouncement() ?: return null
        if (AccountApi.isAnnouncementRead(getApplication(), a.id)) return null
        return a
    }

    fun markAnnouncementRead(id: Int) = AccountApi.markAnnouncementRead(getApplication(), id)

    fun totalUnread(): Int = MsgRepo.getTotalUnreadCount(getApplication(), friends)

    fun logout() {
        MessageSyncScheduler.stop(getApplication())
        AppSession.logout()
    }

    fun isOfficialPersona(name: String): Boolean =
        PersonaManager.isOfficial(getApplication(), name)

    override fun onMessagesChanged() {}

    override fun onTypingChanged(typing: Boolean) {}

    override fun onNotice(msg: String) {
        showToast(msg)
    }

    override fun onSendingChanged(sending: Boolean) {}
}
