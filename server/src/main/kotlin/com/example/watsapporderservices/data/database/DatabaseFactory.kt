package com.example.watsapporderservices.data.database

import com.example.watsapporderservices.data.database.input.Inputs
import com.example.watsapporderservices.data.database.order.Orders
import com.example.watsapporderservices.data.database.product.Products
import com.example.watsapporderservices.data.database.session.Sessions
import com.example.watsapporderservices.data.database.step.StepInputs
import com.example.watsapporderservices.data.database.step.Steps
import com.example.watsapporderservices.data.database.store.Stores
import com.example.watsapporderservices.data.database.user.AUTH_PROVIDER_MAX_LENGTH
import com.example.watsapporderservices.data.database.user.FIREBASE_UID_MAX_LENGTH
import com.example.watsapporderservices.data.database.user.Users
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {
    fun init(config: DatabaseConfig) {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = config.maxPoolSize
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            },
        )
        Database.connect(dataSource)
        transaction {
            SchemaUtils.create(
                Users,
                Products,
                Steps,
                Inputs,
                StepInputs,
                Stores,
                Orders,
                Sessions,
            )
        }
    }

}
