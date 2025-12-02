package com.example.running.domain.user.service

import com.example.running.domain.term.service.TermService
import com.example.running.domain.user.controller.dto.PatchUserAgreementRequest
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserAgreement
import com.example.running.domain.user.repository.UserAgreementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAgreementService(
    private val userAgreementRepository: UserAgreementRepository,
    private val termService: TermService
) {


    @Transactional(rollbackFor = [Exception::class])
    fun createDefault(user: User) {
        termService.getAll(true)
            .map { term ->
                UserAgreement(
                    user = user,
                    term = term,
                    isAgreed = false
                )
            }.let {
                userAgreementRepository.saveAll(it)
            }
    }

    fun isAllTermsAgreed(userId: Long): Boolean {
        return userAgreementRepository.findAllIsAgreedByUserIdAndIsRequired(userId, true)
            .all { it }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun patch(userId:Long, requests: List<PatchUserAgreementRequest>) {
        val termIdToUserAgreementMap = userAgreementRepository.findTermIdToUserAgreementMapByUserId(userId)

        requests.forEach { request ->
            termIdToUserAgreementMap[request.termId]
                ?.run {
                    if(request.isAgreed) {
                        this.agree()
                    }
                }
        }

        userAgreementRepository.saveAll(termIdToUserAgreementMap.values)
    }
}