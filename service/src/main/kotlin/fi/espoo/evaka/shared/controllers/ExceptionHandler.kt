// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.shared.controllers

import fi.espoo.evaka.shared.domain.BadRequest
import fi.espoo.evaka.shared.domain.Conflict
import fi.espoo.evaka.shared.domain.Forbidden
import fi.espoo.evaka.shared.domain.NotFound
import fi.espoo.evaka.shared.domain.Unauthorized
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.time.Instant
import javax.servlet.http.HttpServletRequest

data class ErrorResponse(
    val errorCode: String? = null,
    val message: String,
    val exception: String? = null,
    val timestamp: Long = Instant.now().toEpochMilli()
)

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
class ExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(value = [BadRequest::class])
    fun badRequest(req: HttpServletRequest, ex: BadRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request (${ex.message})")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                errorCode = ex.errorCode,
                message = ex.message
            )
        )
    }

    @ExceptionHandler(value = [NotFound::class])
    fun notFound(req: HttpServletRequest, ex: NotFound): ResponseEntity<ErrorResponse> {
        logger.warn("Not found (${ex.message})")
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                errorCode = ex.errorCode,
                message = ex.message
            )
        )
    }

    @ExceptionHandler(value = [Conflict::class])
    fun conflict(req: HttpServletRequest, ex: Conflict): ResponseEntity<ErrorResponse> {
        logger.warn("Conflict (${ex.message})")
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                errorCode = ex.errorCode,
                message = ex.message
            )
        )
    }

    @ExceptionHandler(value = [Unauthorized::class])
    fun unauthorized(req: HttpServletRequest, ex: Unauthorized): ResponseEntity<ErrorResponse> {
        logger.warn("Unauthorized (${ex.message})")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(
                errorCode = ex.errorCode,
                message = ex.message
            )
        )
    }

    @ExceptionHandler(value = [Forbidden::class])
    fun forbidden(req: HttpServletRequest, ex: Forbidden): ResponseEntity<ErrorResponse> {
        logger.warn("Forbidden (${ex.message})")
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(
                errorCode = ex.errorCode,
                message = ex.message
            )
        )
    }

    @ExceptionHandler(
        value = [
            fi.espoo.evaka.vtjclient.controllers.UseCaseDeniedException::class
        ]
    )
    fun useCaseDenied(req: HttpServletRequest, ex: RuntimeException): ResponseEntity<ErrorResponse> {
        logger.warn("Forbidden (${ex.message})")
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(
                message = ex.message ?: ""
            )
        )
    }

    // We don't want alerts from ClientAbortExceptions or return any http responses to them
    @ExceptionHandler(value = [IOException::class])
    fun IOExceptions(req: HttpServletRequest, ex: IOException): ResponseEntity<ErrorResponse>? {
        if (ex.toString().contains("ClientAbortException", true)) {
            logger.warn("ClientAbortException ($ex)")
            return null
        }
        val message = "Unexpected error (${ex.message})"
        logger.error(message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                message = ex.message ?: ""
            )
        )
    }

    @ExceptionHandler(value = [Throwable::class])
    fun unexpectedError(req: HttpServletRequest, ex: Throwable): ResponseEntity<ErrorResponse> {
        // Checked exceptions get wrapped in UndeclaredThrowableException in kotlin
        val cause = if (ex is UndeclaredThrowableException) ex.cause else ex
        val message = "Unexpected error (${ex.message})"
        logger.error(message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                message = message,
                exception = cause?.message
            )
        )
    }
}
