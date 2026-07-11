package com.eloiza.finrag.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class QuestionRequest(
    @field:NotBlank
    @field:Size(max = MAX_QUESTION_LENGTH, message = "pergunta não pode passar de {max} caracteres")
    val question: String,
) {
    companion object {
        const val MAX_QUESTION_LENGTH = 2000
    }
}
