package com.clang.session.service.dto

import com.clang.session.domain.User
import java.io.Serializable
/**
 * A DTO representing a user, with only the public attributes.
 */
open class UserDTO(
    var id: Long? = null,
    var login: String? = null,
) : Serializable {

    constructor(user: User) : this(user.id, user.login)

    override fun toString() = "UserDTO{" +
        "login='" + login + '\'' +
        "}"

    companion object {
        private const val serialVersionUID = 1L
    }
}
