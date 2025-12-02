package com.example.running.domain.user.service

import com.example.running.domain.auth.service.dto.UserCreationDto
import com.example.running.domain.common.enums.AuthorityType
import com.example.running.domain.user.dto.UserDataDto
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.dto.UserDto
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userAccountService: UserAccountService,
    private val userRepository: UserRepository
) {

    @Transactional(rollbackFor = [Exception::class])
    fun save(userCreationDto: UserCreationDto): User {

        val user = User(
            nickname = userCreationDto.nickname,
            authorityType = AuthorityType.USER
        ).let {
            userRepository.save(it)
        }

        userAccountService.save(
            user = user,
            email = userCreationDto.email,
            password = userCreationDto.password,
            accountType = userCreationDto.accountType
        )
        return user
    }


    fun getUserDataDto(userId: Long): UserDataDto {
        return userRepository.getUserDataDtoById(userId)
    }

    fun getUserDto(id: Long): UserDto {
        return userRepository.findUserDto(id)
            ?:run{ throw ApiException(ApiError.NOT_FOUND_USER) }
    }

    fun getById(id: Long): User {
        return userRepository.findByIdOrNull(id)
            ?:run { throw ApiException(ApiError.NOT_FOUND_USER) }
    }

    /**
     * 프로필 업데이트
     */
    @Transactional
    fun updateProfile(userId: Long, nickname: String?, profileImageUrl: String?): User {
        val user = getById(userId)
        user.updateProfile(nickname, profileImageUrl)
        return user
    }

    /**
     * 회원 탈퇴 (익명화 처리)
     */
    @Transactional
    fun withdraw(userId: Long) {
        val user = getById(userId)
        user.withdraw()
        // 연결된 계정도 비활성화
        userAccountService.disableAllByUserId(userId)
    }
}