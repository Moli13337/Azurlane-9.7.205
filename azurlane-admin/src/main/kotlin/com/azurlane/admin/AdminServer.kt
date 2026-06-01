package com.azurlane.admin

import com.azurlane.infra.config.AlsAdminSection
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ServerStatusResponse(
    val status: String,
    val timestamp: Long
)

object AdminServer {
    fun start(config: AlsAdminSection) {
        io.ktor.server.engine.embeddedServer(Netty, host = config.bindAddress, port = config.port) {
            install(ContentNegotiation) { json() }
            install(CORS) {
                allowHost("*", schemes = listOf("http", "https"))
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
            }

            routing {
                get("/health") {
                    call.respondText("ok", ContentType.Text.Plain)
                }
                get("/api/v1/server/status") {
                    call.respond(ServerStatusResponse("running", System.currentTimeMillis()))
                }
            }
        }.start(wait = false)
    }
}
