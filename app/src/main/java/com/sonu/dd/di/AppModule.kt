package com.sonu.dd.di

import android.content.Context
import androidx.room.Room
import com.sonu.dd.core.data.datastore.DDPreferences
import com.sonu.dd.core.data.db.DDDatabase
import com.sonu.dd.core.data.db.DownloadDao
import com.sonu.dd.core.data.db.LibraryDao
import com.sonu.dd.core.data.db.SearchCacheDao
import com.sonu.dd.core.data.db.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DDDatabase {
        return Room.databaseBuilder(
            context,
            DDDatabase::class.java,
            "dd_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSearchCacheDao(db: DDDatabase): SearchCacheDao = db.searchCacheDao()

    @Provides
    fun provideDownloadDao(db: DDDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideLibraryDao(db: DDDatabase): LibraryDao = db.libraryDao()

    @Provides
    fun provideSearchHistoryDao(db: DDDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://yts.mx/api/v2/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DDPreferences {
        return DDPreferences(context)
    }
}
