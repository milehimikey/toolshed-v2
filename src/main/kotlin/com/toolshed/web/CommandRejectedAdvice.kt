package com.toolshed.web

import com.toolshed.axon.CommandRejected
import io.github.oshai.kotlinlogging.KotlinLogging
import org.axonframework.messaging.commandhandling.CommandExecutionException
import org.axonframework.messaging.queryhandling.QueryExecutionException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class CommandRejectedAdvice {
    @ExceptionHandler(CommandRejected::class)
    fun onRejected(ex: CommandRejected): ProblemDetail = rejection(ex)

    @ExceptionHandler(CommandExecutionException::class, QueryExecutionException::class)
    fun onExecutionFailed(ex: RuntimeException): ProblemDetail {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<CommandRejected>()
                .firstOrNull()
        if (rejected != null) return rejection(rejected)
        logger.error(ex) { "dispatch failed with no rejection" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Request failed")
    }

    private fun rejection(ex: CommandRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.message).apply {
            setProperty("invariant", ex.invariant)
            setProperty("reason", ex.reason)
        }
}
