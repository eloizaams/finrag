package com.eloiza.finrag.api

import com.eloiza.finrag.api.dto.DocumentResponse
import com.eloiza.finrag.api.dto.PagedResponse
import com.eloiza.finrag.application.DeleteDocumentUseCase
import com.eloiza.finrag.application.GetDocumentUseCase
import com.eloiza.finrag.application.IngestDocumentUseCase
import com.eloiza.finrag.application.ListDocumentsUseCase
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/documents")
class DocumentController(
    private val ingestDocumentUseCase: IngestDocumentUseCase,
    private val listDocumentsUseCase: ListDocumentsUseCase,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
) {
    @PostMapping(consumes = ["multipart/form-data"])
    fun ingest(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam file: MultipartFile,
    ): ResponseEntity<DocumentResponse> {
        val document = ingestDocumentUseCase.ingest(userId, file.originalFilename ?: "", file.bytes)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(DocumentResponse.from(document))
    }

    @GetMapping
    fun list(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): PagedResponse<DocumentResponse> = PagedResponse.from(listDocumentsUseCase.list(userId, page, size), DocumentResponse::from)

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
    ): DocumentResponse = DocumentResponse.from(getDocumentUseCase.get(userId, id))

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        deleteDocumentUseCase.delete(userId, id)
        return ResponseEntity.noContent().build()
    }

    companion object {
        const val MAX_PAGE_SIZE = 100L
    }
}
