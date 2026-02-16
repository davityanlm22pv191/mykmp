package com.example.mykmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform