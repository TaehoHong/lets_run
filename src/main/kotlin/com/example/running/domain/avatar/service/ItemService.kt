package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.controller.dto.ItemSearchRequest
import com.example.running.domain.avatar.repository.ItemQueryRepository
import com.example.running.domain.avatar.service.dto.ItemDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemService(
    private val itemQueryRepository: ItemQueryRepository
) {

    @Transactional(readOnly = true)
    fun getItemDtoPage(itemSearchRequest: ItemSearchRequest, pageable: Pageable): Page<ItemDto> {
        return itemQueryRepository.findItemDtoPage(itemSearchRequest, pageable)
    }
}