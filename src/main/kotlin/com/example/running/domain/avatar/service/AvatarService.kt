package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.repository.AvatarRepository
import org.springframework.stereotype.Service

@Service
class AvatarService(
    private val avatarRepository: AvatarRepository
) {


}