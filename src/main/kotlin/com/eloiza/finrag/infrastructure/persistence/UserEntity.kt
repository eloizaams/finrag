package com.eloiza.finrag.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    val id: UUID,
    @Column(nullable = false, unique = true)
    val email: String,
    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
