package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.entity.Avatar
import com.example.running.domain.avatar.repository.AvatarRepository
import com.example.running.domain.avatar.service.dto.AvatarDto
import com.example.running.domain.user.entity.User
import com.example.running.utils.alsoIfFalse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AvatarService(
    private val avatarRepository: AvatarRepository,
    private val avatarUserItemService: AvatarUserItemService,
    private val itemService: ItemService
) {

    @Transactional
    fun createDefault(userId: Long) {
        saveAvatar(userId, true)
            .also {
                avatarUserItemService.createDefault(userId, it.id)
            }
    }

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

    @Transactional(readOnly = true)
    fun verifyAvatarExists(userId: Long, avatarId: Long) {
        avatarRepository.existsByUserIdAndId(userId, avatarId)
            .alsoIfFalse { throw IllegalArgumentException("Avatar not found") }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun put(avatarId: Long, itemIds: List<Long>): AvatarDto {

        avatarUserItemService.deleteAllByAvatarId(avatarId)

        avatarUserItemService.saveAll(avatarId, itemIds)

        return getMainAvatar(avatarId)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun addOrChangeItems(avatarId: Long, itemIds: List<Long>, hairColor: String? = null): AvatarDto {

        // 헤어 색상 업데이트
        hairColor?.let { updateHairColor(avatarId, it) }

        itemService.getAllItemTypeId(itemIds)
            .let {
                avatarUserItemService.deleteAllByAvatarIdAndItemTypeIds(avatarId, it)
            }

        avatarUserItemService.saveAll(avatarId, itemIds)

        return getMainAvatar(avatarId)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun updateHairColor(avatarId: Long, hairColor: String) {
        // HEX 색상 형식 검증 (#FFFFFF 또는 #FFF)
        require(hairColor.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))) {
            "Invalid hair color format. Expected HEX format like #FFFFFF"
        }

        avatarRepository.findById(avatarId)
            .orElseThrow { IllegalArgumentException("Avatar not found") }
            .apply { this.hairColor = hairColor }
    }
}