package com.example.simplenotes.di

import android.content.Context
import android.os.Build
import com.example.simplenotes.data.database.NoteDao
import com.example.simplenotes.data.database.NoteDatabase
import com.example.simplenotes.data.remote.ai.AiApiService
import com.example.simplenotes.data.remote.ai.RetrofitClient
import com.example.simplenotes.data.repository.NoteRepositoryImpl
import com.example.simplenotes.data.repository.UserPreferencesRepository
import com.example.simplenotes.domain.repository.NoteRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
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
    fun provideNoteDatabase(@ApplicationContext context: Context): NoteDatabase {
        return NoteDatabase.getInstance(context)
    }

    @Provides
    fun provideNoteDao(database: NoteDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    @Singleton
    fun provideNoteRepository(impl: NoteRepositoryImpl): NoteRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    @Provides
    @Singleton
    fun provideDynamicBaseUrlInterceptor(userPreferencesRepository: UserPreferencesRepository): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            var baseUrl = runBlocking { userPreferencesRepository.aiBaseUrlFlow.first() }
            
            // Tự động nhận diện thiết bị để chọn URL AI phù hợp
            if (baseUrl.contains("10.0.2.2") && !isEmulator()) {
                // Nếu chạy trên máy thật nhưng config là localhost của giả lập, 
                // tự động chuyển sang IP máy tính của bạn trong mạng LAN
                baseUrl = baseUrl.replace("10.0.2.2", "192.168.1.167")
            }

            val httpUrl = baseUrl.toHttpUrlOrNull()
            val newUrl = originalRequest.url.newBuilder()
                .scheme(httpUrl?.scheme ?: "http")
                .host(httpUrl?.host ?: "10.0.2.2")
                .port(httpUrl?.port ?: 11434)
                .build()
            
            chain.proceed(originalRequest.newBuilder().url(newUrl).build())
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(dynamicBaseUrlInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(dynamicBaseUrlInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAiApiService(): AiApiService {
        return RetrofitClient.aiApiService
    }
}
