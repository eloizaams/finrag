package com.eloiza.finrag.infrastructure.security

import com.eloiza.finrag.domain.model.User
import com.eloiza.finrag.domain.port.TokenClaims
import com.eloiza.finrag.domain.port.TokenProvider
import com.eloiza.finrag.domain.port.TokenResult
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

@Component
class JjwtTokenProvider(
    @Value("\${finrag.jwt.secret}") secret: String,
    @Value("\${finrag.jwt.expiration-minutes}") private val expirationMinutes: Long,
) : TokenProvider {
    private val signingKey = Keys.hmacShaKeyFor(secret.toByteArray())

    override fun generate(user: User): TokenResult {
        val now = Date()
        val expiration = Date(now.time + expirationMinutes * 60_000)

        val token =
            Jwts.builder()
                .subject(user.id.toString())
                .claim("email", user.email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact()

        return TokenResult(token = token, expiresInSeconds = expirationMinutes * 60)
    }

    override fun validate(token: String): TokenClaims? =
        try {
            val claims =
                Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .payload

            val email = claims["email"] as? String ?: return null

            TokenClaims(
                userId = UUID.fromString(claims.subject),
                email = email,
            )
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
