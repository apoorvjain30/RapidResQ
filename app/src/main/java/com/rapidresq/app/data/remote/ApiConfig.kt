package com.rapidresq.app.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiConfig {
    const val BASE_URL = "http://127.0.0.1:4000"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
