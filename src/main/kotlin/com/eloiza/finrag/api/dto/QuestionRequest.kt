package com.eloiza.finrag.api.dto

import jakarta.validation.constraints.NotBlank

data class QuestionRequest(
    @field:NotBlank
    val question: String,
)
