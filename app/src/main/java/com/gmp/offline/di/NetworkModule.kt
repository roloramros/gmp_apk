package com.gmp.offline.di

import android.content.Context
import coil.ImageLoader
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.remote.ApiService
import com.gmp.offline.data.remote.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)

    // Coil usa por defecto su propio OkHttpClient interno, sin el
    // AuthInterceptor — por eso las fotos servidas por
    // GET /jobs/:uuid/photos/:photo_uuid/file (que requiere el header
    // Authorization) nunca cargaban: la petición fallaba en silencio y
    // Coil no mostraba nada. Se le pasa acá el mismo cliente autenticado
    // que usa Retrofit, y se registra como default en GmpApplication.
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, client: OkHttpClient): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .build()
}
