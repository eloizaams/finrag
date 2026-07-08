package com.eloiza.finrag.infrastructure.persistence

import com.eloiza.finrag.domain.exception.EmailAlreadyRegisteredException
import com.eloiza.finrag.domain.model.User
import com.eloiza.finrag.domain.port.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class UserRepositoryJpaAdapter(
    private val jpaUserRepository: JpaUserRepository,
) : UserRepository {
    override fun save(user: User): User {
        try {
            jpaUserRepository.save(user.toEntity())
        } catch (e: DataIntegrityViolationException) {
            throw EmailAlreadyRegisteredException(user.email)
        }
        return user
    }

    override fun findByEmail(email: String): User? = jpaUserRepository.findByEmail(email)?.toDomain()
}

private fun User.toEntity() =
    UserEntity(
        id = id,
        email = email,
        passwordHash = passwordHash,
        createdAt = createdAt,
    )

private fun UserEntity.toDomain() =
    User(
        id = id,
        email = email,
        passwordHash = passwordHash,
        createdAt = createdAt,
    )
