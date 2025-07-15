package com.example.running.utils

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths


fun readResourceAsString(resourcePath: String): String {
    val inputStream: InputStream? = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
    requireNotNull(inputStream) { "Resource not found: $resourcePath" }
    return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}