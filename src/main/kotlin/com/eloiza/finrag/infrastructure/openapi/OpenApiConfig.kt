package com.eloiza.finrag.infrastructure.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("FinRAG API")
                    .description(
                        "API REST com pipeline RAG sobre documentos financeiros: " +
                            "autenticação JWT, ingestão de PDF/Markdown com embeddings e " +
                            "Q&A com busca semântica. Obtenha um token em /auth/login e " +
                            "use o botão Authorize para chamar os endpoints protegidos.",
                    ).version("v1"),
            ).components(
                Components().addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
            // Requisito global: endpoints públicos (ex.: /auth/**) anulam com @SecurityRequirements
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
