package com.eloiza.finrag.api

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// Quem abre a URL nua no browser cai direto na documentação interativa
@Hidden
@RestController
class RootController {
    @GetMapping("/")
    fun root(): ResponseEntity<Void> =
        ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create("/swagger-ui.html"))
            .build()
}
