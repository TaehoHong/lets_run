package com.example.running.domain.shoe.service

import com.example.running.domain.shoe.entity.Shoe
import com.example.running.domain.shoe.repository.ShoeRepository
import com.example.running.domain.shoe.service.dto.ShoePatchDto
import com.example.running.domain.user.entity.User
import com.example.running.exception.ApiException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class ShoeServiceActiveMainTest {
    private val repository = mock(ShoeRepository::class.java)
    private val service = ShoeService(repository)
    private val shoe = Shoe(id = 11, user = User(7), brand = "test", model = "test", targetDistance = null, isMain = true)

    @Test
    fun `storing and deleting main shoe clears main and enabling does not restore it`() {
        `when`(repository.findById(11)).thenReturn(Optional.of(shoe))
        `when`(repository.save(shoe)).thenReturn(shoe)
        assertFalse(service.patch(7, 11, ShoePatchDto(isEnabled = false)).isMain)
        assertFalse(service.patch(7, 11, ShoePatchDto(isEnabled = true)).isMain)
        shoe.isMain = true
        assertFalse(service.patch(7, 11, ShoePatchDto(isDeleted = true)).isMain)
    }

    @Test
    fun `brand-only patch keeps active main shoe selected`() {
        `when`(repository.findById(11)).thenReturn(Optional.of(shoe))
        `when`(repository.save(shoe)).thenReturn(shoe)
        assertTrue(service.patch(7, 11, ShoePatchDto(brand = "changed")).isMain)
    }

    @Test
    fun `invalid representative request does not clear the existing main shoe`() {
        `when`(repository.findById(11)).thenReturn(Optional.of(shoe))
        assertThrows(ApiException::class.java) { service.updateToMain(8, 11) }
        assertTrue(shoe.isMain)
        verify(repository, never()).updateIsMainByUserId(8, false)
        shoe.isEnabled = false
        assertThrows(ApiException::class.java) { service.updateToMain(7, 11) }
        verify(repository, never()).updateIsMainByUserId(7, false)
        `when`(repository.findById(99)).thenReturn(Optional.empty())
        assertThrows(ApiException::class.java) { service.updateToMain(7, 99) }
        verify(repository, never()).updateIsMainByUserId(7, false)
    }
}
