package com.azurlane.data.config

import java.util.concurrent.ConcurrentHashMap

object ConfigRegistry {
    private val configs = ConcurrentHashMap<String, Any>()

    fun <T : Any> register(name: String, config: T) {
        configs[name] = config
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(name: String): T? = configs[name] as? T

    fun <T : Any> getOrThrow(name: String): T {
        return get(name) ?: throw IllegalStateException("config not registered: $name")
    }

    fun isRegistered(name: String): Boolean = configs.containsKey(name)

    fun clear() = configs.clear()
}
