package com.iptvapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.iptvapp.data.local.dao.*
import com.iptvapp.data.local.entities.*

@Database(
    entities = [
        ChannelEntity::class,
        CategoryEntity::class,
        VodEntity::class,
        SeriesEntity::class,
        EpgEntity::class
    ],
    // v3: added indices on hot columns (categoryId/num/isFavorite/lastWatched,
    // categories.type, epg streamId+startTimestamp/stopTimestamp). Migration is
    // handled by fallbackToDestructiveMigration in AppModule — this is a
    // server-backed cache, so it simply rebuilds on upgrade.
    version = 3,
    exportSchema = false
)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun categoryDao(): CategoryDao
    abstract fun vodDao(): VodDao
    abstract fun seriesDao(): SeriesDao
    abstract fun epgDao(): EpgDao

    companion object {
        const val DATABASE_NAME = "iptv_db"
    }
}
