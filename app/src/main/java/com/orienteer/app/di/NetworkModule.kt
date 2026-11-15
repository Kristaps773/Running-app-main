package com.orienteer.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.orienteer.app.BuildConfig
import com.orienteer.app.data.api.OsrmApiService
import com.orienteer.app.data.api.OverpassApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * OSRM public foot-routing endpoint.
     * Users can self-host OSRM for production to avoid rate limits.
     */
    private const val OSRM_BASE_URL = "https://routing.openstreetmap.de/routed-foot/"

    /**
     * Overpass API for OSM POI queries.
     */
    private const val OVERPASS_BASE_URL = "https://overpass-api.de/api/"

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    @Named("osrmHttp")
    fun provideOsrmOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(appUserAgentInterceptor())
            .addInterceptor(RetryInterceptor(maxRetries = 1))
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
        return builder.build()
    }

    /** Fail fast — Overpass 502s should not retry and stall route generation. */
    @Provides
    @Singleton
    @Named("overpassHttp")
    fun provideOverpassOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .addInterceptor(appUserAgentInterceptor())
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    @Named("osrm")
    fun provideOsrmRetrofit(@Named("osrmHttp") okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(OSRM_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @Named("overpass")
    fun provideOverpassRetrofit(@Named("overpassHttp") okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(OVERPASS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideOsrmApiService(@Named("osrm") retrofit: Retrofit): OsrmApiService =
        retrofit.create(OsrmApiService::class.java)

    @Provides
    @Singleton
    fun provideOverpassApiService(@Named("overpass") retrofit: Retrofit): OverpassApiService =
        retrofit.create(OverpassApiService::class.java)

    /** Public OSRM/Overpass policies expect an identifiable client, not a blank default agent. */
    private fun appUserAgentInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header(
                "User-Agent",
                "OrienteerRun/${BuildConfig.VERSION_NAME} (Android; com.orienteer.app; routing)"
            )
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }
}
