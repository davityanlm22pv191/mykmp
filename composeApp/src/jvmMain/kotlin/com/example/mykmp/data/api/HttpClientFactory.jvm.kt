package com.example.mykmp.data.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual fun createPlatformEngine(): HttpClientEngine = CIO.create()
