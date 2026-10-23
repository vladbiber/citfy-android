package com.ctify.player.radio

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ctify.player.genre.Genre
import org.json.JSONArray
import org.json.JSONObject

enum class StationSource { DIRECTORY, CLIAMP, CUSTOM }

/** A radio station. Stored only when it is a favorite, a recent or a custom one. */
@Entity(tableName = "stations")
data class Station(
    @PrimaryKey val uuid: String,
    val name: String,
    val streamUrl: String,
    val homepage: String? = null,
    val favicon: String? = null,
    /** Radio Browser tags, lowercase, comma separated */
    val tags: String = "",
    val countryCode: String? = null,
    val state: String? = null,
    val codec: String? = null,
    val bitrate: Int = 0,
    val hls: Boolean = false,
    val votes: Int = 0,
    val clickCount: Int = 0,
    val clickTrend: Int = 0,
    val source: StationSource = StationSource.DIRECTORY,
    /** cliamp channels only: people listening right now */
    val listeners: Int? = null,
    /** cliamp song-library channels that are not a live broadcast */
    val description: String? = null,
) {
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val genre: Genre? get() = Genre.dominant(tagList)
    val mediaId: String get() = MEDIA_PREFIX + uuid

    /** "MP3 128k · RO" style line for lists. */
    val detail: String
        get() = listOfNotNull(
            codec?.takeIf { it.isNotBlank() && it != "UNKNOWN" },
            bitrate.takeIf { it > 0 }?.let { "${it}k" },
            countryCode?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    companion object {
        const val MEDIA_PREFIX = "radio:"

        fun toJson(list: List<Station>): String = JSONArray().apply {
            list.forEach { s ->
                put(
                    JSONObject()
                        .put("uuid", s.uuid).put("name", s.name).put("url", s.streamUrl)
                        .put("homepage", s.homepage).put("favicon", s.favicon).put("tags", s.tags)
                        .put("cc", s.countryCode).put("state", s.state).put("codec", s.codec)
                        .put("bitrate", s.bitrate).put("hls", s.hls).put("votes", s.votes)
                        .put("clicks", s.clickCount).put("trend", s.clickTrend).put("source", s.source.name)
                        .put("listeners", s.listeners).put("desc", s.description)
                )
            }
        }.toString()

        fun fromJson(json: String): List<Station> = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Station(
                    uuid = o.getString("uuid"),
                    name = o.getString("name"),
                    streamUrl = o.getString("url"),
                    homepage = o.optString("homepage").ifBlank { null },
                    favicon = o.optString("favicon").ifBlank { null },
                    tags = o.optString("tags"),
                    countryCode = o.optString("cc").ifBlank { null },
                    state = o.optString("state").ifBlank { null },
                    codec = o.optString("codec").ifBlank { null },
                    bitrate = o.optInt("bitrate"),
                    hls = o.optBoolean("hls"),
                    votes = o.optInt("votes"),
                    clickCount = o.optInt("clicks"),
                    clickTrend = o.optInt("trend"),
                    source = runCatching { StationSource.valueOf(o.optString("source")) }.getOrDefault(StationSource.DIRECTORY),
                    listeners = if (o.has("listeners") && !o.isNull("listeners")) o.optInt("listeners") else null,
                    description = o.optString("desc").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
    }
}
