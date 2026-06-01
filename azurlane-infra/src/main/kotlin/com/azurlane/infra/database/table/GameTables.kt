package com.azurlane.infra.database.table

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Resources : Table("resources") {
    val id = integer("id")
    val name = text("name")
    val itemId = integer("item_id").default(0)

    override val primaryKey = PrimaryKey(id)
}

object OwnedResources : Table("owned_resources") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val resourceId = integer("resource_id")
    val amount = long("amount").default(0L)

    override val primaryKey = PrimaryKey(commanderId, resourceId)
}

object Items : Table("items") {
    val id = integer("id")
    val name = text("name")
    val rarity = integer("rarity")
    val shopId = integer("shop_id").default(-2)
    val type = integer("type")
    val virtualType = integer("virtual_type").default(0)

    override val primaryKey = PrimaryKey(id)
}

object ShipTemplates : Table("ship_templates") {
    val templateId = integer("template_id")
    val name = text("name")
    val englishName = text("english_name").default("")
    val rarityId = integer("rarity_id")
    val star = integer("star")
    val type = integer("type")
    val nationality = integer("nationality")
    val buildTime = integer("build_time").default(0)
    val poolId = integer("pool_id").nullable()
    val attrs = text("attrs").nullable()
    val attrsGrowth = text("attrs_growth").nullable()
    val attrsGrowthExtra = text("attrs_growth_extra").nullable()
    val starAttrs = text("star_attrs").nullable()

    override val primaryKey = PrimaryKey(templateId)
}

object OwnedShips : Table("owned_ships") {
    val id = integer("id").autoIncrement()
    val ownerId = integer("owner_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val templateId = integer("template_id")
    val level = integer("level").default(1)
    val exp = long("exp").default(0L)
    val surplusExp = long("surplus_exp").default(0L)
    val maxLevel = integer("max_level").default(50)
    val intimacy = integer("intimacy").default(5000)
    val energy = integer("energy").default(150)
    val isLocked = integer("is_locked").default(0)
    val propose = integer("propose").default(0)
    val commonFlag = integer("common_flag").default(0)
    val blueprintFlag = integer("blueprint_flag").default(0)
    val proficiency = integer("proficiency").default(0)
    val activityNpc = integer("activity_npc").default(0)
    val customName = text("custom_name").default("")
    val changeNameTimestamp = long("change_name_timestamp").default(0L)
    val skinId = integer("skin_id").default(0)
    val isSecretary = integer("is_secretary").default(0)
    val secretaryPosition = integer("secretary_position").nullable()
    val secretaryPhantomId = integer("secretary_phantom_id").default(0)
    val state = integer("state").default(1)
    val stateInfo1 = integer("state_info1").default(0)
    val stateInfo2 = integer("state_info2").default(0)
    val stateInfo3 = integer("state_info3").default(0)
    val stateInfo4 = integer("state_info4").default(0)
    val createTime = long("create_time").default(System.currentTimeMillis())
    val deletedAt = long("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, ownerId)
        index(false, templateId)
    }
}

object OwnedShipEquipments : Table("owned_ship_equipments") {
    val ownerId = integer("owner_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipId = integer("ship_id").references(OwnedShips.id, onDelete = ReferenceOption.CASCADE)
    val pos = integer("pos")
    val equipId = integer("equip_id").default(0)
    val skinId = integer("skin_id").default(0)
    val equipLevel = integer("equip_level").default(1)

    override val primaryKey = PrimaryKey(ownerId, shipId, pos)
}

object OwnedShipStrengths : Table("owned_ship_strengths") {
    val ownerId = integer("owner_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipId = integer("ship_id").references(OwnedShips.id, onDelete = ReferenceOption.CASCADE)
    val strengthId = integer("strength_id")
    val exp = long("exp").default(0L)

    override val primaryKey = PrimaryKey(ownerId, shipId, strengthId)
}

object OwnedShipTransforms : Table("owned_ship_transforms") {
    val ownerId = integer("owner_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipId = integer("ship_id").references(OwnedShips.id, onDelete = ReferenceOption.CASCADE)
    val transformId = integer("transform_id")
    val level = integer("level").default(0)

    override val primaryKey = PrimaryKey(ownerId, shipId, transformId)
}

object OwnedShipShadowSkins : Table("owned_ship_shadow_skins") {
    val ownerId = integer("owner_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipId = integer("ship_id").references(OwnedShips.id, onDelete = ReferenceOption.CASCADE)
    val skinId = integer("skin_id")

    override val primaryKey = PrimaryKey(ownerId, shipId, skinId)
}

object EquipmentTemplates : Table("equipment_templates") {
    val id = integer("id")
    val base = integer("base").nullable()
    val destroyGold = integer("destroy_gold").default(0)
    val destroyItem = text("destroy_item").nullable()
    val equipLimit = integer("equip_limit").default(0)
    val groupId = integer("group_id").default(0)
    val important = integer("important").default(0)
    val level = integer("level").default(0)
    val nextId = integer("next_id").default(0)
    val prevId = integer("prev_id").default(0)
    val restoreGold = integer("restore_gold").default(0)
    val restoreItem = text("restore_item").nullable()
    val shipTypeForbidden = text("ship_type_forbidden").nullable()
    val transUseGold = integer("trans_use_gold").default(0)
    val transUseItem = text("trans_use_item").nullable()
    val type = integer("type").default(0)
    val upgradeFormulaId = text("upgrade_formula_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object OwnedEquipments : Table("owned_equipments") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val equipmentId = integer("equipment_id")
    val count = integer("count").default(0)
    val isLocked = integer("is_locked").default(0)

    override val primaryKey = PrimaryKey(commanderId, equipmentId)
}

object OwnedSpweapons : Table("owned_spweapons") {
    val id = integer("id").autoIncrement()
    val ownerId = integer("owner_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val templateId = integer("template_id")
    val attr1 = integer("attr_1").default(0)
    val attr2 = integer("attr_2").default(0)
    val attrTemp1 = integer("attr_temp_1").default(0)
    val attrTemp2 = integer("attr_temp_2").default(0)
    val effect = integer("effect").default(0)
    val pt = integer("pt").default(0)
    val equippedShipId = integer("equipped_ship_id").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, ownerId)
    }
}

object OwnedSkins : Table("owned_skins") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val skinId = integer("skin_id")
    val expiresAt = long("expires_at").nullable()

    override val primaryKey = PrimaryKey(commanderId, skinId)
}
