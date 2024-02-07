package com.clang.session.repository

import com.clang.session.domain.PersistentToken
import com.clang.session.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

/**
 * Spring Data JPA repository for the [PersistentToken] entity.
 */
interface PersistentTokenRepository : JpaRepository<PersistentToken, String> {
    fun findByUser(user: User): List<PersistentToken>

    fun findByTokenDateBefore(localDate: LocalDate): List<PersistentToken>
}
