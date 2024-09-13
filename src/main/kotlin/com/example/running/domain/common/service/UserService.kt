package com.example.running.user.service

import com.example.running.user.entity.User
import com.example.running.user.enums.AuthorityType
import com.example.running.user.repository.UserRepository
import com.example.running.user.service.dto.UserCreationDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    val userAccountService: UserAccountService,
    val userRepository: UserRepository
) {

    @Transactional(rollbackFor = [Exception::class])
    fun save(userCreationDto: UserCreationDto): User {

        val user = userCreationDto.let {
            User(
                nickname = it.nickname,
                authorityType = AuthorityType.USER
            )
        }.let {
            userRepository.save(it)
        }

        userAccountService.save(
            user.id,
            userCreationDto.email,
            userCreationDto.password
        )

        return user
    }
}