/*
 * Ichaival - Android client for LANraragi https://github.com/Utazukin/Ichaival/
 * Copyright (C) 2025 Utazukin
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
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.load
import coil3.request.addLastModifiedToFileCacheKey
import coil3.request.allowHardware
import com.awxkee.jxlcoder.coil.JxlDecoder
import com.utazukin.ichaival.database.DatabaseReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RandomPicksAdapter(
    private val scope: CoroutineScope,
    private val onClick: (ArchiveListEntry) -> Unit
) : ListAdapter<ArchiveListEntry, RandomPicksAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val coverLoader = App.context.imageLoader.newBuilder()
        .coroutineContext(Dispatchers.IO.limitedParallelism(3))
        .components { add(JxlDecoder.Factory()) }
        .build()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.random_pick_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val cover: ImageView = view.findViewById(R.id.random_cover)
        private val title: TextView = view.findViewById(R.id.random_title)
        private val meta: TextView = view.findViewById(R.id.random_meta)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION)
                    onClick(getItem(position))
            }
        }

        fun bind(entry: ArchiveListEntry) {
            cover.setTag(R.id.random_cover, entry.id)
            cover.setImageDrawable(null)
            title.text = entry.title
            meta.text = itemView.context.getString(R.string.random_picks_pages, entry.pageCount)

            val cached = DatabaseReader.getArchiveImagePath(entry.id)
            if (cached.exists()) {
                loadCover(cached)
            } else {
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        tryOrNull { DatabaseReader.getArchiveImage(entry.id, App.context) }
                    }
                    if (file != null && cover.getTag(R.id.random_cover) == entry.id)
                        loadCover(file)
                }
            }
        }

        private fun loadCover(file: java.io.File) {
            cover.load(file, coverLoader) {
                allowHardware(false)
                addLastModifiedToFileCacheKey(true)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ArchiveListEntry>() {
            override fun areItemsTheSame(oldItem: ArchiveListEntry, newItem: ArchiveListEntry) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ArchiveListEntry, newItem: ArchiveListEntry) = oldItem == newItem
        }
    }
}
