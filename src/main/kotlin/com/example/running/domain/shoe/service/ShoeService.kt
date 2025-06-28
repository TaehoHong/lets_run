package com.example.running.domain.shoe.service

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.shoe.entity.Shoe
import com.example.running.domain.shoe.repository.ShoeRepository
import com.example.running.domain.shoe.service.dto.ShoeCreationDto
import com.example.running.domain.shoe.service.dto.ShoeDto
import com.example.running.domain.shoe.service.dto.ShoePatchDto
import com.example.running.domain.user.entity.User
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShoeService(private val shoeRepository: ShoeRepository) {


    @Transactional
    fun save(shoeCreationDto: ShoeCreationDto): ShoeDto {
        return Shoe(
            user = User(shoeCreationDto.userId),
            brand = shoeCreationDto.brand,
            model = shoeCreationDto.model,
            targetDistance = shoeCreationDto.targetDistance,
            isMain = shoeCreationDto.isMain
        ).let {
            shoeRepository.save(it)
        }.let { ShoeDto(it) }
    }

    fun getShoeDtoCursor(userId: Long, isEnabled: Boolean, cursor: Long?, size: Int): CursorResult<ShoeDto> {

        val content = shoeRepository.findAll(userId, isEnabled, cursor, size)
        val cursor = content.lastOrNull()?.id
        val hasNext = cursor?.let { shoeRepository.hasNext(userId, isEnabled, it) } ?: false

        return CursorResult(
            content = content.map { ShoeDto(it) },
            cursor = cursor,
            hasNext = hasNext
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    fun patch(userId: Long, id: Long, shoePatchDto: ShoePatchDto): ShoeDto {
        return getById(id).also {
            if(it.user.id != userId) {
                throw ApiException(ApiError.NOT_AUTHORIZED)
            }
        }.also {
            it.update(
                brand = shoePatchDto.brand,
                model = shoePatchDto.model,
                targetDistance = shoePatchDto.targetDistance,
                isMain = shoePatchDto.isMain,
                isEnabled = shoePatchDto.isEnabled,
                isDeleted = shoePatchDto.isDeleted
            )
        }.let {
            shoeRepository.save(it)
        }.let { ShoeDto(it) }
    }

    private fun getById(id: Long): Shoe {
        return shoeRepository.findById(id)
            .orElseThrow { ApiException(ApiError.NOT_FOUND_SHOE) }
    }
}