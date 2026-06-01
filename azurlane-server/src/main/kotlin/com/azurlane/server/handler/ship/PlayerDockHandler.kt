package com.azurlane.server.handler.ship

import com.azurlane.data.config.ConfigRegistry
import com.azurlane.data.loader.model.ShipDataTemplateEntry
import com.azurlane.infra.database.repository.ShadowSkinRow
import com.azurlane.infra.database.repository.ShipEquipmentRepository
import com.azurlane.infra.database.repository.ShipOpsRepository
import com.azurlane.infra.database.repository.ShipRepository
import com.azurlane.infra.database.repository.ShipStrengthRepository
import com.azurlane.infra.database.repository.ShipTransformRepository
import com.azurlane.infra.database.repository.SkinRepository
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Ship
import com.google.protobuf.Message

class PlayerDockHandler : PacketHandler {
    override val cmdId = 12001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message? {
        val commanderId = client.commanderId ?: return null
        val ships = ShipRepository.findByOwnerId(commanderId)
        val allShadowSkins = SkinRepository.findShadowSkinsByCommanderId(commanderId)
        val shadowSkinMap = allShadowSkins.groupBy { it.shipId }

        val builder = Ship.SC_12001.newBuilder()
        for (ship in ships) {
            val shadows = shadowSkinMap[ship.id] ?: emptyList()
            builder.addShiplist(buildShipInfo(ship, commanderId, emptyList(), shadows))
        }

        return builder.build()
    }

    companion object {
        fun buildShipInfo(
            ship: com.azurlane.infra.database.repository.OwnedShipRow,
            commanderId: Int = 0,
            randomFlags: List<Int> = emptyList(),
            shadowSkins: List<ShadowSkinRow> = emptyList()
        ): Common.SHIPINFO {
            val shipState = Common.SHIPSTATE.newBuilder()
                .setState(ship.state)
                .setStateInfo1(ship.stateInfo1)
                .setStateInfo2(ship.stateInfo2)
                .setStateInfo3(ship.stateInfo3)
                .setStateInfo4(ship.stateInfo4)
                .build()

            val equippedMap = ShipEquipmentRepository.findByShipId(ship.id)
                .associateBy { it.pos }

            val slotCount = getEquipSlotCount(ship.templateId)

            val equipInfoList = (1..slotCount).map { pos ->
                val eq = equippedMap[pos]
                Common.EQUIPSKIN_INFO.newBuilder()
                    .setId(eq?.equipId ?: 0)
                    .setSkinId(eq?.skinId ?: 0)
                    .build()
            }

            val strengthList = ShipStrengthRepository.findByShipId(ship.id).map { st ->
                Common.STRENGTH_INFO.newBuilder()
                    .setId(st.strengthId)
                    .setExp(st.exp.toInt())
                    .build()
            }

            val transformList = if (commanderId > 0) {
                ShipTransformRepository.findByShipId(commanderId, ship.id).map { tr ->
                    Common.TRANSFORM_INFO.newBuilder()
                        .setId(tr.transformId)
                        .setLevel(tr.level)
                        .build()
                }
            } else {
                emptyList()
            }

            val skinShadowList = shadowSkins.map { ss ->
                Common.KVDATA.newBuilder()
                    .setKey(ss.shipId)
                    .setValue(ss.skinId)
                    .build()
            }

            return Common.SHIPINFO.newBuilder()
                .setId(ship.id)
                .setTemplateId(ship.templateId)
                .setLevel(ship.level)
                .setExp(ship.exp.toInt())
                .addAllEquipInfoList(equipInfoList)
                .setEnergy(ship.energy)
                .setState(shipState)
                .setIsLocked(ship.isLocked)
                .addAllTransformList(transformList)
                .setIntimacy(ship.intimacy)
                .setProficiency(ship.proficiency)
                .addAllStrengthList(strengthList)
                .setCreateTime((ship.createTime / 1000).toInt())
                .setSkinId(ship.skinId)
                .setPropose(ship.propose)
                .setName(ship.customName)
                .setChangeNameTimestamp((ship.changeNameTimestamp / 1000).toInt())
                .setMaxLevel(ship.maxLevel)
                .setCommonFlag(ship.commonFlag)
                .setActivityNpc(ship.activityNpc)
                .addAllMetaRepairList(emptyList())
                .addAllSkinShadowList(skinShadowList)
                .addAllCharRandomFlag(randomFlags)
                .build()
        }

        private fun getEquipSlotCount(templateId: Int): Int {
            val templateData = ConfigRegistry.get<Map<String, ShipDataTemplateEntry>>("ship_data_template")
            return templateData?.get(templateId.toString())?.slotCount() ?: 3
        }
    }
}
