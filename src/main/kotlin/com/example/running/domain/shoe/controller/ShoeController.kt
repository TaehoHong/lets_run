package com.example.running.domain.shoe.controller

import com.example.running.domain.shoe.controller.dto.ShoeCreationRequest
import com.example.running.domain.shoe.controller.dto.ShoeResponse
import com.example.running.domain.shoe.entity.Shoe
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.domain.shoe.service.dto.ShoeCreationDto
import com.example.running.helper.authenticateWithUser
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/v1/shoes")
@RestController
class ShoeController(private val shoeService: ShoeService) {

    @PostMapping
    fun create(@RequestBody shoeCreationRequest: ShoeCreationRequest): ShoeResponse {

        return authenticateWithUser { userId ->
            shoeService.save(
                ShoeCreationDto(
                    userId = userId,
                    brand = shoeCreationRequest.brand,
                    model = shoeCreationRequest.model,
                    targetDistance = shoeCreationRequest.targetDistance,
                    isMain = shoeCreationRequest.isMain
                )
            ).let { ShoeResponse(it) }
        }
    }
}