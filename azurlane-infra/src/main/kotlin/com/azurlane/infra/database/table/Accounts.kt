package com.azurlane.infra.database.table

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Accounts : Table("accounts") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = text("password_hash")
    val passwordAlgo = varchar("password_algo", 32).default("argon2id")
    val isAdmin = integer("is_admin").default(0)
    val commanderId = integer("commander_id").uniqueIndex().nullable()
    val disabledAt = long("disabled_at").nullable()
    val lastLoginAt = long("last_login_at").default(0L)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}

object LocalAccounts : Table("local_accounts") {
    val arg2 = integer("arg2").uniqueIndex()
    val account = varchar("account", 128).uniqueIndex()
    val password = text("password")
    val mailBox = varchar("mail_box", 256).default("")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(arg2)
}

object DeviceAuthMaps : Table("device_auth_maps") {
    val deviceId = varchar("device_id", 128)
    val arg2 = integer("arg2")
    val accountId = integer("account_id").default(0)
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(deviceId)
}

object Sessions : Table("sessions") {
    val id = varchar("id", 36)
    val accountId = integer("account_id").references(Accounts.id, onDelete = ReferenceOption.CASCADE)
    val csrfToken = text("csrf_token")
    val ipAddress = text("ip_address").default("")
    val userAgent = text("user_agent").default("")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val expiresAt = long("expires_at")
    val revokedAt = long("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AuditLogs : Table("audit_logs") {
    val id = integer("id").autoIncrement()
    val actorAccountId = integer("actor_account_id").references(Accounts.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val actorCommanderId = integer("actor_commander_id").nullable()
    val method = text("method")
    val path = text("path")
    val statusCode = integer("status_code")
    val permissionKey = text("permission_key").nullable()
    val action = text("action").nullable()
    val metadata = text("metadata").nullable()
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}
