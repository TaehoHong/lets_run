package com.example.running.domain.shoe.controller

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.shoe.controller.dto.ShoeCreationRequest
import com.example.running.domain.shoe.controller.dto.ShoePatchRequest
import com.example.running.domain.shoe.controller.dto.ShoeResponse
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.domain.shoe.service.dto.ShoeCreationDto
import com.example.running.domain.shoe.service.dto.ShoePatchDto
import com.example.running.helper.authenticateWithUser
import org.springframework.web.bind.annotation.*

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

    @GetMapping
    fun getAll(@RequestParam(required = false) cursor: Long?,
               @RequestParam(required = false) isEnabled: Boolean?,
               @RequestParam(defaultValue = "10") size: Int
    ): CursorResult<ShoeResponse> {

        return authenticateWithUser { userId ->
            shoeService.getShoeDtoCursor(userId, isEnabled, cursor, size)
                .of { ShoeResponse(it) }
        }
    }

    @PatchMapping("/{id}")
    fun patch(@PathVariable id: Long, @RequestBody shoePatchRequest: ShoePatchRequest): ShoeResponse {
        return authenticateWithUser { userId ->
            shoeService.patch(userId, id,
                ShoePatchDto(
                    brand = shoePatchRequest.brand,
                    model = shoePatchRequest.model,
                    targetDistance = shoePatchRequest.targetDistance,
                    isEnabled = shoePatchRequest.isEnabled,
                    isDeleted = shoePatchRequest.isDeleted
                )
            ).let { ShoeResponse(it) }
        }
    }

    @PostMapping("/{id}/main")
    fun updateToMain(@PathVariable id: Long) {
        return authenticateWithUser { userId ->
            shoeService.updateToMain(userId, id)
        }
    }
}