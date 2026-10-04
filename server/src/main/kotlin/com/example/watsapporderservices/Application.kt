package com.example.watsapporderservices

import com.example.watsapporderservices.data.database.DatabaseConfig
import com.example.watsapporderservices.data.database.DatabaseFactory
import com.example.watsapporderservices.data.repositoryImpl.AiRepositoryImpl
import com.example.watsapporderservices.data.repositoryImpl.AuthRepositoryImpl
import com.example.watsapporderservices.data.repositoryImpl.InputRepositoryImpl
import com.example.watsapporderservices.data.repositoryImpl.MessageRepositoryImpl
import com.example.watsapporderservices.data.repositoryImpl.WebhookRepositoryImpl
import com.example.watsapporderservices.data.security.FirebaseConfig
import com.example.watsapporderservices.data.security.FirebaseTokenVerifier
import com.example.watsapporderservices.data.security.GroqConfig
import com.example.watsapporderservices.data.security.JwtConfig
import com.example.watsapporderservices.data.security.PasswordHasher
import com.example.watsapporderservices.data.security.TokenService
import com.example.watsapporderservices.data.security.WhatsAppConfig
import com.example.watsapporderservices.data.database.input.InputDao
import com.example.watsapporderservices.data.database.product.ProductDao
import com.example.watsapporderservices.data.database.step.StepDao
import com.example.watsapporderservices.data.database.step.StepInputDao
import com.example.watsapporderservices.data.database.store.StoreDao
import com.example.watsapporderservices.data.database.user.UserDao
import com.example.watsapporderservices.data.repositoryImpl.ProductRepositoryImpl
import com.example.watsapporderservices.data.repositoryImpl.StoreRepositoryImpl
import com.example.watsapporderservices.domain.usecase.AuthUseCase
import com.example.watsapporderservices.domain.usecase.InputUseCase
import com.example.watsapporderservices.domain.usecase.MessageUseCase
import com.example.watsapporderservices.domain.usecase.ProductUseCase
import com.example.watsapporderservices.domain.usecase.StoreUseCase
import com.example.watsapporderservices.domain.usecase.WebhookUseCase
import com.example.watsapporderservices.plugins.configureSecurity
import com.example.watsapporderservices.plugins.configureSerialization
import com.example.watsapporderservices.plugins.configureStatusPages
import com.example.watsapporderservices.routes.authRoutes
import com.example.watsapporderservices.routes.inputRoutes
import com.example.watsapporderservices.routes.messageRoutes
import com.example.watsapporderservices.routes.productRoutes
import com.example.watsapporderservices.routes.storeRoutes
import com.example.watsapporderservices.routes.webhookRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init(DatabaseConfig.from(environment.config))
    val tokenService = TokenService(JwtConfig.from(environment.config, System.getenv()))
    val firebaseConfig = FirebaseConfig.from(environment.config, System.getenv())
    if (!firebaseConfig.isConfigured) {
        log.warn("FIREBASE_PROJECT_ID is not set: /auth/federated will reject every token")
    }
    val authRepository = AuthRepositoryImpl(
        UserDao(),
        PasswordHasher(),
        tokenService,
        FirebaseTokenVerifier(firebaseConfig.projectId),
    )
    val authUseCase = AuthUseCase(authRepository)
    val whatsAppConfig = WhatsAppConfig.from(environment.config, System.getenv())
    val groqConfig = GroqConfig.from(environment.config, System.getenv())
    if (!groqConfig.isConfigured) {
        log.warn("TOKEN_GROK is not set: the WhatsApp auto-reply will not work")
    }
    val messageRepository = MessageRepositoryImpl(whatsAppConfig)
    val storeDao = StoreDao()
    val storeRepository = StoreRepositoryImpl(storeDao)
    val productRepository = ProductRepositoryImpl(ProductDao(), StepDao(), StepInputDao(), InputDao(), storeDao)
    val webhookUseCase = WebhookUseCase(
        WebhookRepositoryImpl(
            whatsAppConfig,
            AiRepositoryImpl(groqConfig),
            messageRepository,
            storeRepository,
            productRepository,
        ),
    )
    val messageUseCase = MessageUseCase(messageRepository)
    val productUseCase = ProductUseCase(productRepository)
    val storeUseCase = StoreUseCase(storeRepository)
    val inputUseCase = InputUseCase(InputRepositoryImpl(InputDao()))
    configureSerialization()
    configureStatusPages()
    configureSecurity(tokenService)
    routing {
        authRoutes(authUseCase)
        webhookRoutes(webhookUseCase)
        messageRoutes(messageUseCase)
        storeRoutes(storeUseCase)
        productRoutes(productUseCase)
        inputRoutes(inputUseCase)
    }
}
