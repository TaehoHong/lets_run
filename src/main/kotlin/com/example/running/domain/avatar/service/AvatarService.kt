package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.entity.Avatar
import com.example.running.domain.avatar.repository.AvatarRepository
import com.example.running.domain.avatar.service.dto.AvatarDto
import com.example.running.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AvatarService(
    private val avatarRepository: AvatarRepository,
    private val avatarUserItemService: AvatarUserItemService
) {

    @Transactional(readOnly = true)
    fun getMainAvatar(userId: Long): AvatarDto {
        val avatar = avatarRepository.findByUserIdAndIsMain(userId, true)
            ?: run { saveAvatar(userId, true) }

        return AvatarDto(
            avatar, avatarUserItemService.getAllByAvatarId(avatar.id))
    }

    fun saveAvatar(userId: Long, isMain: Boolean): Avatar {
        return avatarRepository.save(
            Avatar(
                user = User(userId),
                isMain = isMain,
                orderIndex = 0
            )
        )
    }
}