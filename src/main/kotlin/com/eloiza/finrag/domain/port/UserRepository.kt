package com.eloiza.finrag.domain.port

import com.eloiza.finrag.domain.model.User

interface UserRepository {
    fun save(user: User): User

    fun findByEmail(email: String): User?
}
