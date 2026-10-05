package com.example.watsapporderservices.routes

import com.example.watsapporderservices.data.enum.AuthErrorCode
import com.example.watsapporderservices.data.enum.OrderErrorCode
import com.example.watsapporderservices.data.mapper.SequenceResponse
import com.example.watsapporderservices.data.mapper.UpdateOrderStatusRequest
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.data.realtime.OrderSocketClient
import com.example.watsapporderservices.data.realtime.OrderSocketManager
import com.example.watsapporderservices.data.security.JWT_AUTH_NAME
import com.example.watsapporderservices.data.security.TokenService
import com.example.watsapporderservices.domain.usecase.OrderResult
import com.example.watsapporderservices.domain.usecase.OrderUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedSendChannelException

fun Route.orderRoutes(
    useCase: OrderUseCase,
    tokenService: TokenService,
    socketManager: OrderSocketManager,
) {
    route("/orders") {
        // Live order stream. The client connects with ?token=<jwt> and receives each new order as JSON.
        webSocket("/ws") {
            val userId = tokenService.userIdFromToken(call.request.queryParameters["token"])
            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                return@webSocket
            }
            val storeId = useCase.storeIdOf(userId)
            val client = KtorOrderSocketClient(this)
            socketManager.register(storeId, client)
            try {
                // Drain incoming frames so the connection stays healthy (clients may ping).
                for (frame in incoming) {
                    if (frame is Frame.Text) frame.readText()
                }
            } finally {
                socketManager.unregister(storeId, client)
            }
        }
        authenticate(JWT_AUTH_NAME) {
            get {
                val userId = call.userId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.UNAUTHORIZED.toResponse())
                    return@get
                }
                call.respond(useCase.getOrders(userId))
            }

            // How far the stream is (clients store this to resume later).
            get("/sequence") {
                call.respond(SequenceResponse(useCase.currentSequence()))
            }

            // Orders created after a sequence, ascending (catch-up after reconnect).
            get("/since/{sequence}") {
                val userId = call.userId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, AuthErrorCode.UNAUTHORIZED.toResponse())
                    return@get
                }
                val since = call.parameters["sequence"]?.toLongOrNull()
                if (since == null) {
                    call.respond(HttpStatusCode.BadRequest, OrderErrorCode.INVALID_ORDER_ID.toResponse())
                    return@get
                }
                call.respond(useCase.getOrdersSince(userId, since))
            }

            patch("/{id}/status") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, OrderErrorCode.INVALID_ORDER_ID.toResponse())
                    return@patch
                }
                val request = call.receive<UpdateOrderStatusRequest>()
                when (val result = useCase.updateStatus(id, request.status)) {
                    is OrderResult.Success -> call.respond(HttpStatusCode.OK, result.order)

                    OrderResult.NotFound ->
                        call.respond(HttpStatusCode.NotFound, OrderErrorCode.ORDER_NOT_FOUND.toResponse())
                }
            }
        }
    }
}

private class KtorOrderSocketClient(private val session: DefaultWebSocketSession) : OrderSocketClient {
    override fun trySend(payload: String): Boolean = try {
        session.outgoing.trySend(Frame.Text(payload)).isSuccess
    } catch (_: ClosedSendChannelException) {
        false
    }
}
