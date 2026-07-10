package com.eloiza.finrag.application

import com.eloiza.finrag.domain.model.Document
import com.eloiza.finrag.domain.port.DocumentRepository
import java.util.UUID

class ListDocumentsUseCase(
    private val documentRepository: DocumentRepository,
) {
    fun list(userId: UUID): List<Document> = documentRepository.findAllByUserId(userId)
}
