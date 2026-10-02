package com.example.watsapporderservices.data.enum

enum class AuthErrorCode(val code: String) {
    INVALID_EMAIL("invalid_email"),
    INVALID_PASSWORD("invalid_password"),
    INVALID_NAME("invalid_name"),
    EMAIL_ALREADY_REGISTERED("email_already_registered"),
    INVALID_CREDENTIALS("invalid_credentials"),
    INVALID_REQUEST("invalid_request"),
    INVALID_TOKEN("invalid_token"),
    UNAUTHORIZED("unauthorized"),
    USER_NOT_FOUND("user_not_found"),
    INTERNAL_ERROR("internal_error"),
}
