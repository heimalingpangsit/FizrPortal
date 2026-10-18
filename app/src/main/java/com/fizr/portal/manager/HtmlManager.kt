package com.fizr.portal.manager

import android.content.Context
import java.io.File

/**
 * Manages HTML template selection.
 * MODE DEFAULT  → serves assets/index.html bundled with the APK.
 * MODE CUSTOM   → serves files/custom_index.html saved to internal storage.
 */
class HtmlManager(private val context: Context) {

    private val customFile: File
        get() = File(context.filesDir, "custom_index.html")

    /** Returns true when a valid custom HTML file exists in internal storage. */
    fun hasCustomHtml(): Boolean = customFile.exists() && customFile.length() > 0

    /**
     * Read bundled default template from assets.
     * Returns empty string if asset is missing (should never happen in a correct build).
     */
    fun readDefaultHtml(): String = try {
        context.assets.open("index.html").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "<html><body><h1>FIZR PORTAL</h1><p>Default template missing.</p></body></html>"
    }

    /**
     * Read saved custom HTML from internal storage.
     * Falls back to default if file doesn't exist.
     */
    fun readCustomHtml(): String = if (hasCustomHtml()) {
        customFile.readText()
    } else {
        readDefaultHtml()
    }

    /**
     * Persist arbitrary HTML string to internal storage as the custom template.
     * Overwrites any previously saved custom template.
     */
    fun saveCustomHtml(html: String) {
        customFile.writeText(html)
    }

    /** Delete the saved custom template, effectively reverting to default. */
    fun deleteCustomHtml() {
        if (customFile.exists()) customFile.delete()
    }

    /**
     * Return the HTML that should be served right now based on [useCustom] flag.
     */
    fun getActiveHtml(useCustom: Boolean): String =
        if (useCustom && hasCustomHtml()) readCustomHtml() else readDefaultHtml()
}
