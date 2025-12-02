package com.example.running.domain.user.controller

import com.example.running.common.service.S3Service
import com.example.running.domain.auth.controller.dto.VerificationEmailDto
import com.example.running.domain.user.controller.dto.ProfileResponse
import com.example.running.domain.user.dto.UserDataDto
import com.example.running.domain.user.service.UserAccountService
import com.example.running.domain.user.service.UserService
import com.example.running.helper.authenticateWithUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "User", description = "사용자 API")
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    val userAccountService: UserAccountService,
    val userService: UserService,
    val s3Service: S3Service
) {

    @PostMapping("/verification/email")
    fun verifyEmailIsNotDuplicate(@Valid @RequestBody emailDto: VerificationEmailDto) {

        userAccountService.verifyEmailIsNotExists(emailDto.email)
    }

    //자체계정 미제공으로 주석처리
//    @PostMapping
//    fun createUser(@Valid @RequestBody userCreationRequest: UserCreationRequest): UserResponse {
//
//        userAccountService.verifyEmailIsNotExists(userCreationRequest.email)
//        return userService.save(UserCreationDto(userCreationRequest))
//            .let { UserResponse(it.id, it.nickname) }
//    }

    @GetMapping("/me")
    fun getMe(): UserDataDto {
        return authenticateWithUser { userId ->
            userService.getUserDataDto(userId)
        }
    }

    @Operation(summary = "프로필 수정", description = "닉네임과 프로필 이미지를 수정합니다.")
    @PatchMapping("/me", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateProfile(
        @RequestPart(required = false) nickname: String?,
        @RequestPart(required = false) profileImage: MultipartFile?
    ): ProfileResponse {
        return authenticateWithUser { userId ->
            // 이미지 업로드 (있는 경우)
            val profileImageUrl = profileImage?.let { file ->
                // 기존 이미지 삭제
                val currentUser = userService.getById(userId)
                currentUser.profileImageUrl?.let { oldUrl ->
                    s3Service.deleteProfileImage(oldUrl)
                }
                // 새 이미지 업로드
                s3Service.uploadProfileImage(userId, file)
            }

            // 프로필 업데이트
            val updatedUser = userService.updateProfile(userId, nickname, profileImageUrl)

            ProfileResponse(
                id = updatedUser.id,
                nickname = updatedUser.nickname,
                profileImageUrl = updatedUser.profileImageUrl
            )
        }
    }

    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴를 진행합니다. 개인정보는 익명화 처리됩니다.")
    @DeleteMapping("/me")
    fun withdraw() {
        return authenticateWithUser { userId ->
            // 기존 프로필 이미지 삭제
            val currentUser = userService.getById(userId)
            currentUser.profileImageUrl?.let { imageUrl ->
                s3Service.deleteProfileImage(imageUrl)
            }
            // 회원 탈퇴 처리
            userService.withdraw(userId)
        }
    }
}