package com.example.watsapporderservices

import com.example.watsapporderservices.data.mapper.WhatsAppInteractiveText
import com.example.watsapporderservices.data.mapper.WhatsAppListAction
import com.example.watsapporderservices.data.mapper.WhatsAppListInteractive
import com.example.watsapporderservices.data.mapper.WhatsAppListPayload
import com.example.watsapporderservices.data.mapper.WhatsAppListRow
import com.example.watsapporderservices.data.mapper.WhatsAppListSection
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class MessagePayloadTest {
    @Test
    fun listPayloadHasMetaShape() {
        val json = Json {
            encodeDefaults = true
            explicitNulls = false
        }
        val payload = WhatsAppListPayload(
            to = "573123463046",
            interactive = WhatsAppListInteractive(
                body = WhatsAppInteractiveText(text = "Estos son los productos"),
                action = WhatsAppListAction(
                    button = "Ver productos",
                    sections = listOf(
                        WhatsAppListSection(
                            title = "Productos",
                            rows = listOf(WhatsAppListRow(id = "product:1", title = "Coca cola")),
                        ),
                    ),
                ),
            ),
        )
        val encoded = json.encodeToString(WhatsAppListPayload.serializer(), payload)
        println(encoded)
        assertTrue(encoded.contains("\"body\":{\"text\":"), "body must be {text}")
        assertTrue(!encoded.contains("\"body\":{\"type\""), "body must not include type")
        assertTrue(!encoded.contains("\"header\""), "null header must be omitted")
    }
}
