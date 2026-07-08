package com.eloiza.finrag.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaUserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmail(email: String): UserEntity?
}
