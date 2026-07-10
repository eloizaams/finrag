package com.eloiza.finrag.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaChunkRepository : JpaRepository<ChunkEntity, UUID>
