package com.focuslite.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * FocusBlockerService
 *
 * Core accessibility service for FocusLite. Monitors window events in real-time
 * when YouTube or Instagram are active. Detects short-form video UI hierarchies
 * (YouTube Shorts and Instagram Reels) and executes a BACK action to exit the feed
 * while keeping standard videos, feed posts, search, and DMs unaffected.
 */
class FocusBlockerService : AccessibilityService() {

    private lateinit var preferencesManager: PreferencesManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Debounce timestamp to avoid rapid successive back clicks
    private var lastBackActionTimestamp: Long = 0L

    companion object {
        private const val TAG = "FocusBlockerService"

        // Cooldown between consecutive back triggers to prevent closing the entire app
        private const val BACK_ACTION_COOLDOWN_MS = 1000L

        // Target application package names
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        const val PACKAGE_INSTAGRAM = "com.instagram.android"

        // YouTube Shorts view ID signatures
        private val YOUTUBE_SHORTS_VIEW_IDS = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_player",
            "com.google.android.youtube:id/reel_player_page_view",
            "com.google.android.youtube:id/reel_player_fragment_container",
            "com.google.android.youtube:id/reel_recycler_fragment",
            "com.google.android.youtube:id/shorts_container",
            "reel_recycler",
            "shorts_player"
        )

        // YouTube Shorts content description signatures
        private val YOUTUBE_SHORTS_CONTENT_DESCRIPTIONS = listOf(
            "Shorts player",
            "Sound used in this short",
            "Remix this video",
            "Pause short",
            "Play short",
            "Pivot button"
        )

        // Instagram Reels view ID signatures (Instagram internally uses "clips" for Reels)
        private val INSTAGRAM_REELS_VIEW_IDS = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_viewer_fixed_container",
            "com.instagram.android:id/clips_swipe_refresh_layout",
            "com.instagram.android:id/clips_media_component",
            "com.instagram.android:id/reels_tab",
            "clips_viewer_view_pager",
            "clips_video_container"
        )

        // Instagram Reels content description signatures
        private val INSTAGRAM_REELS_CONTENT_DESCRIPTIONS = listOf(
            "Watch more Reels",
            "Original audio - ",
            "Remix of "
        )
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager.getInstance(applicationContext)
        Log.i(TAG, "FocusBlockerService initialized.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "FocusBlockerService connected and active.")

        // Configure dynamic service parameters safely preserving target package filter
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.packageNames = arrayOf(PACKAGE_YOUTUBE, PACKAGE_INSTAGRAM)
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Fast-path package filter
        if (packageName != PACKAGE_YOUTUBE && packageName != PACKAGE_INSTAGRAM) {
            return
        }

        // Check rate limiting / debounce
        val now = System.currentTimeMillis()
        if (now - lastBackActionTimestamp < BACK_ACTION_COOLDOWN_MS) {
            return
        }

        val className = event.className?.toString() ?: ""

        // Fast-path 1: Instant detection via YouTube Shorts Activity name
        if (packageName == PACKAGE_YOUTUBE &&
            preferencesManager.isBlockYouTubeShortsEnabled() &&
            className.contains("ReelWatchActivity", ignoreCase = true)
        ) {
            Log.d(TAG, "Detected YouTube Shorts via ReelWatchActivity!")
            executeBlockAction(
                message = "YouTube Short blocked by FocusLite",
                onIncrement = { preferencesManager.incrementShortsBlockedCount() }
            )
            return
        }

        // Fast-path 2: Instant detection via Instagram Reels viewer activity
        if (packageName == PACKAGE_INSTAGRAM &&
            preferencesManager.isBlockInstagramReelsEnabled() &&
            (className.contains("ClipsViewer", ignoreCase = true) ||
             (className.contains("ModalActivity", ignoreCase = true) && event.text?.any { it.contains("Reels", ignoreCase = true) } == true))
        ) {
            Log.d(TAG, "Detected Instagram Reels via ClipsViewer / ModalActivity!")
            executeBlockAction(
                message = "Instagram Reel blocked by FocusLite",
                onIncrement = { preferencesManager.incrementReelsBlockedCount() }
            )
            return
        }

        // Inspect UI node hierarchy (using rootInActiveWindow with fallback to event.source)
        val rootNode = rootInActiveWindow ?: event.source ?: return

        try {
            when (packageName) {
                PACKAGE_YOUTUBE -> {
                    if (preferencesManager.isBlockYouTubeShortsEnabled()) {
                        if (isYouTubeShortsPresent(rootNode)) {
                            Log.d(TAG, "YouTube Shorts detected in node tree! Executing blocking action...")
                            executeBlockAction(
                                message = "YouTube Short blocked by FocusLite",
                                onIncrement = { preferencesManager.incrementShortsBlockedCount() }
                            )
                        }
                    }
                }

                PACKAGE_INSTAGRAM -> {
                    if (preferencesManager.isBlockInstagramReelsEnabled()) {
                        if (isInstagramReelsPresent(rootNode)) {
                            Log.d(TAG, "Instagram Reel detected in node tree! Executing blocking action...")
                            executeBlockAction(
                                message = "Instagram Reel blocked by FocusLite",
                                onIncrement = { preferencesManager.incrementReelsBlockedCount() }
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while inspecting accessibility node tree", e)
        } finally {
            try {
                @Suppress("DEPRECATION")
                rootNode.recycle()
            } catch (_: Exception) {}
        }
    }

    /**
     * Executes the back navigation action to exit the short-form feed without closing the app.
     */
    private fun executeBlockAction(message: String, onIncrement: () -> Unit) {
        lastBackActionTimestamp = System.currentTimeMillis()

        // Trigger global back press
        val success = performGlobalAction(GLOBAL_ACTION_BACK)

        if (success) {
            onIncrement()

            if (preferencesManager.isShowToastEnabled()) {
                mainHandler.post {
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.w(TAG, "performGlobalAction(GLOBAL_ACTION_BACK) returned false")
        }
    }

    /**
     * Checks if YouTube Shorts feed/player is currently visible in the active window.
     */
    private fun isYouTubeShortsPresent(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. Direct view ID lookups (Fastest path)
        for (viewId in YOUTUBE_SHORTS_VIEW_IDS) {
            val matchingNodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!matchingNodes.isNullOrEmpty()) {
                recycleNodeList(matchingNodes)
                return true
            }
        }

        // 2. Check if the active selected bottom navigation tab is "Shorts"
        val shortsTabs = rootNode.findAccessibilityNodeInfosByText("Shorts")
        if (!shortsTabs.isNullOrEmpty()) {
            for (tabNode in shortsTabs) {
                val parent = tabNode.parent
                val isSelected = tabNode.isSelected || (parent != null && parent.isSelected)
                try {
                    @Suppress("DEPRECATION")
                    parent?.recycle()
                } catch (_: Exception) {}

                if (isSelected) {
                    recycleNodeList(shortsTabs)
                    return true
                }
            }
            recycleNodeList(shortsTabs)
        }

        // 3. Recursive inspection for content descriptions and partial view IDs
        return inspectNodeTreeRecursively(rootNode, maxDepth = 6) { node ->
            val viewId = node.viewIdResourceName
            if (viewId != null) {
                val isShortsId = YOUTUBE_SHORTS_VIEW_IDS.any { viewId.contains(it, ignoreCase = true) }
                if (isShortsId) return@inspectNodeTreeRecursively true
            }

            val contentDesc = node.contentDescription?.toString()
            if (contentDesc != null) {
                val matchesContentDesc = YOUTUBE_SHORTS_CONTENT_DESCRIPTIONS.any {
                    contentDesc.contains(it, ignoreCase = true)
                }
                if (matchesContentDesc) return@inspectNodeTreeRecursively true
            }

            false
        }
    }

    /**
     * Checks if Instagram Reels viewer is currently visible in the active window.
     */
    private fun isInstagramReelsPresent(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. Direct view ID lookups (Instagram clips viewer)
        for (viewId in INSTAGRAM_REELS_VIEW_IDS) {
            val matchingNodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!matchingNodes.isNullOrEmpty()) {
                if (viewId.contains("reels_tab")) {
                    val isTabSelected = matchingNodes.any {
                        val p = it.parent
                        val sel = it.isSelected || (p != null && p.isSelected)
                        try {
                            @Suppress("DEPRECATION")
                            p?.recycle()
                        } catch (_: Exception) {}
                        sel
                    }
                    recycleNodeList(matchingNodes)
                    if (isTabSelected) return true
                } else {
                    recycleNodeList(matchingNodes)
                    return true
                }
            }
        }

        // 2. Check selected bottom navigation bar item for "Reels"
        val reelsTabs = rootNode.findAccessibilityNodeInfosByText("Reels")
        if (!reelsTabs.isNullOrEmpty()) {
            for (tabNode in reelsTabs) {
                val parent = tabNode.parent
                val isSelected = tabNode.isSelected || (parent != null && parent.isSelected)
                try {
                    @Suppress("DEPRECATION")
                    parent?.recycle()
                } catch (_: Exception) {}

                if (isSelected) {
                    recycleNodeList(reelsTabs)
                    return true
                }
            }
            recycleNodeList(reelsTabs)
        }

        // 3. Recursive inspection for clips/reels identifiers
        return inspectNodeTreeRecursively(rootNode, maxDepth = 6) { node ->
            val viewId = node.viewIdResourceName
            if (viewId != null) {
                val isReelsId = INSTAGRAM_REELS_VIEW_IDS.any { viewId.contains(it, ignoreCase = true) }
                if (isReelsId) return@inspectNodeTreeRecursively true
            }

            val contentDesc = node.contentDescription?.toString()
            if (contentDesc != null) {
                if (contentDesc.equals("Reels", ignoreCase = true) && node.isSelected) {
                    return@inspectNodeTreeRecursively true
                }
                val matchesContentDesc = INSTAGRAM_REELS_CONTENT_DESCRIPTIONS.any {
                    contentDesc.contains(it, ignoreCase = true)
                }
                if (matchesContentDesc) return@inspectNodeTreeRecursively true
            }

            false
        }
    }

    /**
     * Traverses the node hierarchy up to [maxDepth] to detect signatures with minimal overhead.
     */
    private fun inspectNodeTreeRecursively(
        node: AccessibilityNodeInfo?,
        currentDepth: Int = 0,
        maxDepth: Int = 6,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (node == null || currentDepth > maxDepth) return false

        if (predicate(node)) {
            return true
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val found = inspectNodeTreeRecursively(child, currentDepth + 1, maxDepth, predicate)
            try {
                @Suppress("DEPRECATION")
                child.recycle()
            } catch (_: Exception) {}
            if (found) return true
        }

        return false
    }

    /**
     * Safely recycles a list of accessibility nodes to prevent memory pressure.
     */
    private fun recycleNodeList(nodes: List<AccessibilityNodeInfo>?) {
        if (nodes.isNullOrEmpty()) return
        for (node in nodes) {
            try {
                @Suppress("DEPRECATION")
                node.recycle()
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusBlockerService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "FocusBlockerService destroyed.")
    }
}
