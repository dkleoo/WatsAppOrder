package com.example.watsapporderservices.data.enum

enum class MessageErrorCode(val code: String) {
    INVALID_EXTENSION("invalid_extension"),
    INVALID_NUMBER("invalid_number"),
    INVALID_MESSAGE("invalid_message"),
    NOT_CONFIGURED("whatsapp_not_configured"),
    SEND_FAILED("send_failed"),
}
