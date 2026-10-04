package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.mapper.WhatsAppWebhookPayload
import com.example.watsapporderservices.domain.usecase.WebhookResult
import com.example.watsapporderservices.domain.usecase.WebhookUseCase
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.launch

fun Route.webhookRoutes(useCase: WebhookUseCase) {
    get("/webhook") {
        val mode = call.request.queryParameters["hub.mode"]
        val token = call.request.queryParameters["hub.verify_token"]
        val challenge = call.request.queryParameters["hub.challenge"]

        when (val result = useCase.verify(mode, token, challenge)) {
            is WebhookResult.Verified ->
                call.respondText(result.challenge, ContentType.Text.Plain, HttpStatusCode.OK)

            WebhookResult.Rejected ->
                call.respondText("Forbidden", ContentType.Text.Plain, HttpStatusCode.Forbidden)
        }
    }

    post("/webhook") {
        val payload = call.receive<WhatsAppWebhookPayload>()
        // Acknowledge Meta immediately and generate/send the AI reply in the background.
        call.application.launch { useCase.handleEvent(payload) }
        call.respondText("EVENT_RECEIVED", ContentType.Text.Plain, HttpStatusCode.OK)
    }
}
