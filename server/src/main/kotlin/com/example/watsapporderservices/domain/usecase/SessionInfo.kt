package com.example.watsapporderservices.domain.usecase

import com.example.watsapporderservices.data.enum.SessionState

data class SessionInfo(
    val id: Int,
    val customerPhone: String,
    val storeId: Int?,
    val state: SessionState,
    val optionProductIds: List<Int>,
    val lastActivityAt: Long,
    val isNew: Boolean,
)
