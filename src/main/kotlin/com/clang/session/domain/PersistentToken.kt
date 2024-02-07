package com.clang.session.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import java.io.Serializable
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

/**
 * Persistent tokens are used by Spring Security to automatically log in users.
 *
 * @see com.clang.session.security.PersistentTokenRememberMeServices
 */
@Entity
@Table(name = "jhi_persistent_token")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class PersistentToken @JvmOverloads constructor(
    @Id
    var series: String? = null,

    @JsonIgnore
    @field:NotNull
    @Column(name = "token_value", nullable = false)
    var tokenValue: String? = null,
    @Column(name = "token_date")
    var tokenDate: LocalDate? = null,

    // An IPV6 address max length is 39 characters
    @field:Size(min = 0, max = 39)
    @Column(name = "ip_address", length = 39)
    var ipAddress: String? = null,

    @Column(name = "user_agent")
    userAgent: String? = null,

    @JsonIgnore
    @ManyToOne
    var user: User? = null

) : Serializable {

    @Column(name = "user_agent")
    var userAgent: String? = userAgent
        set(userAgent) {
            field =
                if (userAgent != null && userAgent.length >= MAX_USER_AGENT_LEN) {
                    userAgent.substring(0, MAX_USER_AGENT_LEN - 1)
                } else {
                    userAgent
                }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PersistentToken) return false

        return series == other.series
    }

    override fun hashCode() = series?.hashCode() ?: 0

    override fun toString() = "PersistentToken{" +
        "series='$series', " +
        "tokenValue='$tokenValue', " +
        "tokenDate=$tokenDate, " +
        "ipAddress='$ipAddress', " +
        "userAgent='${this.userAgent}'" +
        "}"

    companion object {
        private const val serialVersionUID = 1L

        private const val MAX_USER_AGENT_LEN = 255
    }
}
