package com.example.iptv.data.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.iptv.data.model.M3UChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryDatabase @Inject constructor(@ApplicationContext context: Context) : SQLiteOpenHelper(context,
    context.noBackupFilesDir.resolve("library.db").absolutePath, null, 3) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.execSQL("PRAGMA auto_vacuum=FULL")
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE categories(cache TEXT,kind TEXT,id TEXT,name TEXT,ord INTEGER,PRIMARY KEY(cache,kind,id))")
        db.execSQL("CREATE TABLE entries(cache TEXT,kind TEXT,id TEXT,name TEXT,category TEXT,poster TEXT,rating REAL,stamp INTEGER,ext TEXT,ord INTEGER,series TEXT,season TEXT,episode TEXT,runtime TEXT,PRIMARY KEY(cache,kind,id))")
        db.execSQL("CREATE INDEX entry_pages ON entries(cache,kind,category,ord)")
        db.execSQL("CREATE INDEX episode_pages ON entries(cache,series,season,ord)")
        db.execSQL("CREATE TABLE freshness(cache TEXT,kind TEXT,time INTEGER,PRIMARY KEY(cache,kind))")
        db.execSQL("CREATE TABLE details(cache TEXT,kind TEXT,id TEXT,plot TEXT,year TEXT,runtime TEXT,genre TEXT,cast TEXT,director TEXT,time INTEGER,used INTEGER,bytes INTEGER,parser INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(cache,kind,id))")
        db.execSQL("CREATE TABLE progress(owner TEXT,identity TEXT,position INTEGER,duration INTEGER,watched INTEGER,seekable INTEGER,updated INTEGER,series TEXT,name TEXT,category TEXT,poster TEXT,url TEXT,agent TEXT,PRIMARY KEY(owner,identity))")
        db.execSQL("CREATE INDEX continuing ON progress(owner,updated DESC)")
        db.execSQL("CREATE TABLE sorts(owner TEXT,kind TEXT,value TEXT,PRIMARY KEY(owner,kind))")
        db.execSQL("CREATE TABLE aliases(owner TEXT,identity TEXT,kind TEXT,id TEXT,PRIMARY KEY(owner,identity))")
        createSeasons(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createSeasons(db)
        if (oldVersion < 3) db.execSQL("ALTER TABLE details ADD COLUMN parser INTEGER NOT NULL DEFAULT 0")
    }
    private fun createSeasons(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS season_favorites(owner TEXT,identity TEXT,series TEXT,season TEXT,name TEXT,category TEXT,category_name TEXT,poster TEXT,PRIMARY KEY(owner,identity))")
    }
    fun saveSeason(account: LibraryAccount, season: SeasonFavorite) {
        writableDatabase.insertWithOnConflict("season_favorites", null, ContentValues().apply {
            put("owner", account.owner); put("identity", season.key(account)); put("series", season.seriesId)
            put("season", season.season); put("name", season.name); put("category", season.category)
            put("category_name", season.categoryName); put("poster", season.poster)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun season(account: LibraryAccount, key: String): SeasonFavorite? = readableDatabase.rawQuery(
        "SELECT series,season,name,category,category_name,poster FROM season_favorites WHERE owner=? AND identity=?",
        arrayOf(account.owner, key)).use { if (it.moveToFirst()) SeasonFavorite(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5)) else null }
    fun hasSeason(account: LibraryAccount, series: String, season: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM entries WHERE cache=? AND kind='EPISODE' AND series=? AND season=? LIMIT 1",
        arrayOf(account.cache, series, season)).use { it.moveToFirst() }

    fun <T> transaction(valid: () -> Boolean = { true }, action: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val value = action(db)
            check(valid()) { "Library account changed" }
            db.setTransactionSuccessful()
            return value
        } finally { db.endTransaction() }
    }
    fun age(cache: String, kind: LibraryKind): Long = readableDatabase.rawQuery(
        "SELECT time FROM freshness WHERE cache=? AND kind=?", arrayOf(cache, kind.name)).use { if (it.moveToFirst()) it.getLong(0) else 0 }
    fun categories(cache: String, kind: LibraryKind, filter: Boolean, offset: Int = 0): List<LibraryCategory> = readableDatabase.rawQuery(
        "SELECT id,name FROM categories WHERE cache=? AND kind=? ${if (filter) "AND instr(lower(name),'adult')=0" else ""} ORDER BY ord LIMIT $PAGE_SIZE OFFSET ?",
        arrayOf(cache, kind.name, offset.coerceAtLeast(0).toString())).use { c -> buildList { while (c.moveToNext()) add(LibraryCategory(c.getString(0), c.getString(1))) } }
    fun clearListing(db: SQLiteDatabase, cache: String, kind: LibraryKind) {
        db.delete("entries", "cache=? AND kind=?", arrayOf(cache, kind.name))
        db.delete("categories", "cache=? AND kind=?", arrayOf(cache, kind.name))
    }
    fun category(db: SQLiteDatabase, cache: String, kind: LibraryKind, category: LibraryCategory, ordinal: Int) {
        db.insertOrThrow("categories", null, ContentValues().apply {
            put("cache", cache); put("kind", kind.name); put("id", category.id); put("name", category.name); put("ord", ordinal)
        })
    }
    fun entry(db: SQLiteDatabase, cache: String, value: LibraryEntry) {
        db.insertWithOnConflict("entries", null, ContentValues().apply {
            put("cache", cache); put("kind", value.kind.name); put("id", value.id); put("name", value.name)
            put("category", value.category); put("poster", value.poster); put("rating", value.rating)
            put("stamp", value.timestamp); put("ext", value.extension); put("ord", value.ordinal)
            put("series", value.seriesId); put("season", value.season); put("episode", value.episode); put("runtime", value.runtime)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun fresh(db: SQLiteDatabase, cache: String, kind: LibraryKind, now: Long) {
        db.insertWithOnConflict("freshness", null, ContentValues().apply { put("cache", cache); put("kind", kind.name); put("time", now) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    private val columns = "e.kind,e.id,e.name,e.category,COALESCE(c.name,''),e.poster,e.rating,e.stamp,e.ext,e.ord,e.series,e.season,e.episode,e.runtime"
    private fun Cursor.entry() = LibraryEntry(LibraryKind.valueOf(getString(0)), getString(1), getString(2), getString(3), getString(4),
        getString(5), if (isNull(6)) null else getDouble(6), getLong(7), getString(8), getInt(9), getString(10), getString(11), getString(12), getString(13))
    private fun join() = "FROM entries e LEFT JOIN categories c ON c.cache=e.cache AND c.kind=CASE WHEN e.kind='EPISODE' THEN 'SERIES' ELSE e.kind END AND c.id=e.category"
    fun page(account: LibraryAccount, kind: LibraryKind? = null, category: String? = null, series: String? = null,
        season: String? = null, query: String? = null, filter: Boolean = true, sort: LibrarySort = LibrarySort.PROVIDER,
        unwatched: Boolean = false, offset: Int = 0, limit: Int = PAGE_SIZE): List<LibraryEntry> {
        val where = mutableListOf("e.cache=?")
        val args = mutableListOf(account.cache)
        fun eq(column: String, value: String?) { if (value != null) { where.add("$column=?"); args.add(value) } }
        eq("e.kind", kind?.name); eq("e.category", category); eq("e.series", series); eq("e.season", season)
        if (!query.isNullOrBlank()) { where.add("instr(lower(e.name),lower(?))>0"); args.add(query.trim()) }
        if (filter) where.add("instr(lower(COALESCE(c.name,'')),'adult')=0")
        if (unwatched) {
            where.add("NOT EXISTS(SELECT 1 FROM progress p WHERE p.owner=? AND p.identity=('library:' || ? || ':' || e.kind || ':' || e.id) AND p.watched=1)")
            args.add(account.owner); args.add(account.owner)
        }
        val order = when (sort) {
            LibrarySort.PROVIDER -> "e.ord,e.id"
            LibrarySort.NAME -> "e.name COLLATE NOCASE,e.ord,e.id"
            LibrarySort.RATING -> "CASE WHEN e.rating>0 THEN 0 ELSE 1 END,e.rating DESC,e.ord,e.id"
            LibrarySort.RECENT -> "e.stamp DESC,e.ord,e.id"
        }
        return readableDatabase.rawQuery("SELECT $columns ${join()} WHERE ${where.joinToString(" AND ")} ORDER BY $order LIMIT ? OFFSET ?",
            (args + limit.coerceIn(1, PAGE_SIZE).toString() + offset.coerceAtLeast(0).toString()).toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(c.entry()) }
        }
    }
    fun category(account: LibraryAccount, kind: LibraryKind, id: String): LibraryCategory? = readableDatabase.rawQuery(
        "SELECT id,name FROM categories WHERE cache=? AND kind=? AND id=?", arrayOf(account.cache, kind.name, id)).use {
        if (it.moveToFirst()) LibraryCategory(it.getString(0), it.getString(1)) else null
    }

    /** Sorted IDs only, streamed by SQLite. Locating a distant result does not retain all title rows. */
    fun browseIndex(account: LibraryAccount, entry: LibraryEntry, category: String? = null,
        series: String? = null, season: String? = null, sort: LibrarySort): Int {
        val where = mutableListOf("e.cache=?", "e.kind=?")
        val args = mutableListOf(account.cache, entry.kind.name)
        for ((column, value) in listOf("e.category" to category, "e.series" to series, "e.season" to season)) {
            if (value != null) { where.add("$column=?"); args.add(value) }
        }
        val order = when (sort) {
            LibrarySort.PROVIDER -> "e.ord,e.id"
            LibrarySort.NAME -> "e.name COLLATE NOCASE,e.ord,e.id"
            LibrarySort.RATING -> "CASE WHEN e.rating>0 THEN 0 ELSE 1 END,e.rating DESC,e.ord,e.id"
            LibrarySort.RECENT -> "e.stamp DESC,e.ord,e.id"
        }
        return readableDatabase.rawQuery("SELECT e.id FROM entries e WHERE ${where.joinToString(" AND ")} ORDER BY $order", args.toTypedArray()).use { cursor ->
            var index = 0
            while (cursor.moveToNext()) { if (cursor.getString(0) == entry.id) return@use index; index++ }
            -1
        }
    }
    fun find(account: LibraryAccount, kind: LibraryKind, id: String): LibraryEntry? = readableDatabase.rawQuery(
        "SELECT $columns ${join()} WHERE e.cache=? AND e.kind=? AND e.id=?", arrayOf(account.cache, kind.name, id)).use { if (it.moveToFirst()) it.entry() else null }
    fun seasons(account: LibraryAccount, series: String, offset: Int = 0): List<String> = readableDatabase.rawQuery(
        "SELECT season FROM entries WHERE cache=? AND kind='EPISODE' AND series=? GROUP BY season ORDER BY MIN(ord) LIMIT $PAGE_SIZE OFFSET ?", arrayOf(account.cache, series, offset.coerceAtLeast(0).toString())).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }
    fun details(account: LibraryAccount, entry: LibraryEntry, now: Long): Pair<LibraryDetails, Long>? {
        val args = arrayOf(account.cache, entry.kind.name, entry.id)
        val result = readableDatabase.rawQuery("SELECT plot,year,runtime,genre,\"cast\",director,time FROM details WHERE cache=? AND kind=? AND id=?", args).use {
            if (it.moveToFirst()) LibraryDetails(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5)) to it.getLong(6) else null
        }
        if (result != null) writableDatabase.update("details", ContentValues().apply { put("used", now) }, "cache=? AND kind=? AND id=?", args)
        return result
    }
    fun seriesParserCurrent(account: LibraryAccount, entry: LibraryEntry): Boolean = readableDatabase.rawQuery(
        "SELECT parser FROM details WHERE cache=? AND kind='SERIES' AND id=?", arrayOf(account.cache, entry.id)).use {
        it.moveToFirst() && it.getInt(0) == SERIES_PARSER
    }
    fun putDetails(db: SQLiteDatabase, account: LibraryAccount, entry: LibraryEntry, detail: LibraryDetails, now: Long, bytes: Long) {
        db.insertWithOnConflict("details", null, ContentValues().apply {
            put("cache", account.cache); put("kind", entry.kind.name); put("id", entry.id); put("plot", detail.synopsis)
            put("year", detail.year); put("runtime", detail.runtime); put("genre", detail.genre); put("cast", detail.cast)
            put("parser", SERIES_PARSER)
            put("director", detail.director); put("time", now); put("used", now); put("bytes", bytes)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        evict(db, account.cache, entry.kind, entry.id)
    }
    fun evict(db: SQLiteDatabase, protectedCache: String, kind: LibraryKind, id: String, budget: Long = DETAIL_BUDGET) {
        var total = db.rawQuery("SELECT COALESCE(SUM(bytes),0) FROM details", null).use { it.moveToFirst(); it.getLong(0) }
        db.rawQuery("SELECT cache,kind,id,bytes FROM details WHERE NOT(cache=? AND kind=? AND id=?) ORDER BY used", arrayOf(protectedCache, kind.name, id)).use { c ->
            while (total > budget && c.moveToNext()) {
                val cache = c.getString(0); val type = c.getString(1); val item = c.getString(2)
                db.delete("details", "cache=? AND kind=? AND id=?", arrayOf(cache, type, item))
                if (type == LibraryKind.SERIES.name) db.delete("entries", "cache=? AND kind='EPISODE' AND series=?", arrayOf(cache, item))
                total -= c.getLong(3)
            }
        }
    }
    fun progress(owner: String, key: String): WatchProgress = readableDatabase.rawQuery(
        "SELECT position,duration,watched,seekable,updated FROM progress WHERE owner=? AND identity=?", arrayOf(owner, key)).use {
        if (it.moveToFirst()) WatchProgress(it.getLong(0), it.getLong(1), it.getInt(2) != 0, it.getInt(3) != 0, it.getLong(4)) else WatchProgress()
    }
    fun alias(db: SQLiteDatabase, owner: String, key: String, kind: LibraryKind, id: String) {
        db.insertWithOnConflict("aliases", null, ContentValues().apply { put("owner", owner); put("identity", key); put("kind", kind.name); put("id", id) }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun alias(owner: String, key: String): Pair<LibraryKind, String>? = readableDatabase.rawQuery(
        "SELECT kind,id FROM aliases WHERE owner=? AND identity=?", arrayOf(owner, key)).use {
        if (it.moveToFirst()) LibraryKind.valueOf(it.getString(0)) to it.getString(1) else null
    }
    fun storedPlayable(owner: String, key: String): M3UChannel? = readableDatabase.rawQuery(
        "SELECT name,category,poster,url,agent FROM progress WHERE owner=? AND identity=?", arrayOf(owner, key)).use {
        if (it.moveToFirst()) M3UChannel(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), null, null, true) else null
    }
    fun storedSeries(owner: String, key: String): String = readableDatabase.rawQuery(
        "SELECT series FROM progress WHERE owner=? AND identity=?", arrayOf(owner, key)).use { if (it.moveToFirst()) it.getString(0) else "" }
    fun nextEpisode(account: LibraryAccount, episode: LibraryEntry): LibraryEntry? = readableDatabase.rawQuery(
        "SELECT $columns ${join()} WHERE e.cache=? AND e.kind='EPISODE' AND e.series=? AND e.ord>? ORDER BY e.ord,e.id LIMIT 1",
        arrayOf(account.cache, episode.seriesId, episode.ordinal.toString())).use { if (it.moveToFirst()) it.entry() else null }
    fun saveProgress(owner: String, key: String, channel: M3UChannel, series: String, value: WatchProgress) {
        writableDatabase.insertWithOnConflict("progress", null, ContentValues().apply {
            put("owner", owner); put("identity", key); put("position", value.position); put("duration", value.duration)
            put("watched", if (value.watched) 1 else 0); put("seekable", if (value.seekable) 1 else 0); put("updated", value.updated)
            put("series", series); put("name", channel.name); put("category", channel.groupTitle); put("poster", channel.logoUrl)
            put("url", channel.streamUrl); put("agent", channel.userAgent)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun continuing(owner: String, filter: Boolean, offset: Int): List<Pair<String, M3UChannel>> = readableDatabase.rawQuery(
        """SELECT p.identity,p.name,p.category,p.poster,p.url,p.agent FROM progress p
        WHERE p.owner=? AND p.watched=0 AND p.seekable=1 AND p.position>=30000 AND p.duration>0
        ${if (filter) "AND instr(lower(p.category),'adult')=0" else ""}
        AND (p.series='' OR NOT EXISTS(SELECT 1 FROM progress q WHERE q.owner=p.owner AND q.series=p.series AND q.watched=0 AND q.seekable=1 AND q.position>=30000 AND q.duration>0 AND (q.updated>p.updated OR (q.updated=p.updated AND q.identity>p.identity))))
        ORDER BY p.updated DESC,p.identity LIMIT $PAGE_SIZE OFFSET ?""".trimIndent(), arrayOf(owner, offset.coerceAtLeast(0).toString())).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0) to M3UChannel(c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), null, null, isVod = true)) }
    }
    fun sort(owner: String, kind: LibraryKind): LibrarySort = readableDatabase.rawQuery("SELECT value FROM sorts WHERE owner=? AND kind=?", arrayOf(owner, kind.name)).use {
        if (it.moveToFirst()) runCatching { LibrarySort.valueOf(it.getString(0)) }.getOrDefault(LibrarySort.PROVIDER) else LibrarySort.PROVIDER
    }
    fun saveSort(owner: String, kind: LibraryKind, value: LibrarySort) {
        writableDatabase.insertWithOnConflict("sorts", null, ContentValues().apply { put("owner", owner); put("kind", kind.name); put("value", value.name) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    companion object { const val PAGE_SIZE = 60; const val DETAIL_BUDGET = 20L * 1024 * 1024; private const val SERIES_PARSER = 1 }
}
