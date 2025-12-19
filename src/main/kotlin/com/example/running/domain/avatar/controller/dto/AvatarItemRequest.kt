package com.example.running.domain.avatar.controller.dto

class AvatarItemRequest(
    val itemIds: List<Long>,
    val hairColor: String? = null  // 헤어 색상 (HEX 형식: "#FFFFFF"), null이면 변경하지 않음
)