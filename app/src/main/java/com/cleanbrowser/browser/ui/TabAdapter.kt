package com.cleanbrowser.browser.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.cleanbrowser.browser.MainActivity.Tab
import com.cleanbrowser.browser.R

data class TabGroupInfo(
    val folderId: Long,
    val folderName: String,
    val folderColor: Int,
    val tabs: MutableList<Tab>,
    var isExpanded: Boolean = false
)

class TabAdapter(
    private val allTabs: MutableList<Tab>,
    private val activeTabId: () -> Int,
    private val onClick: (Tab) -> Unit,
    private val onClose: (Tab) -> Unit,
    private val onLongClick: (Tab) -> Unit,
    private val onCloseGroup: (Long) -> Unit,
    private val onToggleGroup: (Long) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val displayItems = mutableListOf<Any>()

    companion object {
        private const val TYPE_TAB = 0
        private const val TYPE_GROUP_HEADER = 1
    }

    fun rebuild() {
        displayItems.clear()

        val groups = mutableMapOf<Long, TabGroupInfo>()
        val ungrouped = mutableListOf<Tab>()

        for (tab in allTabs) {
            if (tab.folderId != -1L) {
                val existing = groups[tab.folderId]
                if (existing != null) {
                    existing.tabs.add(tab)
                } else {
                    groups[tab.folderId] = TabGroupInfo(
                        tab.folderId, tab.folderName, tab.folderColor,
                        mutableListOf(tab), true
                    )
                }
            } else {
                ungrouped.add(tab)
            }
        }

        // Ungrouped tabs first
        displayItems.addAll(ungrouped)

        // Group headers followed by their tab cards
        for (group in groups.values) {
            displayItems.add(group)
            if (group.isExpanded) {
                displayItems.addAll(group.tabs)
            }
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayItems[position] is TabGroupInfo) TYPE_GROUP_HEADER else TYPE_TAB
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_TAB) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.tab_item, parent, false)
            TabVH(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.tab_group_header, parent, false)
            GroupHeaderVH(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayItems[position]
        if (holder is TabVH && item is Tab) {
            val isActive = item.id == activeTabId()
            holder.bind(item, isActive)
        } else if (holder is GroupHeaderVH && item is TabGroupInfo) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = displayItems.size

    // --- Tab ViewHolder (Chrome-style card with thumbnail) ---
    inner class TabVH(view: View) : RecyclerView.ViewHolder(view) {
        private val card: androidx.cardview.widget.CardView = view.findViewById(R.id.tab_card)
        private val thumbnail: ImageView = view.findViewById(R.id.tab_thumbnail)
        private val favicon: TextView = view.findViewById(R.id.tab_favicon)
        private val domain: TextView = view.findViewById(R.id.tab_domain)
        private val title: TextView = view.findViewById(R.id.tab_title)
        private val closeBtn: ImageView = view.findViewById(R.id.tab_close)
        private val incognitoBadge: ImageView = view.findViewById(R.id.tab_incognito_badge)
        private val groupStrip: View = view.findViewById(R.id.tab_group_strip)
        private val groupDot: View = view.findViewById(R.id.tab_group_dot)
        private val groupLabel: TextView = view.findViewById(R.id.tab_group_label)

        fun bind(tab: Tab, isActive: Boolean) {
            // Title
            title.text = if (tab.title.isEmpty() || tab.title.startsWith("http")) "New tab" else tab.title

            // Domain
            val host = try { java.net.URL(tab.url).host.replace("www.", "") } catch (_: Exception) { "" }
            domain.text = if (host.isNotEmpty()) host else tab.url

            // Favicon letter
            favicon.text = if (host.isNotEmpty()) host.substring(0, 1).uppercase() else "?"
            val hue = if (host.isNotEmpty()) (host.hashCode() % 360 + 360) % 360 else 240
            favicon.setTextColor(Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.6f, 0.9f)))

            // Thumbnail
            if (tab.thumbnail != null) {
                thumbnail.setImageBitmap(tab.thumbnail)
                thumbnail.visibility = View.VISIBLE
                thumbnail.setBackgroundColor(Color.TRANSPARENT)
            } else {
                thumbnail.setImageBitmap(null)
                thumbnail.visibility = View.GONE
            }

            // Active tab highlight
            card.setCardBackgroundColor(if (isActive) 0xFF252540.toInt() else 0xFF1E1E32.toInt())

            // Incognito
            incognitoBadge.visibility = if (tab.isIncognito) View.VISIBLE else View.GONE

            // Group indicators
            if (tab.folderId != -1L) {
                groupStrip.visibility = View.VISIBLE
                groupStrip.setBackgroundColor(tab.folderColor)
                groupDot.visibility = View.VISIBLE
                groupDot.setBackgroundColor(tab.folderColor)
                groupLabel.visibility = View.VISIBLE
                groupLabel.text = tab.folderName
            } else {
                groupStrip.visibility = View.GONE
                groupDot.visibility = View.GONE
                groupLabel.visibility = View.GONE
            }

            // Click listeners
            itemView.setOnClickListener { onClick(tab) }
            closeBtn.setOnClickListener { onClose(tab) }
            itemView.setOnLongClickListener {
                onLongClick(tab)
                true
            }
        }
    }

    // --- Group Header ViewHolder ---
    inner class GroupHeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.group_name)
        private val countText: TextView = view.findViewById(R.id.group_count)
        private val colorDot: View = view.findViewById(R.id.group_color_dot)
        private val closeBtn: ImageView = view.findViewById(R.id.group_close)

        fun bind(group: TabGroupInfo) {
            nameText.text = group.folderName
            countText.text = "${group.tabs.size} tabs"
            colorDot.setBackgroundColor(group.folderColor)

            itemView.setOnClickListener { onToggleGroup(group.folderId) }
            closeBtn.setOnClickListener { onCloseGroup(group.folderId) }
        }
    }
}