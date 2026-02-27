package com.example.mykmp.data.api

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Хранит application context для инициализации Chucker.
 * Инициализируется в MainActivity.onCreate() до создания HttpClient.
 */
object AndroidHttpInit {
    internal var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

actual fun createPlatformEngine(): HttpClientEngine = OkHttp.create {
    val context = AndroidHttpInit.appContext
    if (context != null) {
        addInterceptor(ChuckerInterceptor(context))
    }
}
