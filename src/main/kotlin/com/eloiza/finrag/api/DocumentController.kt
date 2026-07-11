package com.eloiza.finrag.api

import com.eloiza.finrag.api.dto.DocumentResponse
import com.eloiza.finrag.api.dto.PagedResponse
import com.eloiza.finrag.application.DeleteDocumentUseCase
import com.eloiza.finrag.application.GetDocumentUseCase
import com.eloiza.finrag.application.IngestDocumentUseCase
import com.eloiza.finrag.application.ListDocumentsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Documentos", description = "Ingestão e gestão dos documentos do usuário autenticado")
class DocumentController(
    private val ingestDocumentUseCase: IngestDocumentUseCase,
    private val listDocumentsUseCase: ListDocumentsUseCase,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
) {
    @Operation(
        summary = "Ingere um documento (PDF ou Markdown)",
        description = "Extrai o texto, divide em chunks e indexa os embeddings para busca semântica. Limite de 10MB por arquivo.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Documento indexado"),
        ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
        ApiResponse(responseCode = "413", description = "Arquivo acima do tamanho máximo"),
        ApiResponse(responseCode = "415", description = "Extensão não suportada (aceitos: .pdf, .md)"),
        ApiResponse(responseCode = "422", description = "Arquivo vazio ou sem texto extraível"),
        ApiResponse(responseCode = "429", description = "Limite de uploads por minuto excedido — ver header Retry-After"),
        ApiResponse(responseCode = "502", description = "Falha no provedor de embeddings (ProblemDetail)"),
    )
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

    @Operation(
        summary = "Lista os documentos do usuário (paginado)",
        description = "Ordenado do mais recente para o mais antigo. page é 0-based; size máximo de 100.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Página de documentos com totalItems/totalPages"),
        ApiResponse(responseCode = "400", description = "Parâmetro de paginação inválido (ProblemDetail)"),
        ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
    )
    @GetMapping
    fun list(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): PagedResponse<DocumentResponse> = PagedResponse.from(listDocumentsUseCase.list(userId, page, size), DocumentResponse::from)

    @Operation(summary = "Busca um documento por id")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Metadados do documento"),
        ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
        ApiResponse(responseCode = "404", description = "Documento inexistente ou de outro usuário — mesma resposta nos dois casos"),
    )
    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
    ): DocumentResponse = DocumentResponse.from(getDocumentUseCase.get(userId, id))

    @Operation(
        summary = "Remove um documento",
        description = "Remoção definitiva: os chunks e embeddings do documento são apagados em cascata.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Documento removido"),
        ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
        ApiResponse(responseCode = "404", description = "Documento inexistente ou de outro usuário — mesma resposta nos dois casos"),
    )
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
