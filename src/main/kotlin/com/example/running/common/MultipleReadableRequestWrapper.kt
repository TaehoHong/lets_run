package com.example.running.common

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import org.apache.tomcat.util.http.fileupload.IOUtils
import java.io.ByteArrayOutputStream

class MultipleReadableRequestWrapper(request: HttpServletRequest, private val maxBytes: Int? = null) : HttpServletRequestWrapper(request) {
    private val outputStream = ByteArrayOutputStream()

    init {
        if (maxBytes != null) {
            val chunk = ByteArray(8192)
            val input = super.getInputStream()
            while (true) {
                val count = input.read(chunk, 0, minOf(chunk.size, maxBytes - outputStream.size() + 1))
                if (count == -1) break
                if (count > maxBytes - outputStream.size()) throw RequestBodyTooLargeException()
                outputStream.write(chunk, 0, count)
            }
        }
    }

    val contents get() = inputStream.use { it.readBytes() }

    override fun getInputStream(): ServletInputStream {
        if (maxBytes == null) IOUtils.copy(super.getInputStream(), outputStream)
        return object : ServletInputStream() {
            private val buffer = outputStream.toByteArray().inputStream()
            override fun read() = buffer.read()
            override fun isFinished() = buffer.available() == 0
            override fun isReady() = true
            override fun setReadListener(listener: ReadListener?) {}
        }
    }
}
class RequestBodyTooLargeException : RuntimeException("Request body too large")
