package com.iptvapp.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index("categoryId"), // getChannelsByCategory / setFavoriteForCategory
        Index("num"),        // default ORDER BY
        Index("isFavorite"), // getFavoriteChannels
        Index("lastWatched") // getRecentChannels
    ]
)
data class ChannelEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val tvArchive: Int,
    val num: Int,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index("type")] // getCategoriesByType / deleteCategoriesByType
)
data class CategoryEntity(
    @PrimaryKey val categoryId: String,
    val categoryName: String,
    val parentId: Int,
    val type: String,
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "vod_streams",
    indices = [Index("categoryId")]
)
data class VodEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val rating: String?,
    val containerExtension: String,
    val added: String?,
    val isFavorite: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "series",
    indices = [Index("categoryId")]
)
data class SeriesEntity(
    @PrimaryKey val seriesId: Int,
    val name: String,
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val rating: String?,
    val categoryId: String?,
    val isFavorite: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "epg_entries",
    indices = [
        Index("streamId", "startTimestamp"), // getEpgForStream(s) ORDER BY start
        Index("stopTimestamp")               // deleteExpiredEpg
    ]
)
data class EpgEntity(
    @PrimaryKey val id: String,
    val streamId: Int,
    val title: String,
    val description: String,
    val startTimestamp: Long,
    val stopTimestamp: Long,
    val nowPlaying: Int,
    val hasArchive: Int
)
