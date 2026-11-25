package com.mrgreenbd.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.util.Rational
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mrgreenbd.browser.ai.AiAssistantEngine
import com.mrgreenbd.browser.data.BookmarkEntity
import com.mrgreenbd.browser.data.BrowserDatabase
import com.mrgreenbd.browser.data.HistoryEntity
import com.mrgreenbd.browser.engine.ShieldAndMediaEngine
import com.mrgreenbd.browser.model.AppTab
import com.mrgreenbd.browser.model.BrowserTabItem
import com.mrgreenbd.browser.model.DnsProfileOption
import com.mrgreenbd.browser.model.DownloadItem
import com.mrgreenbd.browser.model.PrivacyStats
import com.mrgreenbd.browser.model.SearchEngineOption
import com.mrgreenbd.browser.model.SpeedDialSite
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mrgreenbd_all_in_one_prefs", Context.MODE_PRIVATE)
        val initialLock = prefs.getBoolean("app_lock_enabled", false)
        val savedPin = prefs.getString("app_pin", "1234") ?: "1234"
        val initialBottomBar = prefs.getBoolean("bottom_address_bar", true)
        val initialForceDark = prefs.getBoolean("force_dark_web", true)
        val initialBlockPopups = prefs.getBoolean("block_popups", true)
        val initialUserScript = prefs.getString("custom_userscript", "") ?: ""
        val initialEngineName = prefs.getString("search_engine", SearchEngineOption.GOOGLE.name)
            ?: SearchEngineOption.GOOGLE.name
        val initialDnsName = prefs.getString("dns_profile", DnsProfileOption.CLOUDFLARE.name)
            ?: DnsProfileOption.CLOUDFLARE.name

        val initialEngine = runCatching { SearchEngineOption.valueOf(initialEngineName) }
            .getOrDefault(SearchEngineOption.GOOGLE)
        val initialDns = runCatching { DnsProfileOption.valueOf(initialDnsName) }
            .getOrDefault(DnsProfileOption.CLOUDFLARE)

        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isAndroidTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isTablet = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

            val darkScheme = darkColorScheme(
                primary = Color(0xFF10B981),
                secondary = Color(0xFF34D399),
                background = Color(0xFF0B1210),
                surface = Color(0xFF121C18),
                surfaceVariant = Color(0xFF1A2822)
            )

            MaterialTheme(colorScheme = darkScheme) {
                var unlocked by remember { mutableStateOf(!initialLock) }

                if (!unlocked) {
                    AppLockGateScreen(
                        savedPin = savedPin,
                        onUnlocked = { unlocked = true },
                        onBiometricRequest = {
                            triggerBiometricAuth(
                                activity = this,
                                onSuccess = { unlocked = true }
                            )
                        }
                    )
                } else {
                    MrGreenBDAllInOneBrowserApp(
                        activity = this,
                        isTablet = isTablet,
                        isTvDevice = isAndroidTv,
                        initialLockEnabled = initialLock,
                        initialPin = savedPin,
                        initialBottomAddressBar = initialBottomBar,
                        initialForceDark = initialForceDark,
                        initialBlockPopups = initialBlockPopups,
                        initialUserScript = initialUserScript,
                        initialSearchEngine = initialEngine,
                        initialDnsProfile = initialDns
                    )
                }
            }
        }
    }

    fun enterPipModeIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun triggerBiometricAuth(activity: FragmentActivity, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MrGreenBD All in One AI+ Browser")
            .setSubtitle("Verify your identity to access private tabs and bookmarks")
            .setNegativeButtonText("Use PIN")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun AppLockGateScreen(
    savedPin: String,
    onUnlocked: () -> Unit,
    onBiometricRequest: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onBiometricRequest()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "MrGreenBD All in One AI+ Browser Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = {
                    pinInput = it
                    errorMsg = null
                },
                label = { Text("Enter 4-Digit PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.widthIn(max = 280.dp)
            )
            errorMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (pinInput == savedPin) {
                        onUnlocked()
                    } else {
                        errorMsg = "Incorrect PIN. Try again."
                    }
                }) {
                    Text("Unlock")
                }
                OutlinedButton(onClick = onBiometricRequest) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Biometric")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MrGreenBDAllInOneBrowserApp(
    activity: MainActivity,
    isTablet: Boolean,
    isTvDevice: Boolean,
    initialLockEnabled: Boolean,
    initialPin: String,
    initialBottomAddressBar: Boolean,
    initialForceDark: Boolean,
    initialBlockPopups: Boolean,
    initialUserScript: String,
    initialSearchEngine: SearchEngineOption,
    initialDnsProfile: DnsProfileOption
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("mrgreenbd_all_in_one_prefs", Context.MODE_PRIVATE) }

    val db = remember { BrowserDatabase.getDatabase(context) }
    val dao = remember { db.browserDao() }
    val bookmarks by dao.getAllBookmarks().collectAsState(initial = emptyList())
    val historyList by dao.getRecentHistory().collectAsState(initial = emptyList())

    var currentNav by remember { mutableStateOf(AppTab.BROWSER) }
    var tvCursorMode by remember { mutableStateOf(isTvDevice) }
    var bottomAddressBar by remember { mutableStateOf(initialBottomAddressBar && !isTablet && !isTvDevice) }

    val defaultSpeedDials = remember {
        listOf(
            SpeedDialSite("Google", "https://www.google.com", "G"),
            SpeedDialSite("YouTube", "https://m.youtube.com", "YT"),
            SpeedDialSite("Wikipedia", "https://bn.wikipedia.org", "W"),
            SpeedDialSite("GitHub", "https://github.com", "GH"),
            SpeedDialSite("DuckDuckGo", "https://duckduckgo.com", "DDG"),
            SpeedDialSite("BBC Bangla", "https://www.bbc.com/bengali", "BBC"),
            SpeedDialSite("Prothom Alo", "https://www.prothomalo.com", "PA"),
            SpeedDialSite("MrGreenBD", "https://mrgreenbd.com", "MG")
        )
    }

    var tabs by remember {
        mutableStateOf(
            listOf(
                BrowserTabItem(
                    id = UUID.randomUUID().toString(),
                    title = "Speed Dial",
                    url = "about:speeddial"
                )
            )
        )
    }
    var activeTabIndex by remember { mutableIntStateOf(0) }
    var addressInput by remember { mutableStateOf("") }
    var desktopMode by remember { mutableStateOf(isTvDevice) }
    var aiSidebarOpen by remember { mutableStateOf(false) }

    var privacyStats by remember {
        mutableStateOf(
            PrivacyStats(
                forceDarkWeb = initialForceDark,
                blockPopupsAndRedirects = initialBlockPopups,
                customUserScript = initialUserScript,
                searchEngine = initialSearchEngine,
                dnsProfile = initialDnsProfile,
                appLockEnabled = initialLockEnabled,
                pinCode = initialPin
            )
        )
    }
    var downloads by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }

    var apiKey by remember { mutableStateOf("") }
    var aiPrompt by remember { mutableStateOf("Summarize this page in clear bullet points.") }
    var aiResponse by remember { mutableStateOf("Ready. Open any webpage and tap Summarize, Bangla Translate, or ask AI.") }
    var aiLoading by remember { mutableStateOf(false) }

    var activeMediaUrl by remember { mutableStateOf<String?>(null) }

    var webContainerSize by remember { mutableStateOf(IntSize(1280, 720)) }
    var cursorX by remember { mutableFloatStateOf(400f) }
    var cursorY by remember { mutableFloatStateOf(300f) }

    val activeTab = tabs.getOrElse(activeTabIndex) { tabs.first() }
    val isBookmarked = remember(bookmarks, activeTab.url) {
        bookmarks.any { it.url == activeTab.url }
    }

    LaunchedEffect(activeTab.url) {
        addressInput = if (activeTab.url == "about:speeddial") "" else activeTab.url
    }

    fun navigateTo(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed == "about:speeddial") {
            tabs = tabs.mapIndexed { idx, item ->
                if (idx == activeTabIndex) item.copy(title = "Speed Dial", url = "about:speeddial", isLoading = false, readerMode = false)
                else item
            }
            addressInput = ""
            currentNav = AppTab.BROWSER
            return
        }
        val normalized = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "${privacyStats.searchEngine.searchUrlPrefix}${Uri.encode(trimmed)}"
        }
        addressInput = normalized
        tabs = tabs.mapIndexed { idx, item ->
            if (idx == activeTabIndex) item.copy(url = normalized) else item
        }
        activeTab.webView?.loadUrl(normalized)
        currentNav = AppTab.BROWSER
    }

    fun addNewTab(privateMode: Boolean = false, initialUrl: String = "about:speeddial") {
        val newTab = BrowserTabItem(
            id = UUID.randomUUID().toString(),
            title = if (privateMode) "Private Dial" else "Speed Dial",
            url = initialUrl,
            isPrivate = privateMode
        )
        tabs = tabs + newTab
        activeTabIndex = tabs.lastIndex
        addressInput = if (initialUrl == "about:speeddial") "" else initialUrl
        currentNav = AppTab.BROWSER
    }

    fun restoreClosedOrLastTab() {
        val lastUrl = historyList.firstOrNull()?.url ?: "https://www.google.com"
        addNewTab(privateMode = false, initialUrl = lastUrl)
        Toast.makeText(context, "Restored recent session tab", Toast.LENGTH_SHORT).show()
    }

    fun toggleBookmarkCurrent() {
        if (activeTab.url == "about:speeddial") return
        scope.launch {
            val existing = bookmarks.find { it.url == activeTab.url }
            if (existing != null) {
                dao.deleteBookmark(existing)
                Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
            } else {
                dao.insertBookmark(
                    BookmarkEntity(
                        title = activeTab.title.ifBlank { activeTab.url },
                        url = activeTab.url
                    )
                )
                Toast.makeText(context, "Bookmarked!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportBookmarksJson() {
        val arr = JSONArray()
        bookmarks.forEach { bm ->
            arr.put(
                JSONObject().apply {
                    put("title", bm.title)
                    put("url", bm.url)
                }
            )
        }
        val jsonStr = arr.toString(2)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MrGreenBD Bookmarks JSON", jsonStr))
        Toast.makeText(context, "Exported ${bookmarks.size} bookmarks to Clipboard JSON", Toast.LENGTH_LONG).show()
    }

    fun importSampleOrClipboardBookmarks() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
        scope.launch {
            if (clipText.startsWith("[")) {
                runCatching {
                    val arr = JSONArray(clipText)
                    val list = mutableListOf<BookmarkEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val u = obj.optString("url")
                        val t = obj.optString("title").ifBlank { u }
                        if (u.startsWith("http")) {
                            list.add(BookmarkEntity(title = t, url = u))
                        }
                    }
                    dao.insertBookmarks(list)
                    Toast.makeText(context, "Imported ${list.size} bookmarks from JSON", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Invalid JSON in clipboard", Toast.LENGTH_SHORT).show()
                }
            } else {
                val defaults = listOf(
                    BookmarkEntity(title = "YouTube", url = "https://m.youtube.com"),
                    BookmarkEntity(title = "GitHub", url = "https://github.com"),
                    BookmarkEntity(title = "Wikipedia Bangla", url = "https://bn.wikipedia.org")
                )
                dao.insertBookmarks(defaults)
                Toast.makeText(context, "Added quick starter bookmarks", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun enqueueDownload(url: String, mimeType: String, suggestedName: String?) {
        val fileName = suggestedName?.takeIf { it.isNotBlank() }
            ?: Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
            ?: "mrgreenbd_download_${System.currentTimeMillis()}"

        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Downloaded via MrGreenBD All in One AI+ Browser")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            dm.enqueue(req)
        } catch (_: Exception) {
        }

        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            url = url,
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            progress = 100,
            status = "Sent to Download Manager"
        )
        downloads = listOf(item) + downloads
    }

    fun extractTextFromCurrentPage(onResult: (String) -> Unit) {
        val wv = activeTab.webView
        if (wv == null || activeTab.url == "about:speeddial") {
            onResult("")
            return
        }
        wv.evaluateJavascript(
            "(function(){ return document.body ? document.body.innerText : ''; })();"
        ) { raw ->
            val unescaped = raw
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                .orEmpty()
            onResult(unescaped)
        }
    }

    fun simulateTapAtCursor() {
        val wv = activeTab.webView ?: return
        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0)
        val upEvent = MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, cursorX, cursorY, 0)
        wv.dispatchTouchEvent(downEvent)
        wv.dispatchTouchEvent(upEvent)
        downEvent.recycle()
        upEvent.recycle()
    }

    @Composable
    fun AddressAndTabHeader() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { navigateTo("about:speeddial") }) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = if (activeTab.isPrivate) Icons.Default.VisibilityOff else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (activeTab.isPrivate) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { toggleBookmarkCurrent() }) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                            if (addressInput.isNotEmpty()) {
                                IconButton(onClick = { addressInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    },
                    placeholder = { Text("${privacyStats.searchEngine.label} or URL...", maxLines = 1) }
                )

                IconButton(onClick = { navigateTo(addressInput) }) {
                    Icon(Icons.Default.Search, contentDescription = "Go")
                }

                IconButton(
                    onClick = {
                        aiSidebarOpen = !aiSidebarOpen
                        if (aiSidebarOpen) {
                            extractTextFromCurrentPage { text ->
                                if (text.isNotBlank() && aiResponse.startsWith("Ready.")) {
                                    aiResponse = "Page text ready (${text.length} chars). Tap Summarize, Bangla Translate, or ask below."
                                }
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI+",
                        tint = if (aiSidebarOpen) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { addNewTab(privateMode = activeTab.isPrivate) }
                ) {
                    Text(
                        text = "${tabs.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (activeTab.isLoading) {
                LinearProgressIndicator(
                    progress = { activeTab.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == activeTabIndex
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            activeTabIndex = index
                            addressInput = if (tab.url == "about:speeddial") "" else tab.url
                            currentNav = AppTab.BROWSER
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.widthIn(max = if (isTablet || isTvDevice) 180.dp else 110.dp)
                            )
                            if (tabs.size > 1) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close tab",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            val updated = tabs.filterIndexed { i, _ -> i != index }
                                            tabs = updated
                                            activeTabIndex = activeTabIndex.coerceIn(0, updated.lastIndex)
                                        }
                                )
                            }
                        }
                    }
                }

                AssistChip(onClick = { addNewTab(privateMode = false) }, label = { Text("+ Tab") })
                AssistChip(onClick = { addNewTab(privateMode = true) }, label = { Text("+ Private") })
                AssistChip(onClick = { restoreClosedOrLastTab() }, label = { Text("Restore") })
            }
        }
    }

    Scaffold(
        topBar = {
            if (!bottomAddressBar) {
                AddressAndTabHeader()
            }
        },
        bottomBar = {
            Column {
                if (bottomAddressBar) {
                    AddressAndTabHeader()
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentNav == AppTab.BROWSER,
                        onClick = { currentNav = AppTab.BROWSER },
                        icon = { Icon(Icons.Default.Language, contentDescription = "Browser") },
                        label = { Text("Browser") }
                    )
                    NavigationBarItem(
                        selected = currentNav == AppTab.AI_HUB,
                        onClick = { currentNav = AppTab.AI_HUB },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI+") },
                        label = { Text("AI+") }
                    )
                    NavigationBarItem(
                        selected = currentNav == AppTab.LIBRARY,
                        onClick = { currentNav = AppTab.LIBRARY },
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentNav == AppTab.DOWNLOADS,
                        onClick = { currentNav = AppTab.DOWNLOADS },
                        icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                        label = { Text("Downloads") }
                    )
                    NavigationBarItem(
                        selected = currentNav == AppTab.PRIVACY,
                        onClick = { currentNav = AppTab.PRIVACY },
                        icon = { Icon(Icons.Default.Tune, contentDescription = "Tools") },
                        label = { Text("Tools") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentNav) {
                AppTab.BROWSER -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(if (aiSidebarOpen && (isTablet || isTvDevice)) 0.65f else 1f)
                                .fillMaxHeight()
                        ) {
                            activeTab.detectedMediaUrl?.let { mediaUrl ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Media stream detected",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { activeMediaUrl = mediaUrl }) {
                                            Text("Play")
                                        }
                                        TextButton(onClick = { activity.enterPipModeIfSupported() }) {
                                            Text("PiP")
                                        }
                                        TextButton(onClick = {
                                            enqueueDownload(mediaUrl, "video/mp4", "stream_video.mp4")
                                        }) {
                                            Text("Save")
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .onSizeChanged { webContainerSize = it }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (!tvCursorMode) return@onPreviewKeyEvent false
                                        val step = 30f
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                cursorX = (cursorX - step).coerceAtLeast(0f)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                cursorX = (cursorX + step).coerceAtMost(webContainerSize.width.toFloat())
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                cursorY = (cursorY - step).coerceAtLeast(0f)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                cursorY = (cursorY + step).coerceAtMost(webContainerSize.height.toFloat())
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                                    simulateTapAtCursor()
                                                }
                                                true
                                            }
                                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP && activeMediaUrl == null && activeTab.detectedMediaUrl != null) {
                                                    activeMediaUrl = activeTab.detectedMediaUrl
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                                    .focusable(tvCursorMode)
                            ) {
                                if (activeTab.url == "about:speeddial") {
                                    SpeedDialHomeScreen(
                                        sites = defaultSpeedDials,
                                        bookmarks = bookmarks,
                                        isPrivate = activeTab.isPrivate,
                                        isLargeScreen = isTablet || isTvDevice,
                                        onOpenSite = { url -> navigateTo(url) }
                                    )
                                } else {
                                    BrowserWebViewContainer(
                                        tab = activeTab,
                                        desktopMode = desktopMode,
                                        privacyStats = privacyStats,
                                        onTabUpdated = { updatedTab ->
                                            tabs = tabs.mapIndexed { idx, item ->
                                                if (idx == activeTabIndex) updatedTab else item
                                            }
                                        },
                                        onRecordHistory = { pageTitle, pageUrl ->
                                            if (!activeTab.isPrivate && pageUrl.startsWith("http")) {
                                                scope.launch {
                                                    dao.insertHistory(
                                                        HistoryEntity(
                                                            title = pageTitle.ifBlank { pageUrl },
                                                            url = pageUrl
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onAdBlocked = {
                                            if (privacyStats.shieldEnabled) {
                                                privacyStats = privacyStats.copy(
                                                    adsBlocked = privacyStats.adsBlocked + 1
                                                )
                                            }
                                        },
                                        onDownloadRequested = { url, mime, name ->
                                            enqueueDownload(url, mime, name)
                                        },
                                        onMediaDetected = { mediaUrl ->
                                            tabs = tabs.mapIndexed { idx, item ->
                                                if (idx == activeTabIndex) item.copy(detectedMediaUrl = mediaUrl)
                                                else item
                                            }
                                        }
                                    )
                                }

                                if (tvCursorMode && activeTab.url != "about:speeddial") {
                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(cursorX.roundToInt(), cursorY.roundToInt())
                                            }
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }

                                activeMediaUrl?.let { streamUrl ->
                                    FloatingExoPlayerCard(
                                        mediaUrl = streamUrl,
                                        onPip = { activity.enterPipModeIfSupported() },
                                        onClose = { activeMediaUrl = null },
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(10.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { activeTab.webView?.goBack() },
                                    enabled = activeTab.canGoBack
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                IconButton(
                                    onClick = { activeTab.webView?.goForward() },
                                    enabled = activeTab.canGoForward
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                                }
                                IconButton(onClick = { activeTab.webView?.reload() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                                }
                                FilterChip(
                                    selected = activeTab.readerMode,
                                    onClick = {
                                        val nextReader = !activeTab.readerMode
                                        tabs = tabs.mapIndexed { idx, item ->
                                            if (idx == activeTabIndex) item.copy(readerMode = nextReader) else item
                                        }
                                        activeTab.webView?.evaluateJavascript(ShieldAndMediaEngine.READER_MODE_SCRIPT, null)
                                    },
                                    label = { Text("Reader") }
                                )
                                FilterChip(
                                    selected = privacyStats.forceDarkWeb,
                                    onClick = {
                                        val next = !privacyStats.forceDarkWeb
                                        privacyStats = privacyStats.copy(forceDarkWeb = next)
                                        prefs.edit().putBoolean("force_dark_web", next).apply()
                                        activeTab.webView?.reload()
                                    },
                                    label = { Text("Force Dark") }
                                )
                                FilterChip(
                                    selected = desktopMode,
                                    onClick = {
                                        desktopMode = !desktopMode
                                        activeTab.webView?.reload()
                                    },
                                    label = { Text(if (desktopMode) "Desktop" else "Mobile") }
                                )
                                FilterChip(
                                    selected = tvCursorMode,
                                    onClick = { tvCursorMode = !tvCursorMode },
                                    label = { Text("TV Cursor") }
                                )
                            }
                        }

                        if (aiSidebarOpen && (isTablet || isTvDevice)) {
                            Surface(
                                tonalElevation = 4.dp,
                                modifier = Modifier
                                    .weight(0.35f)
                                    .fillMaxHeight()
                            ) {
                                AiAssistantPanel(
                                    apiKey = apiKey,
                                    onApiKeyChange = { apiKey = it },
                                    prompt = aiPrompt,
                                    onPromptChange = { aiPrompt = it },
                                    response = aiResponse,
                                    isLoading = aiLoading,
                                    onSummarizePage = {
                                        extractTextFromCurrentPage { text ->
                                            aiLoading = true
                                            scope.launch {
                                                val res = AiAssistantEngine.askAi(
                                                    prompt = "Summarize this webpage clearly with key takeaways.",
                                                    pageContext = text,
                                                    apiKey = apiKey
                                                )
                                                aiResponse = res.getOrElse { "Error: ${it.message}" }
                                                aiLoading = false
                                            }
                                        }
                                    },
                                    onTranslateBangla = {
                                        extractTextFromCurrentPage { text ->
                                            aiLoading = true
                                            scope.launch {
                                                val res = AiAssistantEngine.askAi(
                                                    prompt = "Translate and explain the main points of this page naturally in Bengali.",
                                                    pageContext = text,
                                                    apiKey = apiKey
                                                )
                                                aiResponse = res.getOrElse { "Error: ${it.message}" }
                                                aiLoading = false
                                            }
                                        }
                                    },
                                    onAsk = {
                                        extractTextFromCurrentPage { text ->
                                            aiLoading = true
                                            scope.launch {
                                                val res = AiAssistantEngine.askAi(
                                                    prompt = aiPrompt,
                                                    pageContext = text,
                                                    apiKey = apiKey
                                                )
                                                aiResponse = res.getOrElse { "Error: ${it.message}" }
                                                aiLoading = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (aiSidebarOpen && !isTablet && !isTvDevice) {
                        ModalBottomSheet(onDismissRequest = { aiSidebarOpen = false }) {
                            AiAssistantPanel(
                                apiKey = apiKey,
                                onApiKeyChange = { apiKey = it },
                                prompt = aiPrompt,
                                onPromptChange = { aiPrompt = it },
                                response = aiResponse,
                                isLoading = aiLoading,
                                onSummarizePage = {
                                    extractTextFromCurrentPage { text ->
                                        aiLoading = true
                                        scope.launch {
                                            val res = AiAssistantEngine.askAi(
                                                prompt = "Summarize this webpage clearly with key takeaways.",
                                                pageContext = text,
                                                apiKey = apiKey
                                            )
                                            aiResponse = res.getOrElse { "Error: ${it.message}" }
                                            aiLoading = false
                                        }
                                    }
                                },
                                onTranslateBangla = {
                                    extractTextFromCurrentPage { text ->
                                        aiLoading = true
                                        scope.launch {
                                            val res = AiAssistantEngine.askAi(
                                                prompt = "Translate and explain the main points of this page naturally in Bengali.",
                                                pageContext = text,
                                                apiKey = apiKey
                                            )
                                            aiResponse = res.getOrElse { "Error: ${it.message}" }
                                            aiLoading = false
                                        }
                                    }
                                },
                                onAsk = {
                                    extractTextFromCurrentPage { text ->
                                        aiLoading = true
                                        scope.launch {
                                            val res = AiAssistantEngine.askAi(
                                                prompt = aiPrompt,
                                                pageContext = text,
                                                apiKey = apiKey
                                            )
                                            aiResponse = res.getOrElse { "Error: ${it.message}" }
                                            aiLoading = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                AppTab.AI_HUB -> {
                    AiAssistantPanel(
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it },
                        prompt = aiPrompt,
                        onPromptChange = { aiPrompt = it },
                        response = aiResponse,
                        isLoading = aiLoading,
                        onSummarizePage = {
                            extractTextFromCurrentPage { text ->
                                aiLoading = true
                                scope.launch {
                                    val res = AiAssistantEngine.askAi(
                                        prompt = "Summarize this webpage clearly with key takeaways.",
                                        pageContext = text,
                                        apiKey = apiKey
                                    )
                                    aiResponse = res.getOrElse { "Error: ${it.message}" }
                                    aiLoading = false
                                }
                            }
                        },
                        onTranslateBangla = {
                            extractTextFromCurrentPage { text ->
                                aiLoading = true
                                scope.launch {
                                    val res = AiAssistantEngine.askAi(
                                        prompt = "Translate and explain the main points of this page naturally in Bengali.",
                                        pageContext = text,
                                        apiKey = apiKey
                                    )
                                    aiResponse = res.getOrElse { "Error: ${it.message}" }
                                    aiLoading = false
                                }
                            }
                        },
                        onAsk = {
                            extractTextFromCurrentPage { text ->
                                aiLoading = true
                                scope.launch {
                                    val res = AiAssistantEngine.askAi(
                                        prompt = aiPrompt,
                                        pageContext = text,
                                        apiKey = apiKey
                                    )
                                    aiResponse = res.getOrElse { "Error: ${it.message}" }
                                    aiLoading = false
                                }
                            }
                        }
                    )
                }

                AppTab.LIBRARY -> {
                    LibraryScreen(
                        bookmarks = bookmarks,
                        historyList = historyList,
                        onOpenUrl = { url -> navigateTo(url) },
                        onDeleteBookmark = { bm -> scope.launch { dao.deleteBookmark(bm) } },
                        onExportBookmarks = { exportBookmarksJson() },
                        onImportBookmarks = { importSampleOrClipboardBookmarks() },
                        onClearHistory = { scope.launch { dao.clearHistory() } }
                    )
                }

                AppTab.DOWNLOADS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Smart Download Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        if (downloads.isEmpty()) {
                            Text("No downloads yet. Any file or media link started in the browser appears here.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(downloads, key = { it.id }) { item ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(item.fileName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                text = item.url,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "${item.status} (${item.progress}%)",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AppTab.PRIVACY -> {
                    var newPinInput by remember { mutableStateOf(privacyStats.pinCode) }
                    var userScriptInput by remember { mutableStateOf(privacyStats.customUserScript) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "MrGreenBD All in One Controls",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${privacyStats.adsBlocked}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("Ads Blocked", style = MaterialTheme.typography.labelMedium)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (isTvDevice) "Android TV" else if (isTablet) "Tablet" else "Mobile",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("Active Mode", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Text("Search Engine", fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchEngineOption.entries.forEach { option ->
                                FilterChip(
                                    selected = privacyStats.searchEngine == option,
                                    onClick = {
                                        privacyStats = privacyStats.copy(searchEngine = option)
                                        prefs.edit().putString("search_engine", option.name).apply()
                                    },
                                    label = { Text(option.label) }
                                )
                            }
                        }

                        Text("DNS / Privacy Profile", fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DnsProfileOption.entries.forEach { dns ->
                                FilterChip(
                                    selected = privacyStats.dnsProfile == dns,
                                    onClick = {
                                        privacyStats = privacyStats.copy(dnsProfile = dns)
                                        prefs.edit().putString("dns_profile", dns.name).apply()
                                    },
                                    label = { Text(dns.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Ad & Tracker Shield")
                            Switch(
                                checked = privacyStats.shieldEnabled,
                                onCheckedChange = { privacyStats = privacyStats.copy(shieldEnabled = it) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Block Pop-Ups & Suspicious Redirects")
                            Switch(
                                checked = privacyStats.blockPopupsAndRedirects,
                                onCheckedChange = { enabled ->
                                    privacyStats = privacyStats.copy(blockPopupsAndRedirects = enabled)
                                    prefs.edit().putBoolean("block_popups", enabled).apply()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Force Dark Mode on Webpages")
                            Switch(
                                checked = privacyStats.forceDarkWeb,
                                onCheckedChange = {
                                    privacyStats = privacyStats.copy(forceDarkWeb = it)
                                    prefs.edit().putBoolean("force_dark_web", it).apply()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Android TV Remote Cursor Mode")
                            Switch(
                                checked = tvCursorMode,
                                onCheckedChange = { tvCursorMode = it }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("One-Handed Bottom Address Bar")
                            Switch(
                                checked = bottomAddressBar,
                                onCheckedChange = {
                                    bottomAddressBar = it
                                    prefs.edit().putBoolean("bottom_address_bar", it).apply()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("App Lock (Biometric + PIN)")
                            Switch(
                                checked = privacyStats.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    privacyStats = privacyStats.copy(appLockEnabled = enabled)
                                    prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newPinInput,
                                onValueChange = { if (it.length <= 6) newPinInput = it },
                                label = { Text("App Lock PIN") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                if (newPinInput.length >= 4) {
                                    privacyStats = privacyStats.copy(pinCode = newPinInput)
                                    prefs.edit().putString("app_pin", newPinInput).apply()
                                    Toast.makeText(context, "PIN Updated", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Save PIN")
                            }
                        }

                        HorizontalDivider()

                        Text("Custom UserScript (JavaScript)", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = userScriptInput,
                            onValueChange = { userScriptInput = it },
                            placeholder = { Text("// Example: document.body.style.fontSize = '18px';") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = {
                            privacyStats = privacyStats.copy(customUserScript = userScriptInput)
                            prefs.edit().putString("custom_userscript", userScriptInput).apply()
                            Toast.makeText(context, "UserScript Saved", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Save & Apply UserScript")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedDialHomeScreen(
    sites: List<SpeedDialSite>,
    bookmarks: List<BookmarkEntity>,
    isPrivate: Boolean,
    isLargeScreen: Boolean,
    onOpenSite: (String) -> Unit
) {
    val columns = if (isLargeScreen) 6 else 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isPrivate) "MrGreenBD Private Dial" else "MrGreenBD All in One Speed Dial",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isPrivate) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Mobile, Tablet & Android TV • Ad Shield • AI+",
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray
        )
        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(sites) { site ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable { onOpenSite(site.url) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                        ) {
                            Text(
                                text = site.badge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = site.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (bookmarks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Pinned Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(bookmarks.take(5), key = { it.id }) { bm ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSite(bm.url) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    bookmarks: List<BookmarkEntity>,
    historyList: List<HistoryEntity>,
    onOpenUrl: (String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onExportBookmarks: () -> Unit,
    onImportBookmarks: () -> Unit,
    onClearHistory: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val formatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(selectedTabIndex = selectedSection) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("Bookmarks (${bookmarks.size})") }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("History (${historyList.size})") }
            )
        }

        Spacer(Modifier.height(10.dp))

        if (selectedSection == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportBookmarks) {
                    Text("Export JSON")
                }
                OutlinedButton(onClick = onImportBookmarks) {
                    Text("Import / Defaults")
                }
            }
            Spacer(Modifier.height(10.dp))

            if (bookmarks.isEmpty()) {
                Text("No bookmarks saved yet. Tap the bookmark icon inside the address bar to save pages.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(bm.url) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bm.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(bm.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onDeleteBookmark(bm) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (historyList.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear History")
                    }
                }
            }
            if (historyList.isEmpty()) {
                Text("Browsing history is empty.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(historyList, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(item.url) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = formatter.format(Date(item.visitedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebViewContainer(
    tab: BrowserTabItem,
    desktopMode: Boolean,
    privacyStats: PrivacyStats,
    onTabUpdated: (BrowserTabItem) -> Unit,
    onRecordHistory: (String, String) -> Unit,
    onAdBlocked: () -> Unit,
    onDownloadRequested: (String, String, String?) -> Unit,
    onMediaDetected: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportMultipleWindows(!privacyStats.blockPopupsAndRedirects)
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(
                    this,
                    !privacyStats.blockThirdPartyCookies
                )

                setDownloadListener(DownloadListener { url, _, contentDisposition, mimetype, _ ->
                    val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                    onDownloadRequested(url, mimetype, fileName)
                })

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val targetUrl = request?.url?.toString() ?: return false
                        if (ShieldAndMediaEngine.isSuspiciousPopupOrRedirect(
                                targetUrl,
                                privacyStats.blockPopupsAndRedirects
                            )
                        ) {
                            onAdBlocked()
                            return true
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (request != null && privacyStats.shieldEnabled) {
                            if (ShieldAndMediaEngine.isAdOrTracker(request)) {
                                onAdBlocked()
                                return ShieldAndMediaEngine.emptyBlockedResponse()
                            }
                            val reqUrl = request.url.toString()
                            if (ShieldAndMediaEngine.isStreamableMedia(reqUrl)) {
                                view?.post { onMediaDetected(reqUrl) }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onTabUpdated(
                            tab.copy(
                                url = url ?: tab.url,
                                isLoading = true,
                                canGoBack = view?.canGoBack() == true,
                                canGoForward = view?.canGoForward() == true,
                                webView = view
                            )
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        val finalUrl = url ?: tab.url
                        val finalTitle = view?.title?.takeIf { it.isNotBlank() } ?: finalUrl
                        if (privacyStats.forceDarkWeb) {
                            view?.evaluateJavascript(ShieldAndMediaEngine.FORCE_DARK_SCRIPT, null)
                        }
                        if (privacyStats.customUserScript.isNotBlank()) {
                            view?.evaluateJavascript(privacyStats.customUserScript, null)
                        }
                        onTabUpdated(
                            tab.copy(
                                title = finalTitle,
                                url = finalUrl,
                                isLoading = false,
                                progress = 100,
                                canGoBack = view?.canGoBack() == true,
                                canGoForward = view?.canGoForward() == true,
                                webView = view
                            )
                        )
                        onRecordHistory(finalTitle, finalUrl)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onTabUpdated(
                            tab.copy(
                                progress = newProgress,
                                isLoading = newProgress < 100,
                                webView = view
                            )
                        )
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        if (!title.isNullOrBlank()) {
                            onTabUpdated(tab.copy(title = title, webView = view))
                        }
                    }
                }

                if (tab.url != "about:speeddial") {
                    loadUrl(tab.url)
                }
                onTabUpdated(tab.copy(webView = this))
            }
        },
        update = { webView ->
            val desktopUa =
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            val mobileUa = WebSettings.getDefaultUserAgent(webView.context)
            val targetUa = if (desktopMode) desktopUa else mobileUa
            if (webView.settings.userAgentString != targetUa) {
                webView.settings.userAgentString = targetUa
            }
        }
    )
}

@Composable
fun FloatingExoPlayerCard(
    mediaUrl: String,
    onPip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(mediaUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onPip,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Player", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun AiAssistantPanel(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    response: String,
    isLoading: Boolean,
    onSummarizePage: () -> Unit,
    onTranslateBangla: () -> Unit,
    onAsk: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "MrGreenBD AI+ Hub",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Gemini API Key (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onSummarizePage, enabled = !isLoading) {
                Text("Summarize")
            }
            OutlinedButton(onClick = onTranslateBangla, enabled = !isLoading) {
                Text("Bangla Translate")
            }
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MrGreenBD AI+ Output", response))
                    Toast.makeText(context, "Copied AI response", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Copy")
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            label = { Text("Ask AI about this page or anything...") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onAsk,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Send to AI+")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = response,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
