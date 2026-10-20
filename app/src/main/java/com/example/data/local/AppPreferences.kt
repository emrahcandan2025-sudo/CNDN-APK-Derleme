package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cndn_prefs", Context.MODE_PRIVATE)

    private val _tokenFlow = MutableStateFlow(getGitHubToken())
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    private val _usernameFlow = MutableStateFlow(getGitHubUsername())
    val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

    fun saveGitHubToken(token: String?) {
        val cleanToken = token?.trim()
        prefs.edit().putString(KEY_GITHUB_TOKEN, cleanToken).apply()
        _tokenFlow.value = cleanToken
    }

    fun getGitHubToken(): String? {
        val t = prefs.getString(KEY_GITHUB_TOKEN, null)
        return if (t.isNullOrBlank()) null else t.trim()
    }

    fun saveGitHubUser(username: String?, avatarUrl: String?, name: String?) {
        prefs.edit()
            .putString(KEY_GITHUB_USERNAME, username)
            .putString(KEY_GITHUB_AVATAR, avatarUrl)
            .putString(KEY_GITHUB_NAME, name)
            .putLong(KEY_LAST_CONNECTED, System.currentTimeMillis())
            .apply()
        _usernameFlow.value = username
    }

    fun getGitHubUsername(): String? = prefs.getString(KEY_GITHUB_USERNAME, null)
    fun getGitHubAvatar(): String? = prefs.getString(KEY_GITHUB_AVATAR, null)
    fun getGitHubName(): String? = prefs.getString(KEY_GITHUB_NAME, null)
    fun getLastConnected(): Long = prefs.getLong(KEY_LAST_CONNECTED, 0L)

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_GITHUB_TOKEN)
            .remove(KEY_GITHUB_USERNAME)
            .remove(KEY_GITHUB_AVATAR)
            .remove(KEY_GITHUB_NAME)
            .remove(KEY_LAST_CONNECTED)
            .apply()
        _tokenFlow.value = null
        _usernameFlow.value = null
    }

    /**
     * Safely masks the token for display in the UI (e.g. ghp_1234****abcd)
     */
    fun getMaskedToken(): String {
        val token = getGitHubToken() ?: return "Bağlı Değil"
        if (token.length <= 8) return "****"
        return "${token.take(4)}****${token.takeLast(4)}"
    }

    companion object {
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_GITHUB_USERNAME = "github_username"
        private const val KEY_GITHUB_AVATAR = "github_avatar"
        private const val KEY_GITHUB_NAME = "github_name"
        private const val KEY_LAST_CONNECTED = "last_connected"
    }
}
