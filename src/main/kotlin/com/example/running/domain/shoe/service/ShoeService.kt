package com.example.running.domain.shoe.service

import com.example.running.domain.shoe.entity.Shoe
import com.example.running.domain.shoe.repository.ShoeRepository
import com.example.running.domain.shoe.service.dto.ShoeCreationDto
import com.example.running.domain.shoe.service.dto.ShoeDto
import com.example.running.domain.user.entity.User
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
}