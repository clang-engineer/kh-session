package com.clang.session.web.rest

import com.clang.session.domain.PersistentToken
import com.clang.session.repository.PersistentTokenRepository
import com.clang.session.repository.UserRepository
import com.clang.session.security.getCurrentUserLogin
import com.clang.session.service.MailService
import com.clang.session.service.UserService
import com.clang.session.service.dto.AdminUserDTO
import com.clang.session.service.dto.PasswordChangeDTO
import com.clang.session.web.rest.errors.EmailAlreadyUsedException
import com.clang.session.web.rest.errors.InvalidPasswordException
import com.clang.session.web.rest.errors.LoginAlreadyUsedException
import com.clang.session.web.rest.vm.KeyAndPasswordVM
import com.clang.session.web.rest.vm.ManagedUserVM
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
class AccountResource(
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val mailService: MailService,
    private val persistentTokenRepository: PersistentTokenRepository
) {

    internal class AccountResourceException(message: String) : RuntimeException(message)

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * `POST  /register` : register the user.
     *
     * @param managedUserVM the managed user View Model.
     * @throws InvalidPasswordException `400 (Bad Request)` if the password is incorrect.
     * @throws EmailAlreadyUsedException `400 (Bad Request)` if the email is already used.
     * @throws LoginAlreadyUsedException `400 (Bad Request)` if the login is already used.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerAccount(@Valid @RequestBody managedUserVM: ManagedUserVM) {
        if (isPasswordLengthInvalid(managedUserVM.password)) {
            throw InvalidPasswordException()
        }
        val user = userService.registerUser(managedUserVM, managedUserVM.password!!)
        mailService.sendActivationEmail(user)
    }

    /**
     * `GET  /activate` : activate the registered user.
     *
     * @param key the activation key.
     * @throws RuntimeException `500 (Internal Server Error)` if the user couldn't be activated.
     */
    @GetMapping("/activate")
    fun activateAccount(@RequestParam(value = "key") key: String) {
        val user = userService.activateRegistration(key)
        if (!user.isPresent) {
            throw AccountResourceException("No user was found for this activation key")
        }
    }

    /**
     * `GET  /authenticate` : check if the user is authenticated, and return its login.
     *
     * @param request the HTTP request.
     * @return the login if the user is authenticated.
     */
    @GetMapping("/authenticate")
    fun isAuthenticated(request: HttpServletRequest): String? {
        log.debug("REST request to check if the current user is authenticated")
        return request.remoteUser
    }

    /**
     * `GET  /account` : get the current user.
     *
     * @return the current user.
     * @throws RuntimeException `500 (Internal Server Error)` if the user couldn't be returned.
     */
    @GetMapping("/account")
    fun getAccount(): AdminUserDTO =
        userService.getUserWithAuthorities()
            .map { AdminUserDTO(it) }
            .orElseThrow { AccountResourceException("User could not be found") }

    /**
     * POST  /account : update the current user information.
     *
     * @param userDTO the current user information
     * @throws EmailAlreadyUsedException `400 (Bad Request)` if the email is already used.
     * @throws RuntimeException `500 (Internal Server Error)` if the user login wasn't found.
     */
    @PostMapping("/account")
    fun saveAccount(@Valid @RequestBody userDTO: AdminUserDTO) {
        val userLogin = getCurrentUserLogin()
            .orElseThrow { AccountResourceException("") }
        val existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.email)
        if (existingUser.isPresent && !existingUser.get().login.equals(userLogin, ignoreCase = true)) {
            throw EmailAlreadyUsedException()
        }
        val user = userRepository.findOneByLogin(userLogin)
        if (!user.isPresent) {
            throw AccountResourceException("User could not be found")
        }
        userService.updateUser(
            userDTO.firstName, userDTO.lastName, userDTO.email,
            userDTO.langKey, userDTO.imageUrl
        )
    }

    /**
     * POST  /account/change-password : changes the current user's password.
     *
     * @param passwordChangeDto current and new password.
     * @throws InvalidPasswordException `400 (Bad Request)` if the new password is incorrect.
     */
    @PostMapping(path = ["/account/change-password"])
    fun changePassword(@RequestBody passwordChangeDto: PasswordChangeDTO) {
        if (isPasswordLengthInvalid(passwordChangeDto.newPassword)) {
            throw InvalidPasswordException()
        }
        userService.changePassword(passwordChangeDto.currentPassword!!, passwordChangeDto.newPassword!!)
    }

    /**
     * GET  /account/sessions : get the current open sessions.
     *
     * @return the current open sessions
     * @throws RuntimeException `500 (Internal Server Error)` if the current open sessions couldn't be retrieved
     */
    @GetMapping("/account/sessions")
    fun getCurrentSessions(): List<PersistentToken> =
        persistentTokenRepository.findByUser(
            userRepository.findOneByLogin(
                getCurrentUserLogin()
                    .orElseThrow { AccountResourceException("Current user login not found") }
            )
                .orElseThrow { AccountResourceException("User could not be found") }
        )

    /**
     *` DELETE  /account/sessions?series={series}` : invalidate an existing session.
     *
     * - You can only delete your own sessions, not any other user's session
     * - If you delete one of your existing sessions, and that you are currently logged in on that session, you will
     *   still be able to use that session, until you quit your browser: it does not work in real time (there is
     *   no API for that), it only removes the "remember me" cookie
     * - This is also true if you invalidate your current session: you will still be able to use it until you close
     *   your browser or that the session times out. But automatic login (the "remember me" cookie) will not work
     *   anymore.
     *   There is an API to invalidate the current session, but there is no API to check which session uses which
     *   cookie.
     *
     * @param series the series of an existing session.
     * @throws IllegalArgumentException if the series couldn't be URL decoded.
     */
    @DeleteMapping("/account/sessions/{series}")
    fun invalidateSession(@PathVariable series: String) {
        val decodedSeries = URLDecoder.decode(series, StandardCharsets.UTF_8)
        getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .ifPresent { u ->
                persistentTokenRepository.findByUser(u).stream()
                    .filter { persistentToken -> StringUtils.equals(persistentToken.series, decodedSeries) }
                    .findAny().ifPresent { persistentTokenRepository.deleteById(decodedSeries) }
            }
    }

    /**
     * POST   /account/reset-password/init : Send an email to reset the password of the user
     *
     * @param mail the mail of the user
     */
    @PostMapping(path = ["/account/reset-password/init"])
    fun requestPasswordReset(@RequestBody mail: String) {
        val user = userService.requestPasswordReset(mail)
        if (user.isPresent()) {
            mailService.sendPasswordResetMail(user.get())
        } else {
            // Pretend the request has been successful to prevent checking which emails really exist
            // but log that an invalid attempt has been made
            log.warn("Password reset requested for non existing mail")
        }
    }

    /**
     * `POST   /account/reset-password/finish` : Finish to reset the password of the user.
     *
     * @param keyAndPassword the generated key and the new password.
     * @throws InvalidPasswordException `400 (Bad Request)` if the password is incorrect.
     * @throws RuntimeException `500 (Internal Server Error)` if the password could not be reset.
     */
    @PostMapping(path = ["/account/reset-password/finish"])
    fun finishPasswordReset(@RequestBody keyAndPassword: KeyAndPasswordVM) {
        if (isPasswordLengthInvalid(keyAndPassword.newPassword)) {
            throw InvalidPasswordException()
        }
        val user = userService.completePasswordReset(keyAndPassword.newPassword!!, keyAndPassword.key!!)

        if (!user.isPresent) {
            throw AccountResourceException("No user was found for this reset key")
        }
    }
}

private fun isPasswordLengthInvalid(password: String?) = password.isNullOrEmpty() || password.length < ManagedUserVM.PASSWORD_MIN_LENGTH || password.length > ManagedUserVM.PASSWORD_MAX_LENGTH
