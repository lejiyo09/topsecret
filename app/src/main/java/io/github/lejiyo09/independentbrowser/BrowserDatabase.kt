package io.github.lejiyo09.independentbrowser

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * History and bookmarks, stored in the app's private database directory.
 * Pass name = null for an in-memory database (tests).
 */
class BrowserDatabase(context: Context, name: String? = DB_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    data class Entry(val id: Long, val url: String, val title: String, val time: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE history (_id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, " +
                "title TEXT NOT NULL DEFAULT '', visited_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX history_visited ON history(visited_at)")
        db.execSQL(
            "CREATE TABLE bookmarks (_id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, " +
                "title TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---- history ----

    fun recordVisit(url: String, title: String?, now: Long = System.currentTimeMillis()) {
        if (!UrlInput.isWebUrl(url)) return
        val db = writableDatabase
        // A redirect or reload of the page just visited updates that row instead of adding a new one.
        db.rawQuery("SELECT _id, url FROM history ORDER BY visited_at DESC, _id DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst() && c.getString(1) == url) {
                val values = ContentValues().apply {
                    put("visited_at", now)
                    if (!title.isNullOrBlank()) put("title", title)
                }
                db.update("history", values, "_id = ?", arrayOf(c.getLong(0).toString()))
                return
            }
        }
        db.insert("history", null, ContentValues().apply {
            put("url", url)
            put("title", title.orEmpty())
            put("visited_at", now)
        })
    }

    fun updateTitle(url: String, title: String) {
        if (!UrlInput.isWebUrl(url) || title.isBlank()) return
        writableDatabase.execSQL(
            "UPDATE history SET title = ? WHERE _id = " +
                "(SELECT _id FROM history WHERE url = ? ORDER BY visited_at DESC, _id DESC LIMIT 1)",
            arrayOf(title, url)
        )
        writableDatabase.execSQL("UPDATE bookmarks SET title = ? WHERE url = ? AND title = ''", arrayOf(title, url))
    }

    fun history(filter: String = "", limit: Int = 500): List<Entry> =
        query("history", "visited_at", filter, limit)

    fun deleteHistory(id: Long) {
        writableDatabase.delete("history", "_id = ?", arrayOf(id.toString()))
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }

    // ---- bookmarks ----

    fun isBookmarked(url: String?): Boolean {
        if (url == null) return false
        readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE url = ? LIMIT 1", arrayOf(url)).use { c ->
            return c.moveToFirst()
        }
    }

    fun addBookmark(url: String, title: String?, now: Long = System.currentTimeMillis()): Boolean {
        if (!UrlInput.isWebUrl(url)) return false
        val values = ContentValues().apply {
            put("url", url)
            put("title", title.orEmpty())
            put("created_at", now)
        }
        return writableDatabase.insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun removeBookmark(url: String) {
        writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
    }

    fun deleteBookmark(id: Long) {
        writableDatabase.delete("bookmarks", "_id = ?", arrayOf(id.toString()))
    }

    fun bookmarks(filter: String = "", limit: Int = 1000): List<Entry> =
        query("bookmarks", "created_at", filter, limit)

    private fun query(table: String, timeColumn: String, filter: String, limit: Int): List<Entry> {
        val args = mutableListOf<String>()
        val where = if (filter.isBlank()) {
            ""
        } else {
            val pattern = "%" + filter.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            args += pattern
            args += pattern
            "WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\'"
        }
        val sql = "SELECT _id, url, title, $timeColumn FROM $table $where ORDER BY $timeColumn DESC, _id DESC LIMIT $limit"
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { it.toEntries() }
    }

    private fun Cursor.toEntries(): List<Entry> {
        val result = ArrayList<Entry>(count)
        while (moveToNext()) {
            result += Entry(getLong(0), getString(1), getString(2), getLong(3))
        }
        return result
    }

    companion object {
        const val DB_NAME = "browser.db"
        private const val VERSION = 1
    }
}
