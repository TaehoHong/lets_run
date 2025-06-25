package com.example.running.domain.common.dto

import org.springframework.data.domain.Page

class PageResult<T>(
    val content: List<T>,
    val pageInfo: PageInfo
) {

    companion object {
        fun <T, R> of(page: Page<T>, mapper: (T) -> R ): PageResult<R> = PageResult(
            page.content.map(mapper),
            PageInfo(page)
        )
    }
}


class PageInfo (
    val pageNumber: Int?,
    val pageSize: Int?,
    val totalElements: Int?,
    val totalPage: Int?
) {
    constructor(page: Page<*>): this(
        pageNumber = page.number,
        pageSize = page.size,
        totalElements = page.totalElements.toInt(),
        totalPage = page.totalPages
    )
}