package com.example.watsapporderservices.data.database

import com.example.watsapporderservices.data.database.input.Inputs
import com.example.watsapporderservices.data.database.product.Products
import com.example.watsapporderservices.data.database.step.StepInputs
import com.example.watsapporderservices.data.database.step.Steps
import com.example.watsapporderservices.data.database.store.StoreProducts
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
            SchemaUtils.create(Users, Products, Steps, Inputs, StepInputs, Stores, StoreProducts)
            migrateUsers()
        }
    }

    /**
     * Adds the federation columns to a pre-existing `users` table. `SchemaUtils.create` only creates
     * missing tables, so databases created before the federated auth feature need an explicit migration.
     */
    private fun JdbcTransaction.migrateUsers() {
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS firebase_uid VARCHAR($FIREBASE_UID_MAX_LENGTH)")
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_provider VARCHAR($AUTH_PROVIDER_MAX_LENGTH)")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS users_firebase_uid_unique ON users (firebase_uid)")
    }
}
