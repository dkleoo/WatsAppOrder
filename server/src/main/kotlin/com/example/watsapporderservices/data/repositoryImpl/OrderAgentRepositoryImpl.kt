package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.enum.OrderStatus
import com.example.watsapporderservices.data.enum.OrderType
import com.example.watsapporderservices.data.mapper.StoreResponse
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.data.realtime.OrderSocketManager
import com.example.watsapporderservices.domain.repository.AiRepository
import com.example.watsapporderservices.domain.repository.MessageRepository
import com.example.watsapporderservices.domain.repository.NotificationRepository
import com.example.watsapporderservices.domain.repository.OrderAgentRepository
import com.example.watsapporderservices.domain.repository.OrderRepository
import com.example.watsapporderservices.domain.repository.ProductRepository
import com.example.watsapporderservices.domain.repository.SessionRepository
import com.example.watsapporderservices.domain.usecase.AiChatResult
import com.example.watsapporderservices.domain.usecase.AiMessage
import com.example.watsapporderservices.domain.usecase.AiTool
import com.example.watsapporderservices.domain.usecase.AiToolCall
import com.example.watsapporderservices.domain.usecase.MessageResult
import com.example.watsapporderservices.domain.usecase.OrderDraft
import com.example.watsapporderservices.domain.usecase.OrderItemDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

private const val ROLE_SYSTEM = "system"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val ROLE_TOOL = "tool"

private const val HISTORY_LIMIT = 20
private const val MAX_TOOL_ITERATIONS = 6
private const val MAX_REPLY_LENGTH = 4096
private const val MAX_CATALOG_ITEMS = 30

private const val FALLBACK_REPLY = "No pude procesar tu mensaje, ¿puedes repetirlo?"
private const val NOT_CONFIGURED_REPLY = "El asistente no está disponible en este momento."

private const val TOOL_BUSCAR_PRODUCTOS = "buscar_productos"
private const val TOOL_VER_PRODUCTO = "ver_producto"
private const val TOOL_AGREGAR_PRODUCTO = "agregar_producto"
private const val TOOL_QUITAR_PRODUCTO = "quitar_producto"
private const val TOOL_VER_PEDIDO = "ver_pedido"
private const val TOOL_INFO_TIENDA = "info_tienda"
private const val TOOL_CONFIRMAR_PEDIDO = "confirmar_pedido"

/**
 * Order-taking agent: instead of calling HTTP endpoints, every tool reads/writes the database through the
 * repositories. The webhook only delivers the message and the reply.
 */
class OrderAgentRepositoryImpl(
    private val aiRepository: AiRepository,
    private val messageRepository: MessageRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val sessionRepository: SessionRepository,
    private val socketManager: OrderSocketManager,
    private val notificationRepository: NotificationRepository,
    private val systemPrompt: String,
) : OrderAgentRepository {
    private val logger = LoggerFactory.getLogger(OrderAgentRepositoryImpl::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tools = buildTools()

    override suspend fun handleMessage(customerPhone: String, store: StoreResponse?, text: String) {
        val session = sessionRepository.resume(customerPhone, store?.id)
        sessionRepository.appendMessage(session.id, ROLE_USER, text)
        val context = AgentContext(
            customerPhone = customerPhone,
            store = store,
            sessionId = session.id,
            orderId = session.orderId,
        )
        val reply = runCatching { runAgent(context) }.getOrElse {
            logger.error("Agent failed", it)
            FALLBACK_REPLY
        }
        sessionRepository.appendMessage(session.id, ROLE_ASSISTANT, reply)
        sendText(customerPhone, reply)
    }

    private suspend fun runAgent(context: AgentContext): String {
        val messages = mutableListOf(AiMessage(role = ROLE_SYSTEM, content = systemMessage(context)))
        sessionRepository.history(context.sessionId, HISTORY_LIMIT).forEach { message ->
            if (message.role == ROLE_USER || message.role == ROLE_ASSISTANT) {
                messages += AiMessage(role = message.role, content = message.content)
            }
        }

        repeat(MAX_TOOL_ITERATIONS) {
            when (val step = aiRepository.chat(messages, tools)) {
                is AiChatResult.Message -> return step.content?.takeIf { it.isNotBlank() } ?: FALLBACK_REPLY

                is AiChatResult.ToolCalls -> {
                    messages += AiMessage(role = ROLE_ASSISTANT, toolCalls = step.calls)
                    step.calls.forEach { call ->
                        val output = executeTool(call, context)
                        logger.debug("Tool {} -> {}", call.name, output.take(300))
                        messages += AiMessage(role = ROLE_TOOL, content = output, toolCallId = call.id)
                    }
                }

                AiChatResult.NotConfigured -> {
                    logger.warn("Groq is not configured (TOKEN_GROK missing): answering with the fallback")
                    return NOT_CONFIGURED_REPLY
                }

                is AiChatResult.Failed -> {
                    logger.warn("Groq chat failed: {}", step.detail)
                    return FALLBACK_REPLY
                }
            }
        }
        logger.warn("Agent gave up after {} tool iterations without a final answer", MAX_TOOL_ITERATIONS)
        return FALLBACK_REPLY
    }

    private fun systemMessage(context: AgentContext): String = buildString {
        append(systemPrompt)
        append("\n\nUsa las herramientas para consultar productos y para registrar el pedido. Nunca inventes precios.")
        if (context.store == null) {
            append("\nLa tienda no está configurada: si el cliente quiere pedir, avísale que no está disponible.")
        }
    }

    private suspend fun executeTool(call: AiToolCall, context: AgentContext): String =
        runCatching { dispatch(call.name, parseArgs(call.arguments), context) }
            .getOrElse { "Error en ${call.name}: ${it.message ?: "fallo desconocido"}" }

    private suspend fun dispatch(name: String, args: JsonObject, context: AgentContext): String = when (name) {
        TOOL_BUSCAR_PRODUCTOS -> buscarProductos(context, args.string("consulta"))
        TOOL_VER_PRODUCTO -> verProducto(args.int("productoId"))
        TOOL_AGREGAR_PRODUCTO -> agregarProducto(context, args)
        TOOL_QUITAR_PRODUCTO -> quitarProducto(context, args)
        TOOL_VER_PEDIDO -> verPedido(context)
        TOOL_INFO_TIENDA -> infoTienda(context)
        TOOL_CONFIRMAR_PEDIDO -> confirmarPedido(context, args)
        else -> "Herramienta desconocida: $name"
    }

    private suspend fun buscarProductos(context: AgentContext, consulta: String?): String {
        val store = context.store ?: return "La tienda no está configurada todavía."
        val products = productRepository.searchByStore(store.whatsappBusinessPhone, store.idWhatsApp, consulta)
        if (products.isEmpty()) {
            return if (consulta.isNullOrBlank()) "El catálogo está vacío." else "No encontré productos para \"$consulta\"."
        }
        return buildString {
            append("Productos (id | nombre | precio):\n")
            products.take(MAX_CATALOG_ITEMS).forEach {
                append("- ${it.id} | ${it.name} | $${it.price}\n")
            }
        }
    }

    private suspend fun verProducto(productoId: Int?): String {
        if (productoId == null) return "Falta el id del producto."
        val product = productRepository.getProduct(productoId) ?: return "No existe el producto $productoId."
        if (product.steps.isEmpty()) {
            return "Producto ${product.id}: ${product.name} - $${product.price}. No tiene pasos ni opciones."
        }
        return buildString {
            append("Producto ${product.id}: ${product.name} - $${product.price}\n")
            product.steps.forEach { step ->
                val options = step.inputs.joinToString(", ") { "id=${it.id} ${it.name} ($${it.price})" }
                append("Paso \"${step.name}\": ${options.ifEmpty { "sin opciones" }}\n")
            }
            append("Si el cliente no indicó estas opciones, pregúntale y luego agrega con \"opciones\".")
        }
    }

    private suspend fun agregarProducto(context: AgentContext, args: JsonObject): String {
        val productoId = args.int("productoId") ?: return "Falta productoId."
        val quantity = (args.int("cantidad") ?: 1).coerceAtLeast(1)
        val options = args.intList("opciones").distinct()
        val product = productRepository.getProduct(productoId) ?: return "No existe el producto $productoId."

        val allInputs = product.steps.flatMap { it.inputs }
        if (allInputs.isNotEmpty() && options.isEmpty()) {
            val detail = product.steps.joinToString("; ") { step ->
                "${step.name}: " + step.inputs.joinToString(", ") { "${it.id} ${it.name}" }
            }
            return "El producto ${product.name} requiere elegir opciones ($detail). Pregúntale al cliente y vuelve a llamar con \"opciones\"."
        }
        val selected = allInputs.filter { it.id in options }
        val unitPrice = product.price + selected.sumOf { it.price }
        val orderId = ensureOrder(context) ?: return "No pude iniciar el pedido."
        orderRepository.addItem(
            orderId,
            OrderItemDraft(
                id = null,
                productId = product.id,
                productName = product.name,
                unitPrice = unitPrice,
                selectedInputIds = selected.map { it.id },
                stepName = null,
                quantity = quantity,
            ),
        )
        return "Agregado ${quantity} x ${product.name} ($$unitPrice c/u). ${orderSummaryLine(context)}"
    }

    private suspend fun quitarProducto(context: AgentContext, args: JsonObject): String {
        val order = context.orderId?.let { orderRepository.getOrder(it) } ?: return "No hay pedido en curso."
        val itemId = args.int("itemId")
        val productoId = args.int("productoId")
        val item = order.items.lastOrNull {
            (itemId != null && it.id == itemId) || (productoId != null && it.productId == productoId)
        } ?: return "No encontré ese producto en el pedido."
        val orderId = order.id ?: return "No encontré ese producto en el pedido."
        val lineId = item.id ?: return "No encontré ese producto en el pedido."
        orderRepository.removeItem(orderId, lineId)
        return "Quité ${item.productName}. ${orderSummaryLine(context)}"
    }

    private suspend fun verPedido(context: AgentContext): String {
        val order = context.orderId?.let { orderRepository.getOrder(it) } ?: return "Todavía no hay productos en el pedido."
        if (order.items.isEmpty()) return "Todavía no hay productos en el pedido."
        return buildString {
            append("Pedido actual:\n")
            order.items.forEach { append("- ${it.quantity} x ${it.productName} = $${it.unitPrice * it.quantity}\n") }
            append("Total: $${order.totalLine()}")
        }
    }

    private fun infoTienda(context: AgentContext): String {
        val store = context.store ?: return "La tienda no está configurada."
        return "Dirección: ${store.address}. Teléfono: ${store.phone}."
    }

    private suspend fun confirmarPedido(context: AgentContext, args: JsonObject): String {
        val order = context.orderId?.let { orderRepository.getOrder(it) } ?: return "No hay un pedido para confirmar."
        if (order.items.isEmpty()) return "Agrega al menos un producto antes de confirmar."

        val type = when (val raw = args.string("tipo")?.lowercase()) {
            null -> return "Falta indicar si es para comer en el lugar o para domicilio."
            else -> when {
                raw.contains("local") || raw.contains("lugar") -> OrderType.LOCAL
                raw.contains("domic") || raw.contains("delivery") || raw.contains("env") -> OrderType.DELIVERY
                else -> return "No entendí el tipo: indica \"local\" o \"domicilio\"."
            }
        }
        val name = args.string("nombre")?.trim()?.takeIf { it.isNotBlank() }
        val address = args.string("direccion")?.trim()?.takeIf { it.isNotBlank() }
        val payment = normalizePayment(args.string("pago"))
        if (type == OrderType.DELIVERY) {
            if (name == null) return "Para domicilio necesito el nombre de quien recibe."
            if (address == null) return "Para domicilio necesito la dirección de entrega."
            if (payment == null) return "Para domicilio necesito el método de pago (Efectivo o Transferencia)."
        }

        val placed = orderRepository.save(
            order.copy(
                orderType = type,
                customerName = name ?: order.customerName,
                deliveryAddress = address ?: order.deliveryAddress,
                paymentType = payment ?: order.paymentType,
                total = order.totalLine(),
                status = OrderStatus.PENDING,
            ),
        )
        sessionRepository.linkOrder(context.sessionId, context.store?.id, null)
        context.orderId = null
        runCatching { socketManager.broadcastOrder(placed.toResponse()) }
            .onFailure { logger.warn("Order broadcast failed: {}", it.message) }
        runCatching {
            notificationRepository.notifyNewOrder(
                storeId = placed.storeId,
                orderId = placed.id ?: 0,
                sequence = placed.sequence ?: 0,
                total = placed.total,
                customerName = placed.customerName,
            )
        }.onFailure { logger.warn("Order push notification failed: {}", it.message) }
        return buildOrderSummary(placed, type)
    }

    private suspend fun ensureOrder(context: AgentContext): Int? {
        context.orderId?.let { return it }
        val order = orderRepository.createDraft(context.store?.id, context.customerPhone)
        val id = order.id ?: return null
        context.orderId = id
        sessionRepository.linkOrder(context.sessionId, context.store?.id, id)
        return id
    }

    private suspend fun orderSummaryLine(context: AgentContext): String {
        val order = context.orderId?.let { orderRepository.getOrder(it) } ?: return "Pedido vacío."
        return "Total parcial: $${order.totalLine()}."
    }

    private suspend fun sendText(to: String, text: String) {
        val result = messageRepository.sendText(to, text.take(MAX_REPLY_LENGTH))
        if (result !is MessageResult.Sent) {
            logger.warn("Reply to {} failed: {}", to, result)
        }
    }

    private fun normalizePayment(raw: String?): String? {
        val value = raw?.lowercase()?.trim() ?: return null
        return when {
            value.contains("efect") -> "Efectivo"
            value.contains("transfer") || value.contains("nequi") || value.contains("daviplata") -> "Transferencia"
            else -> null
        }
    }

    private fun buildOrderSummary(order: OrderDraft, type: OrderType): String = buildString {
        append("✅ Pedido #${order.id} confirmado.\n")
        order.items.forEach { append("- ${it.quantity} x ${it.productName}\n") }
        append("Total: $${order.totalLine()}\n")
        append("Tipo: ${if (type == OrderType.LOCAL) "Comer en el lugar" else "Domicilio"}\n")
        order.customerName?.let { append("Nombre: $it\n") }
        order.deliveryAddress?.let { append("Dirección: $it\n") }
        order.paymentType?.let { append("Pago: $it\n") }
    }

    private fun parseArgs(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())

    private data class AgentContext(
        val customerPhone: String,
        val store: StoreResponse?,
        val sessionId: Int,
        var orderId: Int?,
    )
}

private fun OrderDraft.totalLine(): Double = items.sumOf { it.unitPrice * it.quantity }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.toIntOrNull()

private fun JsonObject.intList(key: String): List<Int> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content?.trim()?.toIntOrNull() }
        .orEmpty()

private fun stringProperty(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun intProperty(description: String) = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

private fun intArrayProperty(description: String) = buildJsonObject {
    put("type", "array")
    put("description", description)
    putJsonObject("items") { put("type", "integer") }
}

private fun enumProperty(description: String, vararg values: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
    putJsonArray("enum") { values.forEach { add(it) } }
}

private fun objectSchema(properties: JsonObject, required: List<String> = emptyList()): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", properties)
    if (required.isNotEmpty()) {
        putJsonArray("required") { required.forEach { add(it) } }
    }
}

private fun buildTools(): List<AiTool> = listOf(
    AiTool(
        name = TOOL_BUSCAR_PRODUCTOS,
        description = "Busca productos del catálogo de la tienda por nombre. Sin consulta devuelve todo el catálogo.",
        parameters = objectSchema(
            properties = buildJsonObject {
                put("consulta", stringProperty("Texto a buscar. Omite para ver todo el catálogo."))
            },
        ),
    ),
    AiTool(
        name = TOOL_VER_PRODUCTO,
        description = "Devuelve el detalle de un producto con sus pasos y opciones (insumos).",
        parameters = objectSchema(
            properties = buildJsonObject { put("productoId", intProperty("Id del producto.")) },
            required = listOf("productoId"),
        ),
    ),
    AiTool(
        name = TOOL_AGREGAR_PRODUCTO,
        description = "Agrega un producto al pedido. Si el producto tiene opciones, debes enviarlas en \"opciones\".",
        parameters = objectSchema(
            properties = buildJsonObject {
                put("productoId", intProperty("Id del producto."))
                put("cantidad", intProperty("Cantidad. Por defecto 1."))
                put("opciones", intArrayProperty("Ids de las opciones (insumos) elegidas."))
            },
            required = listOf("productoId"),
        ),
    ),
    AiTool(
        name = TOOL_QUITAR_PRODUCTO,
        description = "Quita un producto del pedido actual por productoId (o itemId).",
        parameters = objectSchema(
            properties = buildJsonObject {
                put("productoId", intProperty("Id del producto a quitar."))
                put("itemId", intProperty("Id de la línea a quitar."))
            },
        ),
    ),
    AiTool(
        name = TOOL_VER_PEDIDO,
        description = "Devuelve el resumen del pedido actual con total.",
        parameters = objectSchema(properties = buildJsonObject { }),
    ),
    AiTool(
        name = TOOL_INFO_TIENDA,
        description = "Devuelve la dirección y el teléfono de la tienda.",
        parameters = objectSchema(properties = buildJsonObject { }),
    ),
    AiTool(
        name = TOOL_CONFIRMAR_PEDIDO,
        description = "Confirma y crea el pedido. Para domicilio exige nombre, dirección y pago.",
        parameters = objectSchema(
            properties = buildJsonObject {
                put("tipo", enumProperty("Tipo de pedido.", "local", "domicilio"))
                put("nombre", stringProperty("Nombre de quien recibe (solo domicilio)."))
                put("direccion", stringProperty("Dirección de entrega (solo domicilio)."))
                put("pago", enumProperty("Método de pago (solo domicilio).", "Efectivo", "Transferencia"))
            },
            required = listOf("tipo"),
        ),
    ),
)
