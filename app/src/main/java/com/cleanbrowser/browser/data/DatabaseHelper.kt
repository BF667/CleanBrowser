package com.cleanbrowser.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "cleanbrowser.db", null, 3) {

    companion object {
        const val TABLE_BOOKMARKS = "bookmarks"
        const val TABLE_HISTORY = "history"
        const val TABLE_TAB_FOLDERS = "tab_folders"
        const val TABLE_USERS = "users"

        const val COL_ID = "id"
        const val COL_USER_ID = "user_id"
        const val COL_TITLE = "title"
        const val COL_URL = "url"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_FOLDER_NAME = "folder_name"
        const val COL_FOLDER_COLOR = "folder_color"
        const val COL_EMAIL = "email"
        const val COL_PASSWORD_HASH = "password_hash"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_AUTH_TYPE = "auth_type"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_USERS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EMAIL TEXT UNIQUE NOT NULL,
                $COL_PASSWORD_HASH TEXT NOT NULL DEFAULT '',
                $COL_DISPLAY_NAME TEXT NOT NULL DEFAULT '',
                $COL_AUTH_TYPE TEXT NOT NULL DEFAULT 'email',
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_BOOKMARKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USER_ID TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL DEFAULT '',
                $COL_URL TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USER_ID TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL DEFAULT '',
                $COL_URL TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_TAB_FOLDERS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USER_ID TEXT NOT NULL,
                $COL_FOLDER_NAME TEXT NOT NULL DEFAULT 'Group',
                $COL_FOLDER_COLOR INTEGER NOT NULL DEFAULT 0,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKMARKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TAB_FOLDERS")
        onCreate(db)
    }

    // ── Users (local auth) ────────────────────────────────────────────────

    fun createUser(email: String, password: String, displayName: String): Long {
        val hash = sha256(password)
        val values = ContentValues().apply {
            put(COL_EMAIL, email.lowercase())
            put(COL_PASSWORD_HASH, hash)
            put(COL_DISPLAY_NAME, displayName)
            put(COL_AUTH_TYPE, "email")
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun createGoogleUser(email: String, displayName: String): Long {
        val values = ContentValues().apply {
            put(COL_EMAIL, email.lowercase())
            put(COL_PASSWORD_HASH, "")
            put(COL_DISPLAY_NAME, displayName)
            put(COL_AUTH_TYPE, "google")
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun authenticateUser(email: String, password: String): Pair<Boolean, String> {
        val hash = sha256(password)
        val cursor = readableDatabase.query(
            TABLE_USERS, arrayOf(COL_DISPLAY_NAME),
            "$COL_EMAIL = ? AND $COL_PASSWORD_HASH = ?",
            arrayOf(email.lowercase(), hash), null, null, null
        )
        val exists = cursor.moveToFirst()
        val name = if (exists) cursor.getString(0) else ""
        cursor.close()
        return Pair(exists, name)
    }

    fun getUserDisplayName(email: String): String {
        val cursor = readableDatabase.query(
            TABLE_USERS, arrayOf(COL_DISPLAY_NAME),
            "$COL_EMAIL = ?", arrayOf(email.lowercase()),
            null, null, null
        )
        val name = if (cursor.moveToFirst()) cursor.getString(0) else ""
        cursor.close()
        return name
    }

    fun deleteUser(email: String) {
        writableDatabase.delete(TABLE_USERS, "$COL_EMAIL = ?", arrayOf(email.lowercase()))
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    fun addBookmark(userId: String, title: String, url: String) {
        val db = writableDatabase
        val cursor = db.query(
            TABLE_BOOKMARKS, arrayOf(COL_ID),
            "$COL_USER_ID = ? AND $COL_URL = ?",
            arrayOf(userId, url), null, null, null
        )
        if (cursor.moveToFirst()) { cursor.close(); return }
        cursor.close()

        val values = ContentValues().apply {
            put(COL_USER_ID, userId)
            put(COL_TITLE, title)
            put(COL_URL, url)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        db.insert(TABLE_BOOKMARKS, null, values)
    }

    fun removeBookmark(userId: String, url: String) {
        writableDatabase.delete(
            TABLE_BOOKMARKS,
            "$COL_USER_ID = ? AND $COL_URL = ?",
            arrayOf(userId, url)
        )
    }

    fun clearBookmarks(userId: String) {
        writableDatabase.delete(TABLE_BOOKMARKS, "$COL_USER_ID = ?", arrayOf(userId))
    }

    fun isBookmarked(userId: String, url: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_BOOKMARKS, arrayOf(COL_ID),
            "$COL_USER_ID = ? AND $COL_URL = ?",
            arrayOf(userId, url), null, null, null
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getBookmarks(userId: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val cursor = readableDatabase.query(
            TABLE_BOOKMARKS, arrayOf(COL_TITLE, COL_URL),
            "$COL_USER_ID = ?", arrayOf(userId),
            null, null, "$COL_TIMESTAMP DESC"
        )
        while (cursor.moveToNext()) {
            list.add(Pair(cursor.getString(0), cursor.getString(1)))
        }
        cursor.close()
        return list
    }

    // ── History ────────────────────────────────────────────────────────────

    fun addHistory(userId: String, title: String, url: String) {
        val values = ContentValues().apply {
            put(COL_USER_ID, userId)
            put(COL_TITLE, title)
            put(COL_URL, url)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_HISTORY, null, values)
    }

    fun clearHistory(userId: String) {
        writableDatabase.delete(TABLE_HISTORY, "$COL_USER_ID = ?", arrayOf(userId))
    }

    fun getHistory(userId: String): List<Triple<String, String, Long>> {
        val list = mutableListOf<Triple<String, String, Long>>()
        val cursor = readableDatabase.query(
            TABLE_HISTORY, arrayOf(COL_TITLE, COL_URL, COL_TIMESTAMP),
            "$COL_USER_ID = ?", arrayOf(userId),
            null, null, "$COL_TIMESTAMP DESC", "200"
        )
        while (cursor.moveToNext()) {
            list.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
        }
        cursor.close()
        return list
    }

    // ── Tab Folders ────────────────────────────────────────────────────────

    fun createFolder(userId: String, name: String, color: Int): Long {
        val values = ContentValues().apply {
            put(COL_USER_ID, userId)
            put(COL_FOLDER_NAME, name)
            put(COL_FOLDER_COLOR, color)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE_TAB_FOLDERS, null, values)
    }

    fun getFolders(userId: String): List<Triple<Long, String, Int>> {
        val list = mutableListOf<Triple<Long, String, Int>>()
        val cursor = readableDatabase.query(
            TABLE_TAB_FOLDERS, arrayOf(COL_ID, COL_FOLDER_NAME, COL_FOLDER_COLOR),
            "$COL_USER_ID = ?", arrayOf(userId),
            null, null, "$COL_TIMESTAMP DESC"
        )
        while (cursor.moveToNext()) {
            list.add(Triple(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)))
        }
        cursor.close()
        return list
    }

    fun deleteFolder(folderId: Long) {
        writableDatabase.delete(TABLE_TAB_FOLDERS, "$COL_ID = ?", arrayOf(folderId.toString()))
    }

    fun renameFolder(folderId: Long, newName: String) {
        val values = ContentValues().apply { put(COL_FOLDER_NAME, newName) }
        writableDatabase.update(TABLE_TAB_FOLDERS, values, "$COL_ID = ?", arrayOf(folderId.toString()))
    }

    // ── Clear all data for user ────────────────────────────────────────────

    fun clearAllUserData(userId: String) {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, "$COL_USER_ID = ?", arrayOf(userId))
        db.delete(TABLE_BOOKMARKS, "$COL_USER_ID = ?", arrayOf(userId))
        db.delete(TABLE_TAB_FOLDERS, "$COL_USER_ID = ?", arrayOf(userId))
    }
}