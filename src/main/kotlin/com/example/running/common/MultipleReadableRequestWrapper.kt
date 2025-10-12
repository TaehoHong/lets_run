package com.example.running.common

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import org.apache.tomcat.util.http.fileupload.IOUtils
import java.io.ByteArrayOutputStream

class MultipleReadableRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    private val outputStream = ByteArrayOutputStream()

    val contents get() = inputStream.use { it.readBytes() }

    override fun getInputStream(): ServletInputStream {
        IOUtils.copy(super.getInputStream(), outputStream)
        return object : ServletInputStream() {
            private val buffer = outputStream.toByteArray().inputStream()
            override fun read() = buffer.read()
            override fun isFinished() = buffer.available() == 0
            override fun isReady() = true
            override fun setReadListener(listener: ReadListener?) {}
        }
    }
}