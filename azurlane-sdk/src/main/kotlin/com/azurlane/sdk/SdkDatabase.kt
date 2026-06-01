package com.azurlane.sdk

import com.azurlane.infra.config.AlsConfigLoader
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object SdkUsers : Table("sdk_users") {
    val uid = integer("uid").autoIncrement("sdk_uid_seq")
    val username = varchar("username", 128)
    val accessKey = varchar("access_key", 64)
    val expires = varchar("expires", 32)
    val renewalExpires = varchar("renewal_expires", 32)
    val uname = varchar("uname", 128).default("")
    val face = varchar("face", 256).default("")
    val sFace = varchar("s_face", 256).default("")
    val sex = varchar("sex", 8).default("0")
    val telStatus = varchar("tel_status", 8).default("0")
    val realnameVerified = varchar("realname_verified", 8).default("1")
    val loginType = varchar("login_type", 8).default("1")
    val gameOpenId = varchar("game_open_id", 64).default("")
    val gameOpenIdEnable = bool("game_open_id_enable").default(true)
    val isAutoLogin = varchar("is_auto_login", 8).default("1")
    val isReg = varchar("is_reg", 8).default("0")
    val isCacheLogin = varchar("is_cache_login", 8).default("0")
    val isNew = varchar("is_new", 8).default("0")

    override val primaryKey = PrimaryKey(uid)
}

object SdkOrders : Table("sdk_orders") {
    val orderNo = varchar("order_no", 128)
    val orderToken = varchar("order_token", 64)
    val money = integer("money")
    val status = varchar("status", 32).default("pending")
    val uid = integer("uid").references(SdkUsers.uid).default(0)
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(orderNo)
}

object SdkDatabase {

    private val logger = KotlinLogging.logger {}

    private var database: Database? = null

    fun init() {
        val baseDir = AlsConfigLoader.getBaseDirectory()
        val dbDir = File(baseDir, "data")
        dbDir.mkdirs()
        val dbPath = File(dbDir, "sdk.db").absolutePath

        val hikariConfig = HikariConfig().apply {
            driverClassName = "org.sqlite.JDBC"
            jdbcUrl = "jdbc:sqlite:$dbPath"
            maximumPoolSize = 1
            isAutoCommit = true
        }
        val dataSource = HikariDataSource(hikariConfig)

        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA synchronous=NORMAL")
                stmt.execute("PRAGMA foreign_keys=ON")
            }
        }

        database = Database.connect(dataSource)

        transaction(database) {
            create(SdkUsers, SdkOrders)
        }

        logger.info { "SDK database initialized at $dbPath" }
    }

    fun getDatabase(): Database = database ?: throw IllegalStateException("SDK database not initialized")
}
