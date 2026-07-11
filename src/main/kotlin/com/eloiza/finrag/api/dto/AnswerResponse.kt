package com.eloiza.finrag.api.dto

import com.eloiza.finrag.domain.model.Answer

data class AnswerResponse(
    val answer: String,
    val sources: List<SourceResponse>,
) {
    companion object {
        fun from(answer: Answer) =
            AnswerResponse(
                answer = answer.text,
                sources = answer.sources.map(SourceResponse::from),
            )
    }
}
