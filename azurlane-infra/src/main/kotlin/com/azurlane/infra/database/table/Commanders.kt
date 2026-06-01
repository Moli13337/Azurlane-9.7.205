package com.azurlane.infra.database.table

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Commanders : Table("commanders") {
    val commanderId = integer("commander_id")
    val accountId = integer("account_id").uniqueIndex()
    val name = text("name")
    val level = integer("level").default(1)
    val exp = long("exp").default(0L)
    val lastLogin = long("last_login").default(System.currentTimeMillis())
    val guideIndex = integer("guide_index").default(0)
    val newGuideIndex = integer("new_guide_index").default(0)
    val nameChangeCooldown = long("name_change_cooldown").default(0L)
    val roomId = integer("room_id").default(0)
    val exchangeCount = integer("exchange_count").default(0)
    val drawCount1 = integer("draw_count1").default(0)
    val drawCount10 = integer("draw_count10").default(0)
    val supportRequisitionCount = integer("support_requisition_count").default(0)
    val supportRequisitionMonth = integer("support_requisition_month").default(0)
    val attackCount = integer("attack_count").default(0)
    val winCount = integer("win_count").default(0)
    val buyOilCount = integer("buy_oil_count").default(0)
    val manifesto = text("manifesto").default("")
    val dormName = text("dorm_name").default("")
    val randomShipMode = integer("random_ship_mode").default(0)
    val randomFlagShipEnabled = integer("random_flag_ship_enabled").default(0)
    val livingAreaCoverId = integer("living_area_cover_id").default(0)
    val selectedIconFrameId = integer("selected_icon_frame_id").default(0)
    val selectedChatFrameId = integer("selected_chat_frame_id").default(0)
    val selectedBattleUiId = integer("selected_battle_ui_id").default(0)
    val displayIconId = integer("display_icon_id").default(0)
    val displaySkinId = integer("display_skin_id").default(0)
    val displayIconThemeId = integer("display_icon_theme_id").default(0)
    val score = integer("score").default(0)
    val rank = integer("rank").default(0)
    val chatRoomId = integer("chat_room_id").default(0)
    val proposeShipId = integer("propose_ship_id").default(0)
    val registerTime = long("register_time").default(System.currentTimeMillis())
    val deletedAt = long("deleted_at").nullable()
    val shipBagMax = integer("ship_bag_max").default(250)
    val equipBagMax = integer("equip_bag_max").default(250)
    val commanderBagMax = integer("commander_bag_max").default(250)
    val gmFlag = integer("gm_flag").default(0)
    val mailStoreroomLv = integer("mail_storeroom_lv").default(1)
    val pvpAttackCount = integer("pvp_attack_count").default(0)
    val pvpWinCount = integer("pvp_win_count").default(0)
    val collectAttackCount = integer("collect_attack_count").default(0)
    val maxRank = integer("max_rank").default(0)
    val accPayLv = integer("acc_pay_lv").default(0)
    val selectedMedalIds = text("selected_medal_ids").default("[]")
    val guildWaitTime = integer("guild_wait_time").default(0)
    val chatMsgBanTime = integer("chat_msg_ban_time").default(0)
    val rmb = integer("rmb").default(0)
    val themeUploadNotAllowedTime = integer("theme_upload_not_allowed_time").default(0)
    val childDisplay = integer("child_display").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object CommanderCommonFlags : Table("commander_common_flags") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val flagId = integer("flag_id")
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(commanderId, flagId)
}

object CommanderAttires : Table("commander_attires") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val type = integer("type")
    val attireId = integer("attire_id")
    val expiresAt = long("expires_at").nullable()
    val isNew = integer("is_new").default(1)

    override val primaryKey = PrimaryKey(commanderId, type, attireId)
}

object CommanderBuffs : Table("commander_buffs") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val buffId = integer("buff_id")
    val expiresAt = long("expires_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId, buffId)
}

object CommanderItems : Table("commander_items") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val itemId = integer("item_id").references(Items.id, onDelete = ReferenceOption.CASCADE)
    val count = integer("count").default(0)

    override val primaryKey = PrimaryKey(commanderId, itemId)
}

object CommanderMiscItems : Table("commander_misc_items") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val itemId = integer("item_id").references(Items.id, onDelete = ReferenceOption.CASCADE)
    val data = long("data").default(0L)

    override val primaryKey = PrimaryKey(commanderId, itemId, data)
}
