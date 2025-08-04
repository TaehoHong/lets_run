package com.example.running.domain.avatar.service

import com.example.running.domain.avatar.controller.dto.ItemSearchRequest
import com.example.running.domain.avatar.entity.Item
import com.example.running.domain.avatar.repository.ItemRepository
import com.example.running.domain.avatar.service.dto.ItemDto
import com.example.running.domain.common.dto.CursorResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ItemService(
    private val itemRepository: ItemRepository
) {

    @Transactional(readOnly = true)
    fun getItemDtoPage(userId: Long, itemSearchRequest: ItemSearchRequest): CursorResult<ItemDto> {
        val content = itemRepository.findAllItemDtos(userId, itemSearchRequest.cursor, itemSearchRequest)

        val cursor = content.lastOrNull()?.id

        val hasNext = itemRepository.hasNext(userId, cursor, itemSearchRequest)


        return CursorResult(content, cursor, hasNext)

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
        return itemRepository.findAllItemTypeIdByIdIn(ids)
    }

    fun getAllByNames(names: List<String>): List<Item> {
        return itemRepository.findAllByNameIn(names)
    }
}