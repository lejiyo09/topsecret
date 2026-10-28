package io.github.lejiyo09.independentbrowser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import io.github.lejiyo09.independentbrowser.databinding.ActivityEntriesBinding
import io.github.lejiyo09.independentbrowser.databinding.ItemEntryBinding

/** Shows either the history or the bookmarks list. Click opens in the current tab, long-click in a new tab. */
class EntryListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntriesBinding
    private lateinit var database: BrowserDatabase
    private lateinit var adapter: EntryAdapter
    private val isHistory: Boolean get() = intent.getStringExtra(EXTRA_MODE) == MODE_HISTORY

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEntriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBarsAndIme()
        database = BrowserDatabase(this)

        binding.title.setText(if (isHistory) R.string.history_title else R.string.bookmarks_title)
        binding.empty.setText(if (isHistory) R.string.empty_history else R.string.empty_bookmarks)
        binding.btnClose.setOnClickListener { finish() }
        binding.btnClearAll.isVisible = isHistory
        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.confirm_clear_history)
                .setPositiveButton(R.string.delete) { _, _ ->
                    database.clearHistory()
                    reload()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        adapter = EntryAdapter(this)
        binding.list.adapter = adapter
        binding.list.setOnItemClickListener { _, _, position, _ -> finishWith(adapter.getItem(position)!!, newTab = false) }
        binding.list.setOnItemLongClickListener { _, _, position, _ ->
            finishWith(adapter.getItem(position)!!, newTab = true)
            true
        }
        binding.search.doAfterTextChanged { reload() }
        reload()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun reload() {
        val filter = binding.search.text?.toString().orEmpty()
        val entries = if (isHistory) database.history(filter) else database.bookmarks(filter)
        adapter.clear()
        adapter.addAll(entries)
        binding.empty.isVisible = entries.isEmpty()
        binding.list.isVisible = entries.isNotEmpty()
    }

    private fun delete(entry: BrowserDatabase.Entry) {
        if (isHistory) database.deleteHistory(entry.id) else database.deleteBookmark(entry.id)
        reload()
    }

    private fun finishWith(entry: BrowserDatabase.Entry, newTab: Boolean) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, entry.url).putExtra(EXTRA_NEW_TAB, newTab))
        finish()
    }

    private inner class EntryAdapter(context: Context) : ArrayAdapter<BrowserDatabase.Entry>(context, 0) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = convertView?.tag as? ItemEntryBinding
                ?: ItemEntryBinding.inflate(layoutInflater, parent, false).also { it.root.tag = it }
            val entry = getItem(position)!!
            item.entryTitle.text = entry.title.ifBlank { getString(R.string.untitled) }
            item.entryUrl.text = if (isHistory) {
                DateUtils.formatDateTime(
                    context, entry.time,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
                ) + " · " + entry.url
            } else {
                entry.url
            }
            item.entryDelete.setOnClickListener { delete(entry) }
            return item.root
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_URL = "url"
        const val EXTRA_NEW_TAB = "new_tab"
        const val MODE_HISTORY = "history"
        const val MODE_BOOKMARKS = "bookmarks"

        fun intent(context: Context, mode: String): Intent =
            Intent(context, EntryListActivity::class.java).putExtra(EXTRA_MODE, mode)
    }
}
