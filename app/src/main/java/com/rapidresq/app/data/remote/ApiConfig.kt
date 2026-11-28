package com.rapidresq.app.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiConfig {
    const val BASE_URL = "https://rapidresq-api-3x8n.onrender.com"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
