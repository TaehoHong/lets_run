package com.example.running.domain.user.dto

import com.example.running.domain.common.enums.AuthorityType

data class UserDataDto(
    val id: Long,
    val name: String,
    val authorityType: AuthorityType,
    val totalPoint: Int,
    val userAccounts: Set<UserAccountDataDto>,
    val avatarId: Long,
    val equippedItems: MutableList<EquippedItemDto> = mutableListOf()
) {
    constructor(
        id: Long,
        name: String,
        authorityType: AuthorityType,
        totalPoint: Int,
        userAccounts: Set<UserAccountDataDto>,
        avatarId: Long): this(
            id = id,
            name = name,
            authorityType = authorityType,
            totalPoint = totalPoint,
            userAccounts = userAccounts,
            avatarId = avatarId,
            equippedItems = mutableListOf()
        )
}


data class UserAccountDataDto(
    val id: Long,
    val email: String,
    val accountType: String
)


data class EquippedItemDto(
    val id: Long,
    val itemTypeId: Short,
    val name: String,
    val filePath: String,
    val unityFilePath: String
)