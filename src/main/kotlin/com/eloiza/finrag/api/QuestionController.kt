package com.eloiza.finrag.api

import com.eloiza.finrag.api.dto.AnswerResponse
import com.eloiza.finrag.api.dto.QuestionRequest
import com.eloiza.finrag.application.AskQuestionUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/questions")
class QuestionController(
    private val askQuestionUseCase: AskQuestionUseCase,
) {
    @PostMapping
    fun ask(
        @AuthenticationPrincipal userId: UUID,
        @RequestBody request: QuestionRequest,
    ): AnswerResponse = AnswerResponse.from(askQuestionUseCase.ask(userId, request.question))
}
