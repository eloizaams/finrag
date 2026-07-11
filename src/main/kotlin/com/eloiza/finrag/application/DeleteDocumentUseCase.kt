package com.eloiza.finrag.application

import com.eloiza.finrag.domain.exception.DocumentNotFoundException
import com.eloiza.finrag.domain.port.DocumentRepository
import java.util.UUID

class DeleteDocumentUseCase(
    private val documentRepository: DocumentRepository,
) {
    fun delete(
        userId: UUID,
        documentId: UUID,
    ) {
        if (!documentRepository.deleteByIdAndUserId(documentId, userId)) {
            throw DocumentNotFoundException()
        }
    }
}
