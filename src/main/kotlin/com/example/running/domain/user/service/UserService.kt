package com.example.running.domain.user.service

import com.example.running.domain.user.entity.User
import com.example.running.domain.common.enums.AuthorityType
import com.example.running.domain.user.dto.UserDataDto
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user_auth.service.dto.UserCreationDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userAccountService: UserAccountService,
    private val userRepository: UserRepository
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


    fun getUserDataDto(userId: Long): UserDataDto {
        return userRepository.getUserDataDtoById(userId)
    }
}