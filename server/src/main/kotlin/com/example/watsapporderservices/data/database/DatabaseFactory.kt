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
            migrateUsers()
            migrateProducts()
            migrateStores()
            dropLegacyStoreProducts()
        }
    }

    /**
     * The federated auth feature added columns to a pre-existing `users` table. `SchemaUtils.create`
     * only creates missing tables, so older databases need explicit migrations.
     */
    private fun JdbcTransaction.migrateUsers() {
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS firebase_uid VARCHAR($FIREBASE_UID_MAX_LENGTH)")
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_provider VARCHAR($AUTH_PROVIDER_MAX_LENGTH)")
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS store_id INT")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS users_firebase_uid_unique ON users (firebase_uid)")
    }

    /** Adds the store column to a pre-existing `products` table. */
    private fun JdbcTransaction.migrateProducts() {
        exec("ALTER TABLE products ADD COLUMN IF NOT EXISTS store_id INT")
    }

    /** Adds the owning user to a pre-existing `stores` table. */
    private fun JdbcTransaction.migrateStores() {
        exec("ALTER TABLE stores ADD COLUMN IF NOT EXISTS user_id INT")
    }

    /**
     * The old `store_products` pivot is no longer used: the relation now lives in `products.store_id`.
     * We copy any existing links (only for products without a store) and drop the table.
     */
    private fun JdbcTransaction.dropLegacyStoreProducts() {
        exec(
            "DO $$ BEGIN " +
                "IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'store_products') THEN " +
                "UPDATE products p SET store_id = sp.store_id FROM store_products sp " +
                "WHERE sp.product_id = p.id AND p.store_id IS NULL; " +
                "DROP TABLE store_products; " +
                "END IF; END $$",
        )
    }
}
