package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.entity.AvatarUserItem
import com.example.running.domain.avatar.repository.AvatarUserItemQueryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AvatarUserItemService(
    private val avatarUserItemQueryRepository: AvatarUserItemQueryRepository
) {

    @Transactional(readOnly = true)
    fun getAllByAvatarId(avatarId: Long): List<AvatarUserItem> {
        return avatarUserItemQueryRepository.findAllByAvatarId(avatarId)
    }
}