package com.azurlane.infra.logging

import org.slf4j.LoggerFactory

inline fun <reified T> structuredLogger() = StructuredLogger(LoggerFactory.getLogger(T::class.java))

class StructuredLogger(private val logger: org.slf4j.Logger) {
    fun info(vararg pairs: Pair<String, Any>, msg: () -> String) {
        if (logger.isInfoEnabled) {
            logger.info(formatMessage(msg(), *pairs))
        }
    }

    fun debug(vararg pairs: Pair<String, Any>, msg: () -> String) {
        if (logger.isDebugEnabled) {
            logger.debug(formatMessage(msg(), *pairs))
        }
    }

    fun warn(vararg pairs: Pair<String, Any>, msg: () -> String) {
        if (logger.isWarnEnabled) {
            logger.warn(formatMessage(msg(), *pairs))
        }
    }

    fun warn(throwable: Throwable, vararg pairs: Pair<String, Any>, msg: () -> String) {
        if (logger.isWarnEnabled) {
            logger.warn(formatMessage(msg(), *pairs), throwable)
        }
    }

    fun error(vararg pairs: Pair<String, Any>, msg: () -> String) {
        if (logger.isErrorEnabled) {
            logger.error(formatMessage(msg(), *pairs))
        }
    }

    fun error(throwable: Throwable, vararg pairs: Pair<String, Any>, msg: () -> String) {
        if (logger.isErrorEnabled) {
            logger.error(formatMessage(msg(), *pairs), throwable)
        }
    }

    private fun formatMessage(message: String, vararg pairs: Pair<String, Any>): String {
        if (pairs.isEmpty()) return message
        val fields = pairs.joinToString(" ") { (key, value) -> "$key=$value" }
        return "$message | $fields"
    }
}
