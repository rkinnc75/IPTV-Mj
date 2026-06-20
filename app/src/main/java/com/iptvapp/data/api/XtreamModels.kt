package com.iptvapp.data.api

import com.google.gson.annotations.SerializedName

// fix: Real-world Xtream providers frequently omit fields or send them with a
// different type than the spec. Gson instantiates these via Unsafe and ignores
// Kotlin non-null types, so an absent object/string/list silently becomes null
// and then NPEs at the use site. Every provider-supplied optional is therefore
// nullable with a default; the repository maps with safe fallbacks.

data class XtreamAuthResponse(
    @SerializedName("user_info") val userInfo: UserInfo? = null,
    @SerializedName("server_info") val serverInfo: ServerInfo? = null
)

data class UserInfo(
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    @SerializedName("is_trial") val isTrial: String? = null,
    @SerializedName("active_cons") val activeCons: String? = null,
    @SerializedName("max_connections") val maxConnections: String? = null,
    @SerializedName("allowed_output_formats") val allowedFormats: List<String>? = null
)

data class ServerInfo(
    @SerializedName("url") val url: String? = null,
    @SerializedName("port") val port: String? = null,
    @SerializedName("https_port") val httpsPort: String? = null,
    @SerializedName("server_protocol") val protocol: String? = null,
    @SerializedName("rtmp_port") val rtmpPort: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("timestamp_now") val timestampNow: Long = 0
)

data class LiveStream(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("stream_type") val streamType: String? = null,
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("tv_archive") val tvArchive: Int = 0,
    @SerializedName("direct_source") val directSource: String? = null
)

data class Category(
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("parent_id") val parentId: Int = 0
)

data class VodStream(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("stream_type") val streamType: String? = null,
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: Double? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = null,
    @SerializedName("custom_sid") val customSid: String? = null,
    @SerializedName("direct_source") val directSource: String? = null
)

data class VodInfo(
    @SerializedName("info") val info: VodInfoDetail? = null,
    @SerializedName("movie_data") val movieData: VodStream? = null
)

data class VodInfoDetail(
    @SerializedName("tmdb_id") val tmdbId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null,
    @SerializedName("releasedate") val releaseDate: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("actors") val actors: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null
)

data class Series(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("series_id") val seriesId: Int = 0,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: Double? = null,
    @SerializedName("category_id") val categoryId: String? = null
)

data class SeriesInfo(
    @SerializedName("info") val info: SeriesInfoDetail? = null,
    @SerializedName("episodes") val episodes: Map<String, List<Episode>>? = null,
    @SerializedName("seasons") val seasons: List<Season>? = null
)

data class SeriesInfoDetail(
    @SerializedName("name") val name: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("rating") val rating: String? = null
)

data class Season(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null
)

data class Episode(
    @SerializedName("id") val id: String? = null,
    @SerializedName("episode_num") val episodeNum: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("season") val season: Int = 0,
    @SerializedName("direct_source") val directSource: String? = null
)

data class EpgResponse(
    @SerializedName("epg_listings") val epgListings: List<EpgEntry>? = null
)

data class EpgEntry(
    @SerializedName("id") val id: String? = null,
    @SerializedName("epg_id") val epgId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("lang") val lang: String? = null,
    @SerializedName("start") val start: String? = null,
    @SerializedName("end") val end: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("start_timestamp") val startTimestamp: Long = 0,
    @SerializedName("stop_timestamp") val stopTimestamp: Long = 0,
    @SerializedName("now_playing") val nowPlaying: Int = 0,
    @SerializedName("has_archive") val hasArchive: Int = 0
)
