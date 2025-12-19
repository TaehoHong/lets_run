package com.example.running.domain.user.dto

import com.example.running.domain.common.enums.AuthorityType

data class UserDataDto(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
    val authorityType: AuthorityType,
    val totalPoint: Int,
    val userAccounts: Set<UserAccountDataDto>,
    val avatarId: Long,
    val haveRunningRecord: Boolean,
    val hairColor: String,
    val equippedItems: MutableList<EquippedItemDto> = mutableListOf()
) {
    constructor(
        id: Long,
        name: String,
        profileImageUrl: String?,
        authorityType: AuthorityType,
        totalPoint: Int,
        userAccounts: Set<UserAccountDataDto>,
        avatarId: Long,
        haveRunningRecord: Boolean,
        hairColor: String
    ) : this(
        id = id,
        name = name,
        profileImageUrl = profileImageUrl,
        authorityType = authorityType,
        totalPoint = totalPoint,
        userAccounts = userAccounts,
        avatarId = avatarId,
        haveRunningRecord = haveRunningRecord,
        hairColor = hairColor,
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