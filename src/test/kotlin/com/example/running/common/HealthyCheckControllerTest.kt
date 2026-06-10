package com.example.running.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

class HealthyCheckControllerTest {

    @Test
    fun `healthyCheck returns server name when database connection is valid`() {
        val dataSource = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.isValid(2)).thenReturn(true)
        val controller = HealthyCheckController(
            serverName = "running-backend",
            activeType = "blue",
            dataSource = dataSource,
        )

        val response = controller.healthyCheck()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("running-backend", response.body)
        verify(connection).close()
    }

    @Test
    fun `healthyCheck returns service unavailable without leaking database error`() {
        val dataSource = mock(DataSource::class.java)
        `when`(dataSource.connection).thenThrow(SQLException("jdbc:mariadb://secret-host/running"))
        val controller = HealthyCheckController(
            serverName = "running-backend",
            activeType = "blue",
            dataSource = dataSource,
        )

        val response = controller.healthyCheck()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("unavailable", response.body)
    }

    @Test
    fun `activeType returns configured server type`() {
        val controller = HealthyCheckController(
            serverName = "running-backend",
            activeType = "green",
            dataSource = mock(DataSource::class.java),
        )

        assertEquals("green", controller.activeType())
    }
}
