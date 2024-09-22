package com.example.running.domain.avatar.controller


import com.example.running.domain.avatar.controller.dto.AvatarItemRequest
import com.example.running.domain.avatar.controller.dto.AvatarResponse
import com.example.running.domain.avatar.service.AvatarService
import com.example.running.domain.avatar.service.AvatarUserItemService
import com.example.running.utils.JwtPayloadParser
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/v1/avatars")
@RestController
class AvatarController(
    private val avatarService: AvatarService,
    private val avatarUserItemService: AvatarUserItemService
) {

    @GetMapping("/main")
    fun getMain(): AvatarResponse {

        return AvatarResponse(
            avatarService.getMainAvatar(JwtPayloadParser.getUserId())
        )
    }

    @PutMapping("/{id}")
    fun put(@PathVariable id: Long, @RequestBody request: AvatarItemRequest): AvatarResponse {

        avatarService.verifyAvatarExists(
            userId = JwtPayloadParser.getUserId(),
            avatarId = id
        )

        return AvatarResponse(
            avatarService.put(id, request.itemIds)
        )
    }

    @PostMapping("/{id}/items")
    fun saveItem(@PathVariable id: Long, @RequestBody request: AvatarItemRequest): AvatarResponse {

        avatarService.verifyAvatarExists(
            userId = JwtPayloadParser.getUserId(),
            avatarId = id
        )

        return AvatarResponse(
            avatarService.addOrChangeItems(id, request.itemIds)
        )
    }

    @DeleteMapping("/{id}/items/{itemId}")
    fun deleteAvatarItem(@PathVariable id: Long, @PathVariable itemId: Long) {

        avatarService.verifyAvatarExists(
            userId = JwtPayloadParser.getUserId(),
            avatarId = id
        )

        avatarUserItemService.deleteByAvatarIdAndItemId(id, itemId)
    }

    @DeleteMapping("/{id}/items")
    fun deleteAllAvatarItem(@PathVariable id: Long) {

        avatarService.verifyAvatarExists(
            userId = JwtPayloadParser.getUserId(),
            avatarId = id
        )

        avatarUserItemService.deleteAllByAvatarId(id)
    }
}