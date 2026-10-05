package com.example.watsapporderservices.data.realtime

import com.example.watsapporderservices.data.mapper.OrderResponse
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * In-memory registry of live order sockets, grouped by store id. When an order is created the repository
 * calls [broadcastOrder]; every connected client of that store receives the order JSON with its sequence.
 *
 * The socket has no retry loop: it stays open. A client that was disconnected uses the sequence endpoints
 * (`GET /orders/sequence` and `GET /orders/since/{sequence}`) to catch up.
 */
class OrderSocketManager {
    private val logger = LoggerFactory.getLogger(OrderSocketManager::class.java)
    private val json = Json { encodeDefaults = true; explicitNulls = false }
    private val clients = mutableMapOf<Int?, MutableSet<OrderSocketClient>>()

    fun register(storeId: Int?, client: OrderSocketClient) {
        synchronized(clients) {
            clients.getOrPut(storeId) { mutableSetOf() }.add(client)
        }
        logger.info("Order socket connected (store={}, total={})", storeId ?: "unknown", clientCount())
    }

    fun unregister(storeId: Int?, client: OrderSocketClient) {
        synchronized(clients) {
            clients[storeId]?.remove(client)
            if (clients[storeId].isNullOrEmpty()) clients.remove(storeId)
        }
    }

    private fun clientCount(): Int = synchronized(clients) { clients.values.sumOf { it.size } }

    /** Sends the order to every connected client of its store. */
    fun broadcastOrder(order: OrderResponse) {
        val targets = synchronized(clients) {
            (clients[order.storeId].orEmpty() + clients[null].orEmpty()).toList()
        }
        if (targets.isEmpty()) return

        val payload = json.encodeToString(OrderResponse.serializer(), order)
        targets.forEach { client ->
            if (!client.trySend(payload)) {
                unregister(order.storeId, client)
                logger.warn("Drop dead order socket (store={})", order.storeId)
            }
        }
    }
}

/** Minimal contract so the manager does not depend on Ktor's session type. */
interface OrderSocketClient {
    /** Returns true when the frame could be queued; false when the socket is dead. */
    fun trySend(payload: String): Boolean
}
