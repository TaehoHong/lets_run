package com.example.running.domain.user.controller.dto

class PatchUserAgreementRequests(
    val requests: List<PatchUserAgreementRequest>
)

class PatchUserAgreementRequest(
    val termId: Int,
    val isAgreed: Boolean,
)