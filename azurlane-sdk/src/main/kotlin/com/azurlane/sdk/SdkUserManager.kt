package com.azurlane.sdk

import com.azurlane.infra.config.AlsSdkSection
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit

data class SdkUser(
    val uid: String,
    val username: String,
    val accessKey: String,
    val expires: String,
    val renewalExpires: String,
    val uname: String = "",
    val face: String = "",
    val sFace: String = "",
    val sex: String = "0",
    val telStatus: String = "0",
    val realnameVerified: String = "1",
    val loginType: String = "1",
    val gameOpenId: String = "",
    val gameOpenIdEnable: Boolean = true,
    val isAutoLogin: String = "1",
    val isReg: String = "0",
    val isCacheLogin: String = "0",
    val isNew: String = "0"
)

data class SdkOrder(
    val orderNo: String,
    val orderToken: String,
    val money: Int,
    val status: String = "pending"
)

class SdkUserManager {

    private val logger = KotlinLogging.logger {}
    private var config: AlsSdkSection = AlsSdkSection()

    fun init(config: AlsSdkSection) {
        this.config = config
    }

    fun getOrCreateUser(name: String = "player"): Pair<String, SdkUser> {
        val (uid, user, _) = getOrCreateUserWithFlag(name)
        return uid to user
    }

    fun getOrCreateUserWithFlag(name: String = "player", loginType: String = "1"): Triple<String, SdkUser, Boolean> {
        val db = SdkDatabase.getDatabase()
        return transaction(db) {
            val existing = SdkUsers.selectAll().where { SdkUsers.username eq name }
                .map { it.toSdkUser() }
                .firstOrNull()

            if (existing != null) {
                Triple(existing.uid, existing, false)
            } else {
                val now = Instant.now()
                val uid = SdkUsers.insert {
                    it[SdkUsers.username] = name
                    it[SdkUsers.accessKey] = SdkSignHelper.md5Hex("${System.currentTimeMillis()}_$name")
                    it[SdkUsers.expires] = now.plus(config.tokenExpireDays.toLong(), ChronoUnit.DAYS).toEpochMilli().toString()
                    it[SdkUsers.renewalExpires] = now.plus(config.tokenRenewalDays.toLong(), ChronoUnit.DAYS).toEpochMilli().toString()
                    it[SdkUsers.uname] = name
                    it[SdkUsers.gameOpenId] = SdkSignHelper.md5Hex("goid_$name")
                    it[SdkUsers.loginType] = loginType
                    it[SdkUsers.isReg] = "1"
                    it[SdkUsers.isNew] = "1"
                } get SdkUsers.uid

                val user = SdkUsers.selectAll().where { SdkUsers.uid eq uid }.single().toSdkUser()
                Triple(uid.toString(), user, true)
            }
        }
    }

    fun createOrder(money: Int): SdkOrder {
        val db = SdkDatabase.getDatabase()
        return transaction(db) {
            val orderNo = "LOCAL${System.currentTimeMillis()}"
            val orderToken = SdkSignHelper.md5Hex("${orderNo}_${System.currentTimeMillis()}")
            SdkOrders.insert {
                it[SdkOrders.orderNo] = orderNo
                it[SdkOrders.orderToken] = orderToken
                it[SdkOrders.money] = money
                if (config.autoApprovePay) {
                    it[SdkOrders.status] = "success"
                }
            }
            SdkOrder(orderNo, orderToken, money, if (config.autoApprovePay) "success" else "pending")
        }
    }

    fun getUserByUid(uid: Int): SdkUser? {
        val db = SdkDatabase.getDatabase()
        return transaction(db) {
            SdkUsers.selectAll().where { SdkUsers.uid eq uid }
                .map { it.toSdkUser() }
                .firstOrNull()
        }
    }

    fun getUserByAccessKey(key: String): SdkUser? {
        val db = SdkDatabase.getDatabase()
        return transaction(db) {
            SdkUsers.selectAll().where { SdkUsers.accessKey eq key }
                .map { it.toSdkUser() }
                .firstOrNull()
        }
    }

    private fun ResultRow.toSdkUser() = SdkUser(
        uid = this[SdkUsers.uid].toString(),
        username = this[SdkUsers.username],
        accessKey = this[SdkUsers.accessKey],
        expires = this[SdkUsers.expires],
        renewalExpires = this[SdkUsers.renewalExpires],
        uname = this[SdkUsers.uname],
        face = this[SdkUsers.face],
        sFace = this[SdkUsers.sFace],
        sex = this[SdkUsers.sex],
        telStatus = this[SdkUsers.telStatus],
        realnameVerified = this[SdkUsers.realnameVerified],
        loginType = this[SdkUsers.loginType],
        gameOpenId = this[SdkUsers.gameOpenId],
        gameOpenIdEnable = this[SdkUsers.gameOpenIdEnable],
        isAutoLogin = this[SdkUsers.isAutoLogin],
        isReg = this[SdkUsers.isReg],
        isCacheLogin = this[SdkUsers.isCacheLogin],
        isNew = this[SdkUsers.isNew]
    )
}
