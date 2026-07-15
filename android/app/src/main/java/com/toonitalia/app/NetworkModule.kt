package com.toonitalia.app

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val BASE_URL = "https://toonitalia.xyz"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "it-IT,it;q=0.9")
                .build()
            chain.proceed(request)
        }
        .build()

    fun buildUrl(path: String): String {
        return if (path.startsWith("http")) path else "$BASE_URL$path"
    }

    fun fetch(url: String): String {
        val request = Request.Builder()
            .url(buildUrl(url))
            .build()
        val response = client.newCall(request).execute()
        return try {
            response.body?.string() ?: ""
        } finally {
            response.close()
        }
    }
}
