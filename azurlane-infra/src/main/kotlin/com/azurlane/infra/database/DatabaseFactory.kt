package com.azurlane.infra.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object DatabaseFactory {
    fun init(dbPath: String, walMode: Boolean = true): Database {
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        val hikariConfig = HikariConfig().apply {
            driverClassName = "org.sqlite.JDBC"
            jdbcUrl = "jdbc:sqlite:$dbPath"
            maximumPoolSize = 1
            isAutoCommit = true
        }
        val dataSource = HikariDataSource(hikariConfig)

        if (walMode) {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode=WAL")
                    stmt.execute("PRAGMA synchronous=NORMAL")
                    stmt.execute("PRAGMA foreign_keys=ON")
                }
            }
        }

        return Database.connect(dataSource)
    }

    fun createTables(database: Database, vararg tables: Table) {
        transaction(database) {
            SchemaUtils.create(*tables)
        }
    }
}
