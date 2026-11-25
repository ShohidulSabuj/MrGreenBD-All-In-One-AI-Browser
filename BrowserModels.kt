package com.mrgreenbd.browser.model

import android.webkit.WebView

enum class AppTab {
    BROWSER, AI_HUB, LIBRARY, DOWNLOADS, PRIVACY
}

enum class SearchEngineOption(val label: String, val searchUrlPrefix: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q=")
}

enum class DnsProfileOption(val label: String, val note: String) {
    CLOUDFLARE("Cloudflare (1.1.1.1)", "Fast privacy DNS & malware protection"),
    NEXTDNS("NextDNS Custom", "Custom threat & tracker blocking profile"),
    ADGUARD("AdGuard DNS", "Network-level ad & tracker filter"),
    SYSTEM("System Default", "Uses device or network default DNS")
}

data class SpeedDialSite(
    val name: String,
    val url: String,
    val badge: String
)

data class BrowserTabItem(
    val id: String,
    val title: String = "Speed Dial",
    val url: String = "about:speeddial",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isPrivate: Boolean = false,
    val readerMode: Boolean = false,
    val detectedMediaUrl: String? = null,
    val webView: WebView? = null
)

data class DownloadItem(
    val id: String,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val progress: Int = 0,
    val status: String = "Queued"
)

data class PrivacyStats(
    val adsBlocked: Int = 0,
    val shieldEnabled: Boolean = true,
    val blockPopupsAndRedirects: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val forceDarkWeb: Boolean = true,
    val customUserScript: String = "",
    val searchEngine: SearchEngineOption = SearchEngineOption.GOOGLE,
    val dnsProfile: DnsProfileOption = DnsProfileOption.CLOUDFLARE,
    val appLockEnabled: Boolean = false,
    val pinCode: String = "1234"
)
