package com.cleanbrowser.browser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cleanbrowser.browser.data.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth

class BookmarksActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val prefs = getSharedPreferences("cleanbrowser", MODE_PRIVATE)
        val isGuest = prefs.getBoolean("is_guest", false)
        val uid = if (isGuest) "guest" else (FirebaseAuth.getInstance().currentUser?.uid ?: "guest")
        val recycler = findViewById<RecyclerView>(R.id.recycler_bookmarks)
        val emptyText = findViewById<TextView>(R.id.text_empty)

        if (uid == "guest") {
            emptyText.visibility = View.VISIBLE
            return
        }

        val bookmarks = DatabaseHelper(this).getBookmarks(uid)

        if (bookmarks.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = SimpleAdapter(bookmarks.map { Pair(it.first, it.second) }) { url ->
                val result = Intent()
                result.putExtra("url", url)
                setResult(RESULT_OK, result)
                finish()
            }
        }
    }

    class SimpleAdapter(
        private val items: List<Pair<String, String>>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<SimpleAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.item_title)
            val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_simple_list, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (title, url) = items[position]
            holder.title.text = title.ifEmpty { url }
            holder.subtitle.text = url
            holder.itemView.setOnClickListener { onClick(url) }
        }

        override fun getItemCount() = items.size
    }
}