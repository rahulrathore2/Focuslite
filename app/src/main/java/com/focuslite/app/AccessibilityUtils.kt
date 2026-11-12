package com.focuslite.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

/**
 * Utility helper for verifying and navigating to Android Accessibility settings.
 */
object AccessibilityUtils {

    /**
     * Checks if [FocusBlockerService] is currently enabled by the user in Android Settings.
     * Evaluates both [AccessibilityManager] and [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES]
     * for maximum compatibility across OEM ROMs (Samsung, Xiaomi, Pixel, OnePlus).
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, FocusBlockerService::class.java)

        // Method 1: Check enabled services via AccessibilityManager
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (service in enabledServices) {
                val serviceInfo = service.resolveInfo?.serviceInfo
                if (serviceInfo != null &&
                    serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == FocusBlockerService::class.java.name
                ) {
                    return true
                }
            }
        }

        // Method 2: Check Secure Settings string directly
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && (
                        enabledComponent == expectedComponentName ||
                        (enabledComponent.packageName == expectedComponentName.packageName &&
                         enabledComponent.className.endsWith("FocusBlockerService"))
                    )
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Opens Android's system Accessibility Settings page so the user can enable FocusLite.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
