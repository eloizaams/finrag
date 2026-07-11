package com.eloiza.finrag.domain.model

data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
) {
    init {
        require(page >= 0) { "page não pode ser negativa" }
        require(size > 0) { "size precisa ser positivo" }
        require(totalItems >= 0) { "totalItems não pode ser negativo" }
    }

    val totalPages: Int = if (totalItems == 0L) 0 else ((totalItems + size - 1) / size).toInt()
}
