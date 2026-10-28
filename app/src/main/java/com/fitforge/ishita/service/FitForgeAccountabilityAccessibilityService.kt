package com.fitforge.ishita.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.fitforge.ishita.data.AccountabilityBlockManager
import com.fitforge.ishita.ui.LockOverlayActivity

class FitForgeAccountabilityAccessibilityService : AccessibilityService() {

    private lateinit var blockManager: AccountabilityBlockManager

    // Supported browser packages for URL inspection
    private val browserPackages = setOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.duckduckgo.mobile.android"
    )

    // Domain tracking state
    private var currentDomain: String? = null
    private var domainEnterEpochMs: Long = 0L
    private var lastLockTriggerEpochMs: Long = 0L

    companion object {
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val myClassName = FitForgeAccountabilityAccessibilityService::class.java.name
            if (enabledServices?.any {
                    it.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                    it.resolveInfo?.serviceInfo?.name == myClassName
                } == true) {
                return true
            }

            val enabledSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledSetting)
            val myComponent = ComponentName(context, FitForgeAccountabilityAccessibilityService::class.java).flattenToString()
            val myShortComponent = ComponentName(context, FitForgeAccountabilityAccessibilityService::class.java).flattenToShortString()
            while (colonSplitter.hasNext()) {
                val component = colonSplitter.next()
                if (component.equals(myComponent, ignoreCase = true) || component.equals(myShortComponent, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun createSettingsIntent(): Intent {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        blockManager = AccountabilityBlockManager.get(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return
        if (pkgName == packageName) return // Ignore FitForge itself

        // 1. Check if the entire Application is blocked
        val appBlock = blockManager.isAppBlocked(pkgName)
        if (appBlock != null) {
            triggerLockOverlay(
                targetName = appBlock.displayName,
                isWebsite = false,
                reason = appBlock.reason,
                id = appBlock.id
            )
            return
        }

        // 2. If it's a browser, check if active Website/Domain is blocked & record time
        if (browserPackages.contains(pkgName)) {
            val rootNode = rootInActiveWindow ?: return
            val capturedDomain = findDomainInNode(rootNode)
            if (capturedDomain != null) {
                handleDomainNavigation(capturedDomain)
            }
        } else {
            // User left browser: flush any active domain timer
            flushDomainTime()
        }
    }

    override fun onInterrupt() {
        flushDomainTime()
    }

    override fun onDestroy() {
        super.onDestroy()
        flushDomainTime()
    }

    private fun handleDomainNavigation(newDomain: String) {
        val now = SystemClock.elapsedRealtime()

        if (currentDomain != null && currentDomain != newDomain) {
            val elapsedSec = ((now - domainEnterEpochMs) / 1000).coerceAtLeast(1)
            blockManager.recordDomainUsage(currentDomain!!, elapsedSec)
            currentDomain = newDomain
            domainEnterEpochMs = now
        } else if (currentDomain == null) {
            currentDomain = newDomain
            domainEnterEpochMs = now
        }

        // Check if the domain is blocked
        val domainBlock = blockManager.isDomainBlocked(newDomain)
        if (domainBlock != null) {
            triggerLockOverlay(
                targetName = domainBlock.displayName,
                isWebsite = true,
                reason = domainBlock.reason,
                id = domainBlock.id
            )
        }
    }

    private fun flushDomainTime() {
        val domain = currentDomain ?: return
        val elapsedSec = ((SystemClock.elapsedRealtime() - domainEnterEpochMs) / 1000).coerceAtLeast(1)
        blockManager.recordDomainUsage(domain, elapsedSec)
        currentDomain = null
        domainEnterEpochMs = 0L
    }

    private fun triggerLockOverlay(
        targetName: String,
        isWebsite: Boolean,
        reason: String,
        id: String
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLockTriggerEpochMs < 1200L) return
        lastLockTriggerEpochMs = now

        // Immediately eject user from restricted app to the Home launcher
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (_: Exception) {}

        val lockIntent = Intent(this, LockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(LockOverlayActivity.EXTRA_TARGET_NAME, targetName)
            putExtra(LockOverlayActivity.EXTRA_IS_WEBSITE, isWebsite)
            putExtra(LockOverlayActivity.EXTRA_REASON, reason)
            putExtra(LockOverlayActivity.EXTRA_ID, id)
        }
        startActivity(lockIntent)
    }

    /**
     * Recursively searches for the URL or domain in the browser accessibility tree.
     */
    private fun findDomainInNode(node: AccessibilityNodeInfo): String? {
        val viewId = node.viewIdResourceName
        val text = node.text?.toString()

        // Check if node has ID matching known browser address bars
        if (viewId != null && (
                viewId.contains("url_bar", ignoreCase = true) ||
                viewId.contains("location_bar", ignoreCase = true) ||
                viewId.contains("search_box", ignoreCase = true) ||
                viewId.contains("search_src_text", ignoreCase = true) ||
                viewId.contains("omnibox", ignoreCase = true) ||
                viewId.contains("url_field", ignoreCase = true) ||
                viewId.contains("url_text", ignoreCase = true) ||
                viewId.contains("address_bar", ignoreCase = true) ||
                viewId.contains("search_url", ignoreCase = true) ||
                viewId.contains("toolbar_url", ignoreCase = true)
            )) {
            if (!text.isNullOrBlank()) {
                val domain = AccountabilityBlockManager.extractDomain(text)
                if (domain != null && domain.contains(".")) {
                    return domain
                }
            }
        }

        // Also inspect plain text content if it resembles a valid host
        if (!text.isNullOrBlank() && (text.startsWith("http://") || text.startsWith("https://") || text.contains(".com") || text.contains(".org") || text.contains(".net") || text.contains(".io"))) {
            val domain = AccountabilityBlockManager.extractDomain(text)
            if (domain != null && domain.contains(".")) {
                return domain
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findDomainInNode(child)
            if (found != null) {
                return found
            }
        }
        return null
    }
}
