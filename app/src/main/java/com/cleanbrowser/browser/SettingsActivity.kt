package com.cleanbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cleanbrowser.browser.data.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var spinnerSearch: Spinner
    private lateinit var inputHomepage: EditText
    private lateinit var btnClearHistory: TextView
    private lateinit var btnClearBookmarks: TextView
    private lateinit var btnClearAll: TextView

    private val searchEngines = arrayOf("Google", "DuckDuckGo", "Bing", "Brave Search")
    private val searchUrls = arrayOf(
        "https://www.google.com/search?q=",
        "https://duckduckgo.com/?q=",
        "https://www.bing.com/search?q=",
        "https://search.brave.com/search?q="
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        textUserName = findViewById(R.id.text_user_name)
        textUserEmail = findViewById(R.id.text_user_email)
        btnLogout = findViewById(R.id.btn_logout)
        spinnerSearch = findViewById(R.id.spinner_search)
        inputHomepage = findViewById(R.id.input_homepage)
        btnClearHistory = findViewById(R.id.btn_clear_history)
        btnClearBookmarks = findViewById(R.id.btn_clear_bookmarks)
        btnClearAll = findViewById(R.id.btn_clear_all)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        loadUserInfo()
        setupSearchSpinner()
        loadSettings()

        btnLogout.setOnClickListener { confirmLogout() }
        btnClearHistory.setOnClickListener { clearHistory() }
        btnClearBookmarks.setOnClickListener { clearBookmarks() }
        btnClearAll.setOnClickListener { clearAll() }

        inputHomepage.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveHomepage()
        }
    }

    private fun getUserId(): String {
        val prefs = getSharedPreferences("cleanbrowser", MODE_PRIVATE)
        val isGuest = prefs.getBoolean("is_guest", false)
        if (isGuest) return "guest"
        return try { FirebaseAuth.getInstance().currentUser?.uid ?: "guest" } catch (_: Exception) { "guest" }
    }

    private fun loadUserInfo() {
        val prefs = getSharedPreferences("cleanbrowser", MODE_PRIVATE)
        val isGuest = prefs.getBoolean("is_guest", false)
        val user = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }

        if (!isGuest && user != null) {
            textUserName.text = user.displayName ?: "User"
            textUserEmail.text = user.email ?: ""
            btnLogout.visibility = View.VISIBLE
        } else {
            textUserName.text = "Guest"
            textUserEmail.text = "Not signed in"
            btnLogout.visibility = View.GONE
        }
    }

    private fun setupSearchSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, searchEngines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSearch.adapter = adapter

        val prefs = getSharedPreferences("cleanbrowser", MODE_PRIVATE)
        val idx = prefs.getInt("search_engine", 0)
        if (idx in searchEngines.indices) spinnerSearch.setSelection(idx)

        spinnerSearch.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.edit().putInt("search_engine", pos).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("cleanbrowser", MODE_PRIVATE)
        inputHomepage.setText(prefs.getString("homepage", "https://www.google.com"))
    }

    private fun saveHomepage() {
        val url = inputHomepage.text.toString().trim()
        if (url.isNotEmpty()) {
            getSharedPreferences("cleanbrowser", MODE_PRIVATE)
                .edit().putString("homepage", url).apply()
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log out?")
            .setMessage("Your bookmarks and history will be kept locally.")
            .setPositiveButton("Log out") { _, _ ->
                try { FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
                getSharedPreferences("cleanbrowser", MODE_PRIVATE)
                    .edit().remove("is_guest").apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearHistory() {
        val uid = getUserId()
        if (uid != "guest") {
            DatabaseHelper(this).clearHistory(uid)
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearBookmarks() {
        val uid = getUserId()
        if (uid != "guest") {
            DatabaseHelper(this).clearBookmarks(uid)
            Toast.makeText(this, "Bookmarks cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAll() {
        AlertDialog.Builder(this)
            .setTitle("Clear all data?")
            .setMessage("This will delete all bookmarks and history.")
            .setPositiveButton("Clear") { _, _ ->
                val uid = getUserId()
                if (uid != "guest") DatabaseHelper(this).clearAllUserData(uid)
                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        fun getSearchUrl(context: Context): String {
            val prefs = context.getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE)
            val idx = prefs.getInt("search_engine", 0)
            val urls = arrayOf(
                "https://www.google.com/search?q=",
                "https://duckduckgo.com/?q=",
                "https://www.bing.com/search?q=",
                "https://search.brave.com/search?q="
            )
            return urls.getOrElse(idx) { urls[0] }
        }

        fun getHomepage(context: Context): String {
            return context.getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE)
                .getString("homepage", "https://www.google.com") ?: "https://www.google.com"
        }
    }
}