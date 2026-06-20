package com.iptvapp.data.repository

import android.util.Base64
import androidx.room.withTransaction
import com.iptvapp.data.api.*
import com.iptvapp.data.local.IptvDatabase
import com.iptvapp.data.local.PreferencesManager
import com.iptvapp.data.local.entities.*
import com.iptvapp.util.Resource
import com.iptvapp.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamRepository @Inject constructor(
    private val api: XtreamApiService,
    private val db: IptvDatabase,
    private val prefs: PreferencesManager
) {
    private suspend fun creds() = prefs.credentials.first()

    private suspend fun urlBuilder(): XtreamUrlBuilder {
        val c = creds()
        return XtreamUrlBuilder(c.serverUrl, c.username, c.password)
    }

    suspend fun authenticate(serverUrl: String, username: String, password: String): Resource<XtreamAuthResponse> {
        val builder = XtreamUrlBuilder(serverUrl, username, password)
        return safeApiCall {
            val response = api.authenticate(builder.apiUrl(), username, password)
            if (!response.isSuccessful) throw Exception("Server returned ${response.code()}")
            val body = response.body() ?: throw Exception("Empty response from server")
            // userInfo may be absent if the server returns an error object.
            val status = body.userInfo?.status
                ?: throw Exception("Invalid credentials or server response")
            if (status != "Active") throw Exception("Account is not active (status: $status)")
            prefs.saveCredentials(serverUrl, username, password)
            body
        }
    }

    suspend fun logout() {
        prefs.clearCredentials()
        db.clearAllTables()
    }

    suspend fun fetchLiveCategories(): Resource<List<Category>> = safeApiCall {
        withContext(Dispatchers.IO) {
            val b = urlBuilder(); val c = creds()
            val response = api.getLiveCategories(b.apiUrl(), c.username, c.password)
            // Do NOT mutate the cache on an error response — an empty body from
            // a 401/500 would otherwise wipe the user's categories.
            if (!response.isSuccessful) throw Exception("Server returned ${response.code()}")
            val list = response.body() ?: emptyList()
            val entities = list.mapNotNull { cat ->
                val id = cat.categoryId ?: return@mapNotNull null
                CategoryEntity(id, cat.categoryName ?: "", cat.parentId, "live")
            }
            db.withTransaction {
                db.categoryDao().deleteCategoriesByType("live")
                db.categoryDao().upsertCategories(entities)
            }
            list
        }
    }

    fun getLiveCategories(): Flow<List<CategoryEntity>> =
        db.categoryDao().getCategoriesByType("live")

    suspend fun fetchLiveStreams(): Resource<List<LiveStream>> = safeApiCall {
        withContext(Dispatchers.IO) {
            val b = urlBuilder(); val c = creds()
            val response = api.getLiveStreams(b.apiUrl(), c.username, c.password)
            if (!response.isSuccessful) throw Exception("Server returned ${response.code()}")
            val list = response.body() ?: emptyList()
            // Preserve user-local fields (favorite / lastWatched) across refresh.
            val existing = db.channelDao().getAllChannelsOnce().associateBy { it.streamId }
            val entities = list.mapNotNull { s ->
                if (s.streamId == 0) return@mapNotNull null
                val prev = existing[s.streamId]
                ChannelEntity(
                    streamId = s.streamId,
                    name = s.name ?: "Unknown",
                    streamIcon = s.streamIcon,
                    categoryId = s.categoryId,
                    epgChannelId = s.epgChannelId,
                    tvArchive = s.tvArchive,
                    num = s.num,
                    isFavorite = prev?.isFavorite ?: false,
                    lastWatched = prev?.lastWatched
                )
            }
            db.channelDao().upsertChannels(entities)
            list
        }
    }

    fun getAllChannels(): Flow<List<ChannelEntity>> = db.channelDao().getAllChannels()

    fun getChannelsByCategory(categoryId: String): Flow<List<ChannelEntity>> =
        db.channelDao().getChannelsByCategory(categoryId)

    fun searchChannels(query: String): Flow<List<ChannelEntity>> =
        db.channelDao().searchChannels(query)

    fun getFavoriteChannels(): Flow<List<ChannelEntity>> =
        db.channelDao().getFavoriteChannels()

    fun getFavoriteLiveCategoryIds(): Flow<Set<String>> =
        prefs.favoriteLiveCategoryIds

    fun getRecentChannels(): Flow<List<ChannelEntity>> =
        db.channelDao().getRecentChannels()

    suspend fun toggleChannelFavorite(streamId: Int) {
        val ch = db.channelDao().getChannelById(streamId) ?: return
        db.channelDao().setFavorite(streamId, !ch.isFavorite)
    }

    suspend fun markChannelWatched(streamId: Int) =
        db.channelDao().updateLastWatched(streamId, System.currentTimeMillis())

    suspend fun setLiveCategoryFavorite(categoryId: String, isFavorite: Boolean) {
        db.channelDao().setFavoriteForCategory(categoryId, isFavorite)

        if (isFavorite) {
            prefs.addFavoriteLiveCategoryId(categoryId)
        } else {
            prefs.removeFavoriteLiveCategoryId(categoryId)
        }
    }

    suspend fun getLiveStreamUrl(streamId: Int): String {
        val format = prefs.preferredFormat.first()
        return urlBuilder().liveStreamUrl(streamId, format)
    }

    suspend fun fetchVodStreams(): Resource<List<VodStream>> = safeApiCall {
        withContext(Dispatchers.IO) {
            val b = urlBuilder(); val c = creds()
            val response = api.getVodStreams(b.apiUrl(), c.username, c.password)
            if (!response.isSuccessful) throw Exception("Server returned ${response.code()}")
            val list = response.body() ?: emptyList()
            val entities = list.mapNotNull { v ->
                if (v.streamId == 0) return@mapNotNull null
                VodEntity(
                    streamId = v.streamId,
                    name = v.name ?: "Unknown",
                    streamIcon = v.streamIcon,
                    categoryId = v.categoryId,
                    rating = v.rating,
                    containerExtension = v.containerExtension ?: "mp4",
                    added = v.added
                )
            }
            db.vodDao().upsertVod(entities)
            list
        }
    }

    suspend fun fetchVodCategories(): Resource<List<Category>> = safeApiCall {
        withContext(Dispatchers.IO) {
            val b = urlBuilder(); val c = creds()
            val response = api.getVodCategories(b.apiUrl(), c.username, c.password)
            if (!response.isSuccessful) throw Exception("Server returned ${response.code()}")
            val list = response.body() ?: emptyList()
            val entities = list.mapNotNull { cat ->
                val id = cat.categoryId ?: return@mapNotNull null
                CategoryEntity(id, cat.categoryName ?: "", cat.parentId, "vod")
            }
            db.withTransaction {
                db.categoryDao().deleteCategoriesByType("vod")
                db.categoryDao().upsertCategories(entities)
            }
            list
        }
    }

    fun getVodCategories(): Flow<List<CategoryEntity>> = db.categoryDao().getCategoriesByType("vod")

    fun getVodByCategory(categoryId: String): Flow<List<VodEntity>> = db.vodDao().getVodByCategory(categoryId)

    suspend fun getVodStreamUrl(streamId: Int, containerExtension: String): String = urlBuilder().vodStreamUrl(streamId, containerExtension)

    fun getAllVod(): Flow<List<VodEntity>> = db.vodDao().getAllVod()

    suspend fun fetchSeries(): Resource<List<Series>> = safeApiCall {
        withContext(Dispatchers.IO) {
            val b = urlBuilder(); val c = creds()
            val response = api.getSeries(b.apiUrl(), c.username, c.password)
            if (!response.isSuccessful) throw Exception("Server returned ${response.code()}")
            val list = response.body() ?: emptyList()
            val entities = list.mapNotNull { s ->
                if (s.seriesId == 0) return@mapNotNull null
                SeriesEntity(
                    seriesId = s.seriesId,
                    name = s.name ?: "Unknown",
                    cover = s.cover,
                    plot = s.plot,
                    genre = s.genre,
                    rating = s.rating,
                    categoryId = s.categoryId
                )
            }
            db.seriesDao().upsertSeries(entities)
            list
        }
    }

    fun getAllSeries(): Flow<List<SeriesEntity>> = db.seriesDao().getAllSeries()

    suspend fun fetchEpg(streamId: Int): Resource<List<EpgEntity>> = safeApiCall {
        withContext(Dispatchers.IO) {
            val b = urlBuilder(); val c = creds()
            val response = api.getShortEpg(b.apiUrl(), c.username, c.password, streamId = streamId)
            if (!response.isSuccessful) throw Exception("Server returned ${response.code()}")
            val list = response.body()?.epgListings ?: emptyList()
            val entities = list.mapNotNull { e ->
                val id = e.id ?: return@mapNotNull null
                EpgEntity(
                    id = id,
                    streamId = streamId,
                    title = decodeBase64(e.title),
                    description = decodeBase64(e.description),
                    startTimestamp = e.startTimestamp,
                    stopTimestamp = e.stopTimestamp,
                    nowPlaying = e.nowPlaying,
                    hasArchive = e.hasArchive
                )
            }
            db.epgDao().upsertEpg(entities)
            entities
        }
    }

    fun getEpgForStream(streamId: Int): Flow<List<EpgEntity>> =
        db.epgDao().getEpgForStream(streamId)

    fun getEpgForStreams(streamIds: List<Int>): Flow<List<EpgEntity>> =
        db.epgDao().getEpgForStreams(streamIds)

    private fun decodeBase64(encoded: String?): String {
        if (encoded.isNullOrEmpty()) return ""
        return try {
            String(Base64.decode(encoded, Base64.DEFAULT))
        } catch (e: Exception) {
            encoded
        }
    }
}
