package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.DocumentNotFoundException
import com.eloiza.finrag.domain.model.Document
import com.eloiza.finrag.domain.port.DocumentRepository
import java.util.UUID

class GetDocumentUseCase(
    private val documentRepository: DocumentRepository,
) {
    fun get(
        userId: UUID,
        documentId: UUID,
    ): Document = documentRepository.findByIdAndUserId(documentId, userId) ?: throw DocumentNotFoundException()
}
