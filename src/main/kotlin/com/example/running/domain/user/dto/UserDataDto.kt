package com.example.running.domain.user.dto

import com.example.running.domain.common.enums.AuthorityType

data class UserDataDto(
    val id: Long,
    val name: String,
    val authorityType: AuthorityType,
    val totalPoint: Int,
    val userAccounts: List<UserAccountDataDto>,
    val equippedItems: List<EquippedItemDto>
)


data class UserAccountDataDto(
    val id: Long,
    val email: String,
    val accountType: String
)


data class EquippedItemDto(
    val id: Long,
    val itemTypeId: Short,
    val name: String,
    val filePath: String
)