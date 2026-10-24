package com.example.iptv.data.library

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream

/** One record at a time; the caller writes it directly into a replacement transaction. */
internal object LibraryParser {
    private fun JsonReader.scalar(): String {
        return when (peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> nextString().also { require(it.length <= 64 * 1024) }
            JsonToken.BOOLEAN -> nextBoolean().toString()
            JsonToken.NULL -> { nextNull(); "" }
            else -> { skipValue(); "" }
        }
    }
    private fun JsonReader.fields(): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val metadata = mutableMapOf<String, String>()
        beginObject()
        while (hasNext()) {
            require(fields.size < 256)
            val key = nextName()
            if (key == "info" && peek() == JsonToken.BEGIN_OBJECT) {
                // Descriptive metadata is not a playable route. Merge only optional facts,
                // then apply top-level fields last, regardless of their JSON order.
                fields().filterKeys { it in descriptiveFields }.forEach { (name, value) -> metadata[name] = value }
            }
            else fields[key] = scalar()
        }
        endObject()
        return metadata + fields
    }
    private val descriptiveFields = setOf("plot", "description", "releasedate", "releaseDate", "year", "genre",
        "cast", "actors", "director", "duration", "duration_secs", "movie_image", "stream_icon", "cover", "rating", "rating_5based")
    private fun Map<String, String>.first(vararg keys: String) = keys.firstNotNullOfOrNull { get(it)?.takeIf(String::isNotBlank) }.orEmpty()
    private fun entry(f: Map<String, String>, kind: LibraryKind, ordinal: Int, series: String = "", season: String = ""): LibraryEntry? {
        val id = f.first(if (kind == LibraryKind.SERIES) "series_id" else if (kind == LibraryKind.EPISODE) "id" else "stream_id")
        if (!id.matches(Regex("[0-9]+"))) return null
        return LibraryEntry(kind, id, f.first("name", "title").ifBlank { id }, f.first("category_id"),
            poster = f.first("stream_icon", "cover", "movie_image").takeIf(String::isNotBlank),
            rating = f.first("rating", "rating_5based").toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 },
            timestamp = f.first(if (kind == LibraryKind.SERIES) "last_modified" else "added").toLongOrNull()?.coerceAtLeast(0) ?: 0,
            extension = f.first("container_extension").ifBlank { "mp4" }, ordinal = ordinal,
            seriesId = series, season = season, episode = f.first("episode_num"), runtime = f.first("duration", "duration_secs"))
    }
    fun categories(input: InputStream, emit: (LibraryCategory) -> Unit) = JsonReader(input.reader()).use { reader ->
        reader.beginArray()
        var count = 0
        while (reader.hasNext()) {
            require(++count <= 100_000)
            val f = reader.fields()
            val id = f.first("category_id")
            require(id.isNotBlank())
            emit(LibraryCategory(id, f.first("category_name")))
        }
        reader.endArray(); require(reader.peek() == JsonToken.END_DOCUMENT)
    }
    fun listing(input: InputStream, kind: LibraryKind, emit: (LibraryEntry) -> Unit) = JsonReader(input.reader()).use { reader ->
        reader.beginArray()
        var count = 0
        var valid = 0
        while (reader.hasNext()) {
            require(count < 500_000)
            entry(reader.fields(), kind, count++)?.let { valid++; emit(it) }
        }
        reader.endArray(); require(reader.peek() == JsonToken.END_DOCUMENT)
        require(count == 0 || valid > 0)
    }
    fun details(input: InputStream, series: String?, emit: (LibraryEntry) -> Unit): LibraryDetails = JsonReader(input.reader()).use { r ->
        val f = mutableMapOf<String, String>()
        var count = 0
        var recognized = false
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "info", "movie_data" -> { recognized = true; if (r.peek() == JsonToken.BEGIN_OBJECT) f.putAll(r.fields()) else r.skipValue() }
                "episodes" -> if (series != null && r.peek() == JsonToken.BEGIN_OBJECT) {
                    recognized = true
                    r.beginObject()
                    while (r.hasNext()) {
                        val season = r.nextName()
                        r.beginArray()
                        while (r.hasNext()) {
                            require(count < 50_000)
                            entry(r.fields(), LibraryKind.EPISODE, count++, series, season)?.let(emit)
                        }
                        r.endArray()
                    }
                    r.endObject()
                } else r.skipValue()
                else -> r.skipValue()
            }
        }
        r.endObject(); require(r.peek() == JsonToken.END_DOCUMENT)
        require(recognized)
        LibraryDetails(f.first("plot", "description"), f.first("releasedate", "releaseDate", "year").take(4),
            f.first("duration", "duration_secs"), f.first("genre"), f.first("cast", "actors"), f.first("director"))
    }
}
