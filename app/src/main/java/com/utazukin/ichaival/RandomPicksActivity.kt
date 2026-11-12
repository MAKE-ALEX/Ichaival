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

import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.utazukin.ichaival.database.DatabaseReader
import kotlinx.coroutines.launch

const val RANDOM_SEARCH = "random"
const val RANDOM_CAT = "category"

/**
 * Full screen "random picks" view, modelled after the web client. Picks a handful of random
 * archives from the local index (respecting the current search/category) and lets the user
 * open any of them.
 */
class RandomPicksActivity : BaseActivity() {
    private lateinit var list: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: RandomPicksAdapter
    private var filter: String = ""
    private var categoryId: String = ""
    private var count: Int = DEFAULT_COUNT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_random_picks)
        setSupportActionBar(findViewById(R.id.toolbar))
        title = getString(R.string.random_picks)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white_24dp)
        }

        with(intent) {
            filter = getStringExtra(RANDOM_SEARCH) ?: ""
            categoryId = getStringExtra(RANDOM_CAT) ?: ""
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // Older versions defaulted to "1", which made no sense for a grid of picks; treat legacy
        // values as unset and persist the new default so the settings screen stays truthful.
        val stored = prefs.getString(getString(R.string.random_count_pref), null)?.toIntOrNull()
        count = stored?.takeIf { it in MIN_COUNT..MAX_COUNT } ?: DEFAULT_COUNT
        if (stored != count) {
            prefs.edit().putString(getString(R.string.random_count_pref), count.toString()).apply()
        }

        list = findViewById(R.id.random_list)
        emptyView = findViewById(R.id.random_empty)
        progress = findViewById(R.id.random_progress)
        adapter = RandomPicksAdapter(lifecycleScope) { startDetailsActivity(it.id) }
        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        val layoutManager = GridLayoutManager(this, spanCount)
        list.layoutManager = layoutManager
        list.adapter = adapter
        // The picker runs in freeform windows too, so derive the column count from the actual width.
        list.addOnLayoutChangeListener { v, left, _, right, _, _, _, _, _ ->
            val density = resources.displayMetrics.density
            val columns = (((right - left) / (MIN_ITEM_WIDTH_DP * density)).toInt()).coerceIn(2, 6)
            if (layoutManager.spanCount != columns)
                layoutManager.spanCount = columns
        }
        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.random_picks_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // This screen has no navigation drawer, so skip the drawer that BaseActivity would set up.
    override fun onCreateDrawer() {}

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.random_picks_refresh -> { reload(); return true }
            R.id.random_picks_count -> { changeCount(); return true }
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); return true }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun reload() {
        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        lifecycleScope.launch {
            val entries = tryOrNull {
                DatabaseReader.getRandomArchives(filter, categoryId, count, StatusFilter.None)
            }.orEmpty()

            progress.visibility = View.GONE
            list.visibility = View.VISIBLE
            adapter.submitList(entries)
            emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun changeCount() {
        val builder = AlertDialog.Builder(this).apply {
            setTitle(R.string.random_count_dialog_title)
            val textField = EditText(this@RandomPicksActivity)
            textField.inputType = InputType.TYPE_CLASS_NUMBER
            textField.setText(count.toString())
            setView(textField)
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                val newCount = tryOrNull { textField.text.toString().toInt() } ?: count
                count = newCount.coerceIn(MIN_COUNT, MAX_COUNT)
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@RandomPicksActivity)
                prefs.edit().putString(getString(R.string.random_count_pref), count.toString()).apply()
                dialog.dismiss()
                reload()
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
        }
        builder.create().show()
    }

    companion object {
        private const val DEFAULT_COUNT = 12
        private const val MIN_COUNT = 4
        private const val MAX_COUNT = 60
        private const val MIN_ITEM_WIDTH_DP = 150f
    }
}
