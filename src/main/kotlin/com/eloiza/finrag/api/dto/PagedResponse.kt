package com.eloiza.finrag.api.dto

import com.eloiza.finrag.domain.model.PageResult

data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T, R> from(
            result: PageResult<T>,
            mapper: (T) -> R,
        ) = PagedResponse(
            items = result.items.map(mapper),
            page = result.page,
            size = result.size,
            totalItems = result.totalItems,
            totalPages = result.totalPages,
        )
    }
}
