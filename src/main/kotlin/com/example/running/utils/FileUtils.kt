package com.example.running.utils

import java.nio.file.Files
import java.nio.file.Paths


fun readFileAsString(path: String) = Files.readString(Paths.get(path), Charsets.UTF_8)