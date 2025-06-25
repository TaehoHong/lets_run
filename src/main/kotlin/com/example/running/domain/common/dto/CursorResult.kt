package com.example.running.domain.common.dto

class CursorResult<T> (
    val content: List<T>,
    val cursor: Long?,
    val hasNext: Boolean,
) {
    fun <R> of(mapper: (T) -> R): CursorResult<R> = CursorResult(
        content = this.content.map(mapper),
        cursor = this.cursor,
        hasNext = this.hasNext,
    )
}