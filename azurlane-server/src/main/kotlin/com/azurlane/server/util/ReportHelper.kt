package com.azurlane.server.util

import com.azurlane.infra.database.table.Punishments
import mu.KotlinLogging
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object ReportHelper {

    private val logger = KotlinLogging.logger {}

    enum class ReportType(val code: Int, val label: String) {
        CHAT(1, "chat"),
        THEME(2, "theme"),
        THEME_DETAIL(3, "theme_detail"),
        DISCUSS(4, "discuss"),
        SHARE(5, "share"),
        OTHER(99, "other");

        companion object {
            fun fromCode(code: Int): ReportType = entries.find { it.code == code } ?: OTHER
        }
    }

    fun submitReport(
        reporterId: Int,
        targetId: Int,
        type: ReportType,
        reason: String = "",
        extraInfo: String = ""
    ) {
        try {
            transaction {
                Punishments.insert {
                    it[commanderId] = targetId
                    it[Punishments.reason] = "reported_by=$reporterId type=${type.label} reason=$reason extra=$extraInfo"
                }
            }
            logger.info { "report submitted: reporter=$reporterId target=$targetId type=${type.label} reason=$reason" }
        } catch (e: Exception) {
            logger.error(e) { "report submission failed: reporter=$reporterId target=$targetId type=${type.label}" }
        }
    }

    fun submitReport(
        reporterId: Int,
        targetId: Int,
        typeCode: Int,
        reason: String = "",
        extraInfo: String = ""
    ) {
        submitReport(reporterId, targetId, ReportType.fromCode(typeCode), reason, extraInfo)
    }
}
