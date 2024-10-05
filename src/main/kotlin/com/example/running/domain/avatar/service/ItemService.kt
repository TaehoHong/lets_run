package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.controller.dto.ItemSearchRequest
import com.example.running.domain.avatar.entity.Item
import com.example.running.domain.avatar.repository.ItemQueryRepository
import com.example.running.domain.avatar.repository.ItemRepository
import com.example.running.domain.avatar.service.dto.ItemDto
import com.example.running.utils.JwtPayloadParser
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemService(
    private val itemRepository: ItemRepository,
    private val itemQueryRepository: ItemQueryRepository
) {

    @Transactional(readOnly = true)
    fun getItemDtoPage(itemSearchRequest: ItemSearchRequest, pageable: Pageable): Page<ItemDto> {
        return itemQueryRepository.findItemDtoPage(
            JwtPayloadParser.getUserId(),
            itemSearchRequest,
            pageable
        )
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): Item {
        return itemRepository.findById(id)
            .orElseThrow { RuntimeException("아이템을 찾을 수 없습니다.") }
    }

    @Transactional(readOnly = true)
    fun getAllByIds(ids: List<Long>): List<Item> {
        return itemRepository.findAllById(ids)
    }

    @Transactional(readOnly = true)
    fun getAllItemTypeId(ids: List<Long>): List<Short> {
        return itemQueryRepository.findAllItemTypeIdByIdIn(ids)
    }
}