package com.cleanbrowser.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cleanbrowser.browser.data.DatabaseHelper
import com.cleanbrowser.browser.ui.TabAdapter

class MainActivity : AppCompatActivity() {

    // --- UI refs ---
    private lateinit var root: FrameLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var webviewContainer: FrameLayout
    private lateinit var urlInput: EditText
    private lateinit var urlBarContainer: FrameLayout
    private lateinit var sslIcon: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabSwitcher: FrameLayout
    private lateinit var tabList: RecyclerView
    private lateinit var tabCount: TextView
    private lateinit var tabCountLabel: TextView
    private lateinit var emptyTabs: LinearLayout
    private lateinit var btnMenu: ImageView
    private lateinit var btnTabs: FrameLayout
    private lateinit var btnNewTab: View
    private lateinit var btnCloseTabs: ImageView
    private lateinit var btnCreateFolder: ImageView
    private lateinit var btnCloseAll: View

    // --- State ---
    private val tabs = mutableListOf<Tab>()
    private var activeTabIndex = -1
    private var isTabSwitcherOpen = false
    private var tabAdapter: TabAdapter? = null
    private var userId: String = ""
    private lateinit var db: DatabaseHelper

    // Folder colors
    private val folderColors = intArrayOf(
        0xFF4285F4.toInt(), // Blue
        0xFFEA4335.toInt(), // Red
        0xFFFBBC05.toInt(), // Yellow
        0xFF34A853.toInt(), // Green
        0xFFE94560.toInt(), // Pink
        0xFF8B5CF6.toInt(), // Purple
        0xFF06B6D4.toInt(), // Cyan
        0xFFF97316.toInt()  // Orange
    )

    // Activity result launchers
    private lateinit var bookmarkLauncher: ActivityResultLauncher<Intent>
    private lateinit var historyLauncher: ActivityResultLauncher<Intent>

    data class Tab(
        var id: Int,
        var webView: WebView,
        var title: String = "",
        var url: String = "",
        var isIncognito: Boolean = false,
        var folderId: Long = -1,
        var folderName: String = "",
        var folderColor: Int = 0,
        var thumbnail: Bitmap? = null
    )

    // ========================
    //  Lifecycle
    // ========================

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check login state from local prefs (no Firebase)
        val prefs = getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE)
        val isGuest = prefs.getBoolean("is_guest", true)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        if (!isGuest && !isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        userId = if (isGuest) "guest" else (prefs.getString("user_email", "guest") ?: "guest")

        db = DatabaseHelper(this)

        bindViews()
        setupTabList()
        setupUrlBar()
        setupMenu()
        setupActivityLaunchers()

        openTab()
        val intentUrl = intent?.data?.toString()
        if (!intentUrl.isNullOrBlank()) {
            loadUrlInCurrentTab(intentUrl)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val url = intent?.data?.toString()
        if (!url.isNullOrBlank()) {
            loadUrlInCurrentTab(url)
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        if (isTabSwitcherOpen) { closeTabSwitcher(); return }
        if (urlInput.hasFocus()) { urlInput.clearFocus(); return }
        val current = getActiveTab() ?: return
        if (current.webView.canGoBack()) {
            current.webView.goBack()
        } else if (tabs.size > 1) {
            closeTab(current.id)
        } else {
            super.onBackPressed()
        }
    }

    // ========================
    //  View binding
    // ========================

    private fun bindViews() {
        root = findViewById(R.id.root)
        toolbar = findViewById(R.id.toolbar)
        webviewContainer = findViewById(R.id.webview_container)
        urlInput = findViewById(R.id.url_input)
        urlBarContainer = findViewById(R.id.url_bar_container)
        sslIcon = findViewById(R.id.ssl_icon)
        progressBar = findViewById(R.id.progress_bar)
        tabSwitcher = findViewById(R.id.tab_switcher)
        tabList = findViewById(R.id.tab_list)
        tabCount = findViewById(R.id.tab_count)
        tabCountLabel = findViewById(R.id.tab_count_label)
        emptyTabs = findViewById(R.id.empty_tabs)
        btnMenu = findViewById(R.id.btn_menu)
        btnTabs = findViewById(R.id.btn_tabs)
        btnNewTab = findViewById(R.id.btn_new_tab)
        btnCloseTabs = findViewById(R.id.btn_close_tabs)
        btnCreateFolder = findViewById(R.id.btn_create_folder)
        btnCloseAll = findViewById(R.id.btn_close_all)
    }

    // ========================
    //  Activity Launchers
    // ========================

    private fun setupActivityLaunchers() {
        bookmarkLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra("url")?.let { loadUrlInCurrentTab(it) }
            }
        }
        historyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra("url")?.let { loadUrlInCurrentTab(it) }
            }
        }
    }

    // ========================
    //  URL bar
    // ========================

    private fun setupUrlBar() {
        urlInput.setOnFocusChangeListener { _, focused ->
            urlBarContainer.background = if (focused) {
                getDrawable(R.drawable.url_bar_focused)
            } else {
                getDrawable(R.drawable.url_bar_bg)
            }
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val input = urlInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    val url = normalizeUrl(input)
                    loadUrlInCurrentTab(url)
                    urlInput.clearFocus()
                }
                true
            } else {
                false
            }
        }

        btnTabs.setOnClickListener { toggleTabSwitcher() }
        btnNewTab.setOnClickListener { openTab(); closeTabSwitcher() }
        btnCloseTabs.setOnClickListener { closeTabSwitcher() }
        btnCloseAll.setOnClickListener { closeAllTabs() }
        btnCreateFolder.setOnClickListener { showCreateGroupDialog() }
    }

    // ========================
    //  Menu
    // ========================

    private fun setupMenu() {
        btnMenu.setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_incognito -> { openTab(incognito = true); true }
                    R.id.menu_bookmark_add -> { toggleBookmark(); true }
                    R.id.menu_bookmarks -> {
                        bookmarkLauncher.launch(Intent(this, BookmarksActivity::class.java))
                        true
                    }
                    R.id.menu_history -> {
                        historyLauncher.launch(Intent(this, HistoryActivity::class.java))
                        true
                    }
                    R.id.menu_find -> { showFindInPage(); true }
                    R.id.menu_desktop -> { toggleDesktopMode(); true }
                    R.id.menu_share -> { sharePage(); true }
                    R.id.menu_settings -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    // ========================
    //  Bookmark
    // ========================

    private fun toggleBookmark() {
        val tab = getActiveTab() ?: return
        if (userId == "guest") {
            Toast.makeText(this, "Sign in to save bookmarks", Toast.LENGTH_SHORT).show()
            return
        }
        if (db.isBookmarked(userId, tab.url)) {
            db.removeBookmark(userId, tab.url)
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
        } else {
            db.addBookmark(userId, tab.title, tab.url)
            Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show()
        }
    }

    // ========================
    //  Find in page
    // ========================

    private fun showFindInPage() {
        val tab = getActiveTab() ?: return
        val input = EditText(this).apply {
            setTextColor(getColor(R.color.url_text))
            setHintTextColor(getColor(R.color.url_text_hint))
            hint = "Find in page..."
            setPadding(40, 20, 40, 20)
            setTextSize(15f)
        }
        AlertDialog.Builder(this)
            .setTitle("Find in page")
            .setView(input)
            .setPositiveButton("Find") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) tab.webView.findAllAsync(query)
            }
            .setNegativeButton("Close") { _, _ ->
                tab.webView.clearMatches()
            }
            .setOnCancelListener { tab.webView.clearMatches() }
            .show()
    }

    // ========================
    //  Desktop mode
    // ========================

    private fun toggleDesktopMode() {
        val tab = getActiveTab() ?: return
        val settings = tab.webView.settings
        val isDesktop = settings.userAgentString.contains("Desktop")
        if (isDesktop) {
            settings.userAgentString = settings.userAgentString.replace(" (Desktop)", "")
        } else {
            settings.userAgentString = "${settings.userAgentString} (Desktop)"
        }
        tab.webView.reload()
        Toast.makeText(this, if (isDesktop) "Mobile mode" else "Desktop mode", Toast.LENGTH_SHORT).show()
    }

    // ========================
    //  Share
    // ========================

    private fun sharePage() {
        val tab = getActiveTab() ?: return
        startActivity(Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, tab.url)
            type = "text/plain"
        }, "Share"))
    }

    // ========================
    //  Tab group management
    // ========================

    private fun closeAllTabs() {
        val tabsCopy = tabs.toList()
        for (t in tabsCopy) {
            t.webView.destroy()
            webviewContainer.removeView(t.webView)
        }
        tabs.clear()
        activeTabIndex = -1
        openTab()
        updateTabListUI()
        Toast.makeText(this, "All tabs closed", Toast.LENGTH_SHORT).show()
    }

    private fun closeGroup(folderId: Long) {
        val toClose = tabs.filter { it.folderId == folderId }.map { it.id }
        for (id in toClose) closeTab(id)
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply {
            setTextColor(getColor(R.color.url_text))
            setHintTextColor(getColor(R.color.url_text_hint))
            hint = "Group name"
            setPadding(40, 20, 40, 20)
            setTextSize(15f)
        }

        val colorNames = arrayOf("Blue", "Red", "Yellow", "Green", "Pink", "Purple", "Cyan", "Orange")
        var selectedColor = 0

        AlertDialog.Builder(this)
            .setTitle("New tab group")
            .setView(input)
            .setSingleChoiceItems(colorNames, 0) { _, which -> selectedColor = which }
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Group" }
                val color = folderColors[selectedColor]
                val folderId = db.createFolder(userId, name, color)
                Toast.makeText(this, "Group created", Toast.LENGTH_SHORT).show()
                // Assign active tab to the new group
                val activeTab = getActiveTab()
                if (activeTab != null) {
                    activeTab.folderId = folderId
                    activeTab.folderName = name
                    activeTab.folderColor = color
                    updateTabListUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTabGroupDialog(tab: Tab) {
        val colorNames = arrayOf("Blue", "Red", "Yellow", "Green", "Pink", "Purple", "Cyan", "Orange")
        val options = arrayOf("Add to new group", "Remove from group")
        val items = if (tab.folderId != -1L) options else arrayOf(options[0])

        AlertDialog.Builder(this)
            .setTitle(tab.title.ifEmpty { "New tab" })
            .setItems(items) { _, which ->
                when {
                    which == 0 -> {
                        // Add to new group
                        showAddToGroupDialog(tab)
                    }
                    which == 1 && tab.folderId != -1L -> {
                        // Remove from group
                        tab.folderId = -1
                        tab.folderName = ""
                        tab.folderColor = 0
                        updateTabListUI()
                        Toast.makeText(this, "Removed from group", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showAddToGroupDialog(tab: Tab) {
        val colorNames = arrayOf("Blue", "Red", "Yellow", "Green", "Pink", "Purple", "Cyan", "Orange")
        var selectedColor = 0
        val input = EditText(this).apply {
            setTextColor(getColor(R.color.url_text))
            setHintTextColor(getColor(R.color.url_text_hint))
            hint = "Group name"
            setPadding(40, 20, 40, 20)
            setTextSize(15f)
        }
        AlertDialog.Builder(this)
            .setTitle("Add to tab group")
            .setView(input)
            .setSingleChoiceItems(colorNames, 0) { _, which -> selectedColor = which }
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Group" }
                val color = folderColors[selectedColor]
                val folderId = db.createFolder(userId, name, color)
                tab.folderId = folderId
                tab.folderName = name
                tab.folderColor = color
                updateTabListUI()
                Toast.makeText(this, "Added to \"$name\"", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========================
    //  Tab list — Chrome-style 2-column grid
    // ========================

    private fun setupTabList() {
        // Use SpanSizeLookup so group headers span both columns
        val gridManager = GridLayoutManager(this, 2)
        gridManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val adapter = tabAdapter ?: return 1
                // Group headers span full width (2 columns)
                return if (adapter.getItemViewType(position) == 1) 2 else 1
            }
        }

        tabAdapter = TabAdapter(
            allTabs = tabs,
            activeTabId = { getActiveTab()?.id ?: -1 },
            onClick = { tab -> switchToTab(tab.id); closeTabSwitcher() },
            onClose = { tab -> closeTab(tab.id) },
            onLongClick = { tab -> showTabGroupDialog(tab) },
            onCloseGroup = { folderId -> closeGroup(folderId) },
            onToggleGroup = { folderId ->
                // Toggle group expand/collapse by rebuilding
                updateTabListUI()
            }
        )
        tabList.layoutManager = gridManager
        tabList.adapter = tabAdapter
    }

    private fun captureTabThumbnails() {
        // Capture thumbnails for all tabs using WebView.draw()
        for (tab in tabs) {
            try {
                val wv = tab.webView
                if (wv.width > 0 && wv.height > 0) {
                    val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    wv.draw(canvas)
                    // Scale down to thumbnail size
                    val thumbW = 360
                    val thumbH = (thumbW * wv.height.toFloat() / wv.width.toFloat()).toInt()
                    tab.thumbnail = Bitmap.createScaledBitmap(bitmap, thumbW, thumbH, true)
                    bitmap.recycle()
                }
            } catch (_: Exception) {
                tab.thumbnail = null
            }
        }
    }

    private fun updateTabListUI() {
        // Update tab count label
        tabCountLabel.text = if (tabs.size == 1) "1 tab" else "${tabs.size} tabs"

        tabAdapter?.rebuild()
        if (tabs.isEmpty()) {
            emptyTabs.visibility = View.VISIBLE
            tabList.visibility = View.GONE
        } else {
            emptyTabs.visibility = View.GONE
            tabList.visibility = View.VISIBLE
        }
        if (tabs.size > 1) {
            tabCount.visibility = View.VISIBLE
            tabCount.text = tabs.size.toString()
        } else {
            tabCount.visibility = View.GONE
        }
    }

    // ========================
    //  Tab switcher
    // ========================

    private fun toggleTabSwitcher() {
        if (isTabSwitcherOpen) closeTabSwitcher() else openTabSwitcher()
    }

    private fun openTabSwitcher() {
        isTabSwitcherOpen = true
        captureTabThumbnails()
        tabSwitcher.visibility = View.VISIBLE
        toolbar.visibility = View.GONE
        updateTabListUI()
    }

    private fun closeTabSwitcher() {
        isTabSwitcherOpen = false
        tabSwitcher.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
        syncUrlBar()
    }

    // ========================
    //  Tab management
    // ========================

    @SuppressLint("SetJavaScriptEnabled")
    private fun openTab(url: String? = null, incognito: Boolean = false) {
        closeTabSwitcher()

        val tabId = System.currentTimeMillis().toInt()

        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                blockNetworkImage = false
                loadsImagesAutomatically = true
                allowFileAccess = true
                allowContentAccess = true
                userAgentString = userAgentString.replace("Mobile Safari", "Chrome")
            }

            if (incognito) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val urlStr = request.url.toString()
                    return !urlStr.startsWith("http://") && !urlStr.startsWith("https://")
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 0
                    val tab = tabs.find { it.webView === view }
                    if (tab != null) {
                        tab.url = url ?: ""
                        tab.title = url ?: ""
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    progressBar.visibility = View.GONE
                    val tab = tabs.find { it.webView === view }
                    if (tab != null) {
                        tab.url = url ?: ""
                        tab.title = view.title ?: url ?: ""
                        // Capture thumbnail on page load
                        try {
                            if (view.width > 0 && view.height > 0) {
                                val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                val c = Canvas(bmp)
                                view.draw(c)
                                val thumbW = 360
                                val thumbH = (thumbW * view.height.toFloat() / view.width.toFloat()).toInt()
                                tab.thumbnail = Bitmap.createScaledBitmap(bmp, thumbW, thumbH, true)
                                bmp.recycle()
                            }
                        } catch (_: Exception) {}
                    }
                    if (view === getActiveTab()?.webView) {
                        syncUrlBar()
                    }
                    updateTabListUI()
                    if (userId != "guest" && !url.isNullOrEmpty()) {
                        db.addHistory(userId, tab?.title ?: url, url)
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (view === getActiveTab()?.webView) {
                        progressBar.progress = newProgress
                        if (newProgress == 100) progressBar.visibility = View.GONE
                        else progressBar.visibility = View.VISIBLE
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    val tab = tabs.find { it.webView === view }
                    if (tab != null) tab.title = title ?: ""
                    if (view === getActiveTab()?.webView) syncUrlBar()
                    updateTabListUI()
                }
            }
        }

        val tab = Tab(id = tabId, webView = webView, isIncognito = incognito)

        for (t in tabs) t.webView.visibility = View.GONE

        webviewContainer.addView(webView)
        tabs.add(tab)
        activeTabIndex = tabs.size - 1
        updateTabListUI()

        val loadUrl = url ?: SettingsActivity.getHomepage(this)
        webView.loadUrl(loadUrl)
        syncUrlBar()
    }

    private fun switchToTab(tabId: Int) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return
        for (t in tabs) t.webView.visibility = View.GONE
        activeTabIndex = index
        tabs[index].webView.visibility = View.VISIBLE
        syncUrlBar()
    }

    private fun closeTab(tabId: Int) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val tab = tabs[index]
        tab.thumbnail?.recycle()
        tab.webView.destroy()
        webviewContainer.removeView(tab.webView)
        tabs.removeAt(index)

        if (tabs.isEmpty()) { openTab(); return }

        if (activeTabIndex >= tabs.size) activeTabIndex = tabs.size - 1
        if (activeTabIndex == index || activeTabIndex >= tabs.size) {
            activeTabIndex = tabs.size - 1
        } else if (activeTabIndex > index) {
            activeTabIndex--
        }

        tabs[activeTabIndex].webView.visibility = View.VISIBLE
        syncUrlBar()
        updateTabListUI()
    }

    private fun getActiveTab(): Tab? {
        if (activeTabIndex in tabs.indices) return tabs[activeTabIndex]
        return null
    }

    private fun loadUrlInCurrentTab(url: String) {
        val tab = getActiveTab() ?: return
        tab.webView.loadUrl(url)
    }

    // ========================
    //  URL bar sync
    // ========================

    private fun syncUrlBar() {
        val tab = getActiveTab() ?: return
        if (!urlInput.hasFocus()) urlInput.setText(tab.url)
        if (tab.url.startsWith("https://")) {
            sslIcon.visibility = View.VISIBLE
            sslIcon.setImageResource(R.drawable.ic_lock)
            sslIcon.setColorFilter(getColor(R.color.ssl_secure))
        } else if (tab.url.startsWith("http://")) {
            sslIcon.visibility = View.VISIBLE
            sslIcon.setImageResource(R.drawable.ic_lock)
            sslIcon.setColorFilter(getColor(R.color.ssl_insecure))
        } else {
            sslIcon.visibility = View.GONE
        }
    }

    // ========================
    //  URL normalization
    // ========================

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.contains(".") && !trimmed.contains(" ")) return "https://$trimmed"
        val searchUrl = SettingsActivity.getSearchUrl(this)
        return "$searchUrl${android.net.Uri.encode(trimmed)}"
    }
}