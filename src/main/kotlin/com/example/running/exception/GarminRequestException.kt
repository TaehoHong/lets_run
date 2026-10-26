package com.example.running.exception

class GarminRequestException(val status: Int) : RuntimeException("Garmin operation unavailable")
