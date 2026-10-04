package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.enum.MessageErrorCode
import com.example.watsapporderservices.data.mapper.SendMessageRequest
import com.example.watsapporderservices.data.mapper.SendMessageResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.usecase.MessageResult
import com.example.watsapporderservices.domain.usecase.MessageUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.messageRoutes(useCase: MessageUseCase) {
    route("/messages") {
        post {
            val request = call.receive<SendMessageRequest>()
            when (val result = useCase.send(request)) {
                is MessageResult.Sent -> call.respond(
                    HttpStatusCode.OK,
                    SendMessageResponse(messageId = result.messageId, to = result.to),
                )

                is MessageResult.Invalid -> call.respond(HttpStatusCode.BadRequest, result.code.toResponse())

                MessageResult.NotConfigured ->
                    call.respond(HttpStatusCode.ServiceUnavailable, MessageErrorCode.NOT_CONFIGURED.toResponse())

                MessageResult.ProviderError ->
                    call.respond(HttpStatusCode.BadGateway, MessageErrorCode.SEND_FAILED.toResponse())
            }
        }
    }
}
