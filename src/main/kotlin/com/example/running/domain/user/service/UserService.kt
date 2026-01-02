package com.example.running.domain.user.service

import com.example.running.domain.auth.service.AppleTokenRevokeService
import com.example.running.domain.auth.service.OAuthTokenService
import com.example.running.domain.auth.service.dto.UserCreationDto
import com.example.running.domain.common.enums.AccountTypeName
import com.example.running.domain.common.enums.AuthorityType
import com.example.running.domain.user.dto.UserDataDto
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.repository.UserRepository
import com.example.running.domain.user.service.dto.UserDto
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userAccountService: UserAccountService,
    private val userRepository: UserRepository,
    private val oAuthTokenService: OAuthTokenService,
    private val appleTokenRevokeService: AppleTokenRevokeService
) {
    private val log = KotlinLogging.logger {}

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
     * App Store 요구사항: Apple 계정의 경우 토큰 해제 필수
     */
    @Transactional
    fun withdraw(userId: Long) {
        val user = getById(userId)

        // Apple 계정의 refresh token 해제
        revokeAppleTokens(userId)

        // 사용자 익명화
        user.withdraw()

        // OAuth 토큰 삭제
        oAuthTokenService.deleteAllByUserId(userId)

        // 연결된 계정도 비활성화
        userAccountService.disableAllByUserId(userId)
    }

    /**
     * Apple 계정의 refresh token 해제
     */
    private fun revokeAppleTokens(userId: Long) {
        val oAuthTokens = oAuthTokenService.findAllByUserId(userId)

        oAuthTokens.forEach { token ->
            val accountTypeId = token.userAccount.accountType.id

            // Apple 계정(id=3)인 경우에만 토큰 해제
            if (accountTypeId == AccountTypeName.APPLE.id) {
                token.refreshToken?.let { refreshToken ->
                    val success = appleTokenRevokeService.revokeToken(refreshToken)
                    if (success) {
                        log.info { "Apple token revoked for userId: $userId" }
                    } else {
                        log.warn { "Failed to revoke Apple token for userId: $userId" }
                    }
                }
            }
        }
    }
}