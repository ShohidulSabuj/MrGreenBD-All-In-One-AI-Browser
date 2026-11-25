package com.mrgreenbd.browser.engine

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object ShieldAndMediaEngine {

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "googletagmanager.com",
        "google-analytics.com",
        "ads.yahoo.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "propellerads.com",
        "adsterra.com",
        "criteo.com",
        "scorecardresearch.com"
    )

    private val popupAdKeywords = listOf(
        "popunder", "popup", "clickadu", "adcash", "hilltopads", "exoclick"
    )

    fun isAdOrTracker(request: WebResourceRequest): Boolean {
        val host = request.url.host?.lowercase() ?: return false
        return blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }
    }

    fun isSuspiciousPopupOrRedirect(url: String, blockPopups: Boolean): Boolean {
        if (!blockPopups) return false
        val lower = url.lowercase()
        return popupAdKeywords.any { lower.contains(it) }
    }

    fun emptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    fun isStreamableMedia(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp4") ||
            lower.contains(".m3u8") ||
            lower.contains(".webm") ||
            lower.contains("videoplayback")
    }

    const val FORCE_DARK_SCRIPT = "(function(){var id='mrgreenbd-force-dark-style';if(document.getElementById(id))return;var style=document.createElement('style');style.id=id;style.innerHTML='html{filter:invert(90%) hue-rotate(180deg)!important;background:#111!important;}img,video,picture,canvas,iframe{filter:invert(100%) hue-rotate(180deg)!important;}';document.head.appendChild(style);})();"

    const val READER_MODE_SCRIPT = "(function(){var id='mrgreenbd-reader-style';var existing=document.getElementById(id);if(existing){existing.remove();return;}var style=document.createElement('style');style.id=id;style.innerHTML='nav,aside,footer,header,.ad,.ads,.advertisement,.sidebar,.popup,.cookie-banner{display:none!important;}body{max-width:780px!important;margin:auto!important;padding:20px!important;font-size:19px!important;line-height:1.75!important;background:#101815!important;color:#E5E7EB!important;}';document.head.appendChild(style);})();"
}
