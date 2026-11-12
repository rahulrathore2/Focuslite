package com.focuslite.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages user preferences and persistent statistics for FocusLite.
 * Thread-safe and provides both synchronous access for [FocusBlockerService]
 * and reactive [StateFlow] streams for Jetpack Compose UI.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Reactive StateFlows for Jetpack Compose UI
    private val _blockShorts = MutableStateFlow(isBlockYouTubeShortsEnabled())
    val blockShorts: StateFlow<Boolean> = _blockShorts.asStateFlow()

    private val _blockReels = MutableStateFlow(isBlockInstagramReelsEnabled())
    val blockReels: StateFlow<Boolean> = _blockReels.asStateFlow()

    private val _shortsBlockedCount = MutableStateFlow(getShortsBlockedCount())
    val shortsBlockedCount: StateFlow<Int> = _shortsBlockedCount.asStateFlow()

    private val _reelsBlockedCount = MutableStateFlow(getReelsBlockedCount())
    val reelsBlockedCount: StateFlow<Int> = _reelsBlockedCount.asStateFlow()

    private val _showToast = MutableStateFlow(isShowToastEnabled())
    val showToast: StateFlow<Boolean> = _showToast.asStateFlow()

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_BLOCK_YOUTUBE_SHORTS -> _blockShorts.value = isBlockYouTubeShortsEnabled()
                KEY_BLOCK_INSTAGRAM_REELS -> _blockReels.value = isBlockInstagramReelsEnabled()
                KEY_SHORTS_BLOCKED_COUNT -> _shortsBlockedCount.value = getShortsBlockedCount()
                KEY_REELS_BLOCKED_COUNT -> _reelsBlockedCount.value = getReelsBlockedCount()
                KEY_SHOW_TOAST -> _showToast.value = isShowToastEnabled()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    // --- YouTube Shorts Toggle ---
    fun isBlockYouTubeShortsEnabled(): Boolean =
        prefs.getBoolean(KEY_BLOCK_YOUTUBE_SHORTS, true)

    fun setBlockYouTubeShortsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_YOUTUBE_SHORTS, enabled).apply()
        _blockShorts.value = enabled
    }

    // --- Instagram Reels Toggle ---
    fun isBlockInstagramReelsEnabled(): Boolean =
        prefs.getBoolean(KEY_BLOCK_INSTAGRAM_REELS, true)

    fun setBlockInstagramReelsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_INSTAGRAM_REELS, enabled).apply()
        _blockReels.value = enabled
    }

    // --- Toast Feedback Toggle ---
    fun isShowToastEnabled(): Boolean =
        prefs.getBoolean(KEY_SHOW_TOAST, true)

    fun setShowToastEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TOAST, enabled).apply()
        _showToast.value = enabled
    }

    // --- Statistics & Metrics ---
    fun getShortsBlockedCount(): Int =
        prefs.getInt(KEY_SHORTS_BLOCKED_COUNT, 0)

    fun incrementShortsBlockedCount() {
        val current = getShortsBlockedCount()
        prefs.edit().putInt(KEY_SHORTS_BLOCKED_COUNT, current + 1).apply()
        _shortsBlockedCount.value = current + 1
    }

    fun getReelsBlockedCount(): Int =
        prefs.getInt(KEY_REELS_BLOCKED_COUNT, 0)

    fun incrementReelsBlockedCount() {
        val current = getReelsBlockedCount()
        prefs.edit().putInt(KEY_REELS_BLOCKED_COUNT, current + 1).apply()
        _reelsBlockedCount.value = current + 1
    }

    fun resetStats() {
        prefs.edit()
            .putInt(KEY_SHORTS_BLOCKED_COUNT, 0)
            .putInt(KEY_REELS_BLOCKED_COUNT, 0)
            .apply()
        _shortsBlockedCount.value = 0
        _reelsBlockedCount.value = 0
    }

    companion object {
        private const val PREFS_NAME = "focuslite_preferences"

        const val KEY_BLOCK_YOUTUBE_SHORTS = "key_block_youtube_shorts"
        const val KEY_BLOCK_INSTAGRAM_REELS = "key_block_instagram_reels"
        const val KEY_SHORTS_BLOCKED_COUNT = "key_shorts_blocked_count"
        const val KEY_REELS_BLOCKED_COUNT = "key_reels_blocked_count"
        const val KEY_SHOW_TOAST = "key_show_toast"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context).also { instance = it }
            }
        }
    }
}
