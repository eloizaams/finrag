package com.eloiza.finrag.infrastructure

import com.eloiza.finrag.application.AskQuestionUseCase
import com.eloiza.finrag.application.AuthenticateUserUseCase
import com.eloiza.finrag.application.DeleteDocumentUseCase
import com.eloiza.finrag.application.GetDocumentUseCase
import com.eloiza.finrag.application.IngestDocumentUseCase
import com.eloiza.finrag.application.ListDocumentsUseCase
import com.eloiza.finrag.application.RegisterUserUseCase
import com.eloiza.finrag.domain.port.ChunkSearchRepository
import com.eloiza.finrag.domain.port.DocumentRepository
import com.eloiza.finrag.domain.port.EmbeddingProvider
import com.eloiza.finrag.domain.port.LlmClient
import com.eloiza.finrag.domain.port.PasswordHasher
import com.eloiza.finrag.domain.port.PipelineMetrics
import com.eloiza.finrag.domain.port.TextExtractor
import com.eloiza.finrag.domain.port.TokenProvider
import com.eloiza.finrag.domain.port.UserRepository
import com.eloiza.finrag.domain.service.RagPromptBuilder
import com.eloiza.finrag.domain.service.TextChunker
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCaseConfig {
    @Bean
    fun registerUserUseCase(
        userRepository: UserRepository,
        passwordHasher: PasswordHasher,
    ): RegisterUserUseCase = RegisterUserUseCase(userRepository, passwordHasher)

    @Bean
    fun authenticateUserUseCase(
        userRepository: UserRepository,
        passwordHasher: PasswordHasher,
        tokenProvider: TokenProvider,
    ): AuthenticateUserUseCase = AuthenticateUserUseCase(userRepository, passwordHasher, tokenProvider)

    @Bean
    fun textChunker(
        @Value("\${finrag.chunking.max-chars}") maxChars: Int,
        @Value("\${finrag.chunking.overlap-chars}") overlapChars: Int,
    ): TextChunker = TextChunker(maxChars, overlapChars)

    @Bean
    fun ingestDocumentUseCase(
        textExtractor: TextExtractor,
        textChunker: TextChunker,
        embeddingProvider: EmbeddingProvider,
        documentRepository: DocumentRepository,
        pipelineMetrics: PipelineMetrics,
    ): IngestDocumentUseCase = IngestDocumentUseCase(textExtractor, textChunker, embeddingProvider, documentRepository, pipelineMetrics)

    @Bean
    fun listDocumentsUseCase(documentRepository: DocumentRepository): ListDocumentsUseCase = ListDocumentsUseCase(documentRepository)

    @Bean
    fun getDocumentUseCase(documentRepository: DocumentRepository): GetDocumentUseCase = GetDocumentUseCase(documentRepository)

    @Bean
    fun deleteDocumentUseCase(documentRepository: DocumentRepository): DeleteDocumentUseCase = DeleteDocumentUseCase(documentRepository)

    @Bean
    fun ragPromptBuilder(): RagPromptBuilder = RagPromptBuilder()

    @Bean
    fun askQuestionUseCase(
        embeddingProvider: EmbeddingProvider,
        chunkSearchRepository: ChunkSearchRepository,
        ragPromptBuilder: RagPromptBuilder,
        llmClient: LlmClient,
        pipelineMetrics: PipelineMetrics,
        ragProperties: RagProperties,
    ): AskQuestionUseCase =
        AskQuestionUseCase(
            embeddingProvider,
            chunkSearchRepository,
            ragPromptBuilder,
            llmClient,
            pipelineMetrics,
            ragProperties.topK,
            ragProperties.minSimilarity,
        )
}
