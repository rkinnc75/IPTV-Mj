package com.iptvapp.di

import android.content.Context
import androidx.room.Room
import com.iptvapp.BuildConfig
import com.iptvapp.data.api.XtreamApiService
import com.iptvapp.data.local.IptvDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
        // SECURITY: Xtream auth/data calls carry username & password in the
        // request URL. HttpLoggingInterceptor (even at BASIC/HEADERS) writes
        // that URL to logcat — a credential leak in release builds. Only log
        // in debug builds, and only at BASIC.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideXtreamApiService(retrofit: Retrofit): XtreamApiService =
        retrofit.create(XtreamApiService::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IptvDatabase =
        Room.databaseBuilder(
            context,
            IptvDatabase::class.java,
            IptvDatabase.DATABASE_NAME
        )
            // Cache DB (channels/EPG re-fetched from the server). On a schema
            // bump, rebuild rather than crash for lack of a migration.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideChannelDao(db: IptvDatabase) = db.channelDao()
    @Provides fun provideCategoryDao(db: IptvDatabase) = db.categoryDao()
    @Provides fun provideVodDao(db: IptvDatabase) = db.vodDao()
    @Provides fun provideSeriesDao(db: IptvDatabase) = db.seriesDao()
    @Provides fun provideEpgDao(db: IptvDatabase) = db.epgDao()
}
