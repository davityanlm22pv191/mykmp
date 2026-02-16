package com.example.mykmp.data.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual fun createPlatformEngine(): HttpClientEngine = Js.create()
