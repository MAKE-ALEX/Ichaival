/*
 * Ichaival - Android client for LANraragi https://github.com/Utazukin/Ichaival/
 * Copyright (C) 2026 Utazukin
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.utazukin.ichaival

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.utazukin.ichaival.database.DatabaseReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Backs the history list in the navigation drawer. Entries come from the server's own progress
 * tracking, so they match what the web reader shows on any device.
 */
class ReadingHistoryAdapter(private val activity: BaseActivity) :
        RecyclerView.Adapter<ReadingHistoryAdapter.ViewHolder>() {
    private val entries = mutableListOf<ReadingHistoryEntry>()
    private var loadJob: Job? = null

    fun reload() {
        loadJob?.cancel()
        loadJob = activity.launch {
            val history = WebHandler.getReadingHistory()
            entries.clear()
            entries.addAll(history)
            notifyDataSetChanged()
            activity.updateHistoryEmptyState(history.isEmpty())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.reader_tab, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(entries[position])

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumb: ImageView = view.findViewById(R.id.reader_thumb)
        private val title: TextView = view.findViewById(R.id.archive_title)
        private val page: TextView = view.findViewById(R.id.archive_page)

        fun bind(entry: ReadingHistoryEntry) {
            title.text = entry.title.ifBlank { entry.id }
            page.text = (entry.currentPage + 1).toString()

            val thumbnail = DatabaseReader.getArchiveImagePath(entry.id)
            if (thumbnail.exists())
                thumb.load(thumbnail)
            else
                thumb.setImageResource(R.drawable.ic_collections_white_24dp)

            itemView.setOnClickListener { activity.openHistoryEntry(entry.id, entry.currentPage) }
        }
    }
}
