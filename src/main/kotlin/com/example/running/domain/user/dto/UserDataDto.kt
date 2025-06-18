package com.example.running.domain.user.dto

import com.example.running.domain.common.enums.AuthorityType
import com.example.running.domain.user.entity.AccountType

data class UserDataDto(
    val id: Long,
    val nickname: String,
    val authorityType: AuthorityType,
    val totalPoint: Long,
    val userAccounts: List<UserAccountDataDto>,
    val equippedItems: List<EquippedItemDto>

)


data class UserAccountDataDto(
    val id: Long,
    val email: String,
    val accountType: AccountType
)


data class EquippedItemDto(
    val id: Long,
    val itemTypeId: Short,
    val filePath: String
)