package com.cleanbrowser.browser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cleanbrowser.browser.data.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth

class HistoryVH(view: View) : RecyclerView.ViewHolder(view) {
    val title: TextView = view.findViewById(R.id.item_title)
    val subtitle: TextView = view.findViewById(R.id.item_subtitle)
}

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val prefs = getSharedPreferences("cleanbrowser", MODE_PRIVATE)
        val isGuest = prefs.getBoolean("is_guest", false)
        val uid = if (isGuest) "guest" else (FirebaseAuth.getInstance().currentUser?.uid ?: "guest")
        val recycler = findViewById<RecyclerView>(R.id.recycler_history)
        val emptyText = findViewById<TextView>(R.id.text_empty)

        findViewById<TextView>(R.id.btn_clear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setPositiveButton("Clear") { _, _ ->
                    if (uid != "guest") DatabaseHelper(this).clearHistory(uid)
                    recycler.adapter = null
                    emptyText.visibility = View.VISIBLE
                    recycler.visibility = View.GONE
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        if (uid == "guest") {
            emptyText.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            return
        }

        val history = DatabaseHelper(this).getHistory(uid)

        if (history.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            recycler.layoutManager = LinearLayoutManager(this)

            val items = history.map { Triple(it.first, it.second, it.third) }
            recycler.adapter = object : RecyclerView.Adapter<HistoryVH>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryVH {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_simple_list, parent, false)
                    return HistoryVH(view)
                }

                override fun onBindViewHolder(holder: HistoryVH, position: Int) {
                    val (title, url, timestamp) = items[position]
                    holder.title.text = title.ifEmpty { url }
                    val domain = try { java.net.URL(url).host } catch (_: Exception) { url }
                    val time = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.US)
                        .format(java.util.Date(timestamp))
                    holder.subtitle.text = "$domain  ·  $time"
                    holder.itemView.setOnClickListener {
                        val result = Intent()
                        result.putExtra("url", url)
                        setResult(RESULT_OK, result)
                        finish()
                    }
                }

                override fun getItemCount() = items.size
            }
        }
    }
}