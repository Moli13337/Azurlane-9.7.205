package com.azurlane.server.handler.tb

import com.azurlane.infra.database.repository.ResourceRepository
import com.azurlane.infra.database.repository.TbRepository
import com.azurlane.infra.database.repository.TbRow
import com.azurlane.infra.network.ClientConnection
import com.azurlane.infra.network.PacketHandler
import com.azurlane.proto.Common
import com.azurlane.proto.Tb
import com.google.protobuf.Message
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val GOLD_RESOURCE_ID = 1
private const val TB_SHOP_GOLD_COST = 50L
private const val TB_WORK_GOLD_REWARD = 30L
private const val TB_SETTLE_GOLD_REWARD = 100L
private const val TB_SETTLE_FAVOR_REWARD = 1

class GetTbInfoHandler : PacketHandler {
    override val cmdId = 29001

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29002.newBuilder().setResult(1).build()

        val request = Tb.CS_29001.parseFrom(payload)
        TbRepository.ensureExists(commanderId)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        val permanent = TbRepository.findPermanentByCommanderId(commanderId)

        val tbInfo = if (tb != null) buildTbInfo(tb) else buildDefaultTbInfo()
        val tbPermanent = if (permanent != null) buildTbPermanent(permanent) else buildDefaultTbPermanent()

        logger.info { "get tb info: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29002.newBuilder()
            .setResult(0)
            .setTb(tbInfo)
            .setPermanent(tbPermanent)
            .build()
    }
}

class GetTbEndingsHandler : PacketHandler {
    override val cmdId = 29003

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29004.newBuilder().setResult(1).build()

        val request = Tb.CS_29003.parseFrom(payload)
        val permanent = TbRepository.findPermanentByCommanderId(commanderId)

        logger.info { "get tb endings: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29004.newBuilder()
            .setResult(0)
            .addAllEndings(permanent?.getEndings() ?: emptyList())
            .build()
    }
}

class ConfirmTbEndingHandler : PacketHandler {
    override val cmdId = 29005

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29006.newBuilder().setResult(1).build()

        val request = Tb.CS_29005.parseFrom(payload)
        val endingId = request.endingId.toInt()

        val permanent = TbRepository.findPermanentByCommanderId(commanderId)
        if (permanent != null) {
            val endings = permanent.getEndings().toMutableList()
            if (!endings.contains(endingId)) endings.add(endingId)
            val activeEndings = permanent.getActiveEndings().toMutableList()
            if (!activeEndings.contains(endingId)) activeEndings.add(endingId)
            TbRepository.updatePermanentExtraData(commanderId, buildPermanentExtraJson(permanent, endings, activeEndings))
        }

        logger.info { "confirm tb ending: commander=$commanderId id=${request.id.toInt()} endingId=$endingId" }

        return Tb.SC_29006.newBuilder().setResult(0).build()
    }
}

class SetTbDifficultyHandler : PacketHandler {
    override val cmdId = 29007

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29008.newBuilder().setResult(1).build()

        val request = Tb.CS_29007.parseFrom(payload)
        TbRepository.updateDifficulty(commanderId, request.difficulty.toInt())

        val tb = TbRepository.findTbByCommanderId(commanderId)
        val tbInfo = if (tb != null) buildTbInfo(tb) else buildDefaultTbInfo()

        logger.info { "set tb difficulty: commander=$commanderId id=${request.id.toInt()} difficulty=${request.difficulty.toInt()}" }

        return Tb.SC_29008.newBuilder()
            .setResult(0)
            .setTb(tbInfo)
            .build()
    }
}

class RenameTbHandler : PacketHandler {
    override val cmdId = 29009

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29010.newBuilder().setResult(1).build()

        val request = Tb.CS_29009.parseFrom(payload)
        val name = request.name.trim()
        if (name.isEmpty() || name.length > 12) {
            return Tb.SC_29010.newBuilder().setResult(2).build()
        }
        TbRepository.updateName(commanderId, name)

        logger.info { "rename tb: commander=$commanderId id=${request.id.toInt()} name=$name" }

        return Tb.SC_29010.newBuilder().setResult(0).build()
    }
}

class StartTbGameHandler : PacketHandler {
    override val cmdId = 29011

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29012.newBuilder().setResult(1).build()

        val request = Tb.CS_29011.parseFrom(payload)
        TbRepository.ensureExists(commanderId)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null && tb.roundNum == 0) {
            TbRepository.updateRound(commanderId, 1, tb.inTemp, tb.tempRound)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb,
                fsmSystemNo = 1,
                fsmCurrentNode = 1
            ))
        }

        logger.info { "start tb game: commander=$commanderId" }

        return Tb.SC_29012.newBuilder()
            .setResult(0)
            .setFirstNode(1)
            .build()
    }
}

class SelectTbRankHandler : PacketHandler {
    override val cmdId = 29013

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29014.newBuilder().setResult(1).build()

        val request = Tb.CS_29013.parseFrom(payload)
        val rank = request.rank.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 1))
        }

        logger.info { "select tb rank: commander=$commanderId id=${request.id.toInt()} rank=$rank" }

        return Tb.SC_29014.newBuilder()
            .setResult(0)
            .setFirstNode(1)
            .build()
    }
}

class GetTbChatsHandler : PacketHandler {
    override val cmdId = 29015

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29016.newBuilder().setResult(1).build()

        val request = Tb.CS_29015.parseFrom(payload)
        val tb = TbRepository.findTbByCommanderId(commanderId)
        val chats = tb?.getChats() ?: emptyList()

        logger.info { "get tb chats: commander=$commanderId id=${request.id.toInt()} chatCount=${chats.size}" }

        return Tb.SC_29016.newBuilder()
            .setResult(0)
            .addAllChats(chats)
            .build()
    }
}

class SelectTbChatHandler : PacketHandler {
    override val cmdId = 29017

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29018.newBuilder().setResult(1).build()

        val request = Tb.CS_29017.parseFrom(payload)
        val chatId = request.chatId.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val chats = tb.getChats().toMutableList()
            if (!chats.contains(chatId)) chats.add(chatId)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, chats = chats, fsmCurrentNode = 1))
        }

        logger.info { "select tb chat: commander=$commanderId id=${request.id.toInt()} chatId=$chatId" }

        return Tb.SC_29018.newBuilder()
            .setResult(0)
            .setFirstNode(1)
            .build()
    }
}

class GetTbTalentsHandler : PacketHandler {
    override val cmdId = 29019

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29020.newBuilder().setResult(1).build()

        val request = Tb.CS_29019.parseFrom(payload)
        val tb = TbRepository.findTbByCommanderId(commanderId)

        logger.info { "get tb talents: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29020.newBuilder()
            .setResult(0)
            .addAllTalents(tb?.getTalents() ?: emptyList())
            .build()
    }
}

class SelectTbTalentHandler : PacketHandler {
    override val cmdId = 29021

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29022.newBuilder().setResult(1).build()

        val request = Tb.CS_29021.parseFrom(payload)
        val talent = request.talent.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val talents = tb.getTalents().toMutableList()
            if (!talents.contains(talent)) talents.add(talent)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, talents = talents))
        }

        logger.info { "select tb talent: commander=$commanderId id=${request.id.toInt()} talent=$talent" }

        return Tb.SC_29022.newBuilder()
            .setResult(0)
            .setTalent(request.talent)
            .build()
    }
}

class ResetTbTalentHandler : PacketHandler {
    override val cmdId = 29023

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29024.newBuilder().setResult(1).build()

        val request = Tb.CS_29023.parseFrom(payload)
        val talent = request.talent.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val talents = tb.getTalents().toMutableList()
            talents.remove(talent)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, talents = talents))
        }

        logger.info { "reset tb talent: commander=$commanderId id=${request.id.toInt()} talent=$talent" }

        return Tb.SC_29024.newBuilder().setResult(0).build()
    }
}

class ExecuteTbPlanHandler : PacketHandler {
    override val cmdId = 29025

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29026.newBuilder().setResult(1).build()

        val request = Tb.CS_29025.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 1))
        }

        logger.info { "execute tb plan: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29026.newBuilder()
            .setResult(0)
            .setFirstNode(1)
            .build()
    }
}

class SettleTbHandler : PacketHandler {
    override val cmdId = 29027

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29028.newBuilder().setResult(1).build()

        val request = Tb.CS_29027.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 0))

            ResourceRepository.addResource(commanderId, GOLD_RESOURCE_ID, TB_SETTLE_GOLD_REWARD)

            val favorGain = TB_SETTLE_FAVOR_REWARD
            TbRepository.updateFavorLv(commanderId, tb.favorLv + favorGain)
        }

        val drops = Tb.TBDROPS.newBuilder()
            .addBaseDrop(Tb.TBDROP.newBuilder().setType(2).setId(1).setNumber(TB_SETTLE_GOLD_REWARD.toInt()).build())
            .build()

        logger.info { "settle tb: commander=$commanderId" }

        return Tb.SC_29028.newBuilder()
            .setResult(0)
            .setDrop(drops)
            .build()
    }
}

class SelectTbBranchHandler : PacketHandler {
    override val cmdId = 29030

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29031.newBuilder().setResult(1).build()

        val request = Tb.CS_29030.parseFrom(payload)
        val branch = request.branch.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 1))
        }

        logger.info { "select tb branch: commander=$commanderId id=${request.id.toInt()} branch=$branch" }

        return Tb.SC_29031.newBuilder()
            .setResult(0)
            .setNextNode(1)
            .build()
    }
}

class GetTbFsmHandler : PacketHandler {
    override val cmdId = 29032

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29033.newBuilder().setResult(1).build()

        val request = Tb.CS_29032.parseFrom(payload)
        val tb = TbRepository.findTbByCommanderId(commanderId)
        val fsm = if (tb != null) buildTbFsm(tb) else Tb.TBFSM.newBuilder().build()

        logger.info { "get tb fsm: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29033.newBuilder()
            .setResult(0)
            .setFsm(fsm)
            .build()
    }
}

class SetTbPlansHandler : PacketHandler {
    override val cmdId = 29040

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29041.newBuilder().setResult(1).build()

        val request = Tb.CS_29040.parseFrom(payload)
        val plans = request.plansList

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val planUpgrade = plans.map { it.value.toInt() }
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, planUpgrade = planUpgrade))
        }

        logger.info { "set tb plans: commander=$commanderId id=${request.id.toInt()} planCount=${plans.size}" }

        return Tb.SC_29041.newBuilder()
            .setResult(0)
            .addAllPlans(plans)
            .build()
    }
}

class NextTbRoundHandler : PacketHandler {
    override val cmdId = 29042

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29043.newBuilder().setResult(1).build()

        val request = Tb.CS_29042.parseFrom(payload)
        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val newRound = tb.roundNum + 1
            TbRepository.updateRound(commanderId, newRound, tb.inTemp, tb.tempRound)
        }

        logger.info { "next tb round: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29043.newBuilder()
            .setResult(0)
            .setFirstNode(0)
            .build()
    }
}

class BatchTbPlanHandler : PacketHandler {
    override val cmdId = 29044

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29045.newBuilder().setResult(1).build()

        val request = Tb.CS_29044.parseFrom(payload)
        val planIds = request.planIdsList.map { it.toInt() }

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val planUpgrade = tb.getPlanUpgrade().toMutableList()
            planIds.forEach { if (!planUpgrade.contains(it)) planUpgrade.add(it) }
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, planUpgrade = planUpgrade))
        }

        logger.info { "batch tb plan: commander=$commanderId id=${request.id.toInt()} planCount=${planIds.size}" }

        return Tb.SC_29045.newBuilder().setResult(0).build()
    }
}

class RestTbHandler : PacketHandler {
    override val cmdId = 29046

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29047.newBuilder().setResult(1).build()

        val request = Tb.CS_29046.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 0))
        }

        logger.info { "rest tb: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29047.newBuilder().setResult(0).build()
    }
}

class GoHomeTbHandler : PacketHandler {
    override val cmdId = 29048

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29049.newBuilder().setResult(1).build()

        val request = Tb.CS_29048.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 0))
        }

        val res = Tb.TBRES.newBuilder()
            .addAttrs(Common.KVDATA.newBuilder().setKey(1).setValue(tb?.favorLv ?: 0).build())
            .addResource(Common.KVDATA.newBuilder().setKey(1).setValue((ResourceRepository.getAmount(commanderId, GOLD_RESOURCE_ID)).toInt()).build())
            .build()

        val drops = Tb.TBDROPS.newBuilder().build()

        logger.info { "go home tb: commander=$commanderId" }

        return Tb.SC_29049.newBuilder()
            .setResult(0)
            .setDrop(drops)
            .setRes(res)
            .build()
    }
}

class NextWeekTbHandler : PacketHandler {
    override val cmdId = 29050

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29051.newBuilder().setResult(1).build()

        val request = Tb.CS_29050.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val newRound = tb.roundNum + 1
            TbRepository.updateRound(commanderId, newRound, tb.inTemp, tb.tempRound)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb,
                fsmCurrentNode = 0,
                chats = emptyList(),
                shops = emptyList(),
                siteCharacters = emptyList(),
                siteEvents = emptyList(),
                siteWorks = emptyList()
            ))
            if (newRound > tb.maxRound) {
                val permanent = TbRepository.findPermanentByCommanderId(commanderId)
                if (permanent != null) {
                    TbRepository.updatePermanentMaxRound(commanderId, newRound)
                }
            }
        }

        logger.info { "next week tb: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29051.newBuilder().setResult(0).build()
    }
}

class EnterTbSiteHandler : PacketHandler {
    override val cmdId = 29060

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29061.newBuilder().setResult(1).build()

        val request = Tb.CS_29060.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 1))
        }

        val characters = tb?.getSiteCharacters() ?: emptyList()
        val fsmSite = Tb.TBFSMCACHESITE.newBuilder()
            .addAllEvents(tb?.getSiteEvents() ?: emptyList())
            .addAllShops(tb?.getShops() ?: emptyList())
            .addAllCharacterThisRound(characters)
            .build()

        logger.info { "enter tb site: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29061.newBuilder()
            .setResult(0)
            .setFsmSite(fsmSite)
            .addAllCharacters(characters)
            .build()
    }
}

class StartTbWorkHandler : PacketHandler {
    override val cmdId = 29062

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29063.newBuilder().setResult(1).build()

        val request = Tb.CS_29062.parseFrom(payload)
        val workId = request.workId.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val works = tb.getSiteWorks().toMutableList()
            if (!works.contains(workId)) works.add(workId)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, siteWorks = works, fsmCurrentNode = 1))
        }

        logger.info { "start tb work: commander=$commanderId id=${request.id.toInt()} workId=$workId" }

        return Tb.SC_29063.newBuilder()
            .setResult(0)
            .setFirstNode(1)
            .build()
    }
}

class TriggerTbEventHandler : PacketHandler {
    override val cmdId = 29064

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29065.newBuilder().setResult(1).build()

        val request = Tb.CS_29064.parseFrom(payload)
        val event = request.event.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val events = tb.getSiteEvents().toMutableList()
            if (!events.contains(event)) events.add(event)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, siteEvents = events, fsmCurrentNode = 1))
        }

        logger.info { "trigger tb event: commander=$commanderId id=${request.id.toInt()} event=$event" }

        return Tb.SC_29065.newBuilder()
            .setResult(0)
            .setFirstNode(1)
            .build()
    }
}

class BuyTbShopHandler : PacketHandler {
    override val cmdId = 29066

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29067.newBuilder().setResult(1).build()

        val request = Tb.CS_29066.parseFrom(payload)
        val shop = request.shop.toInt()
        val num = request.num.toInt().coerceAtLeast(1)

        val goldCost = TB_SHOP_GOLD_COST * num.toLong()
        val currentGold = ResourceRepository.getAmount(commanderId, GOLD_RESOURCE_ID)
        if (currentGold < goldCost) {
            return Tb.SC_29067.newBuilder().setResult(2).build()
        }
        ResourceRepository.addResource(commanderId, GOLD_RESOURCE_ID, -goldCost)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val shops = tb.getShops().toMutableList()
            if (!shops.contains(shop)) shops.add(shop)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, shops = shops))
        }

        val drops = Tb.TBDROPS.newBuilder()
            .addBaseDrop(Tb.TBDROP.newBuilder().setType(2).setId(shop).setNumber(num).build())
            .build()

        logger.info { "buy tb shop: commander=$commanderId shop=$shop num=$num gold=$goldCost" }

        return Tb.SC_29067.newBuilder()
            .setResult(0)
            .setDrop(drops)
            .build()
    }
}

class MeetTbCharacterHandler : PacketHandler {
    override val cmdId = 29068

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29069.newBuilder().setResult(1).build()

        val request = Tb.CS_29068.parseFrom(payload)
        val character = request.character.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val characters = tb.getSiteCharacters().toMutableList()
            if (!characters.contains(character)) characters.add(character)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, siteCharacters = characters, fsmCurrentNode = 1))
        }

        logger.info { "meet tb character: commander=$commanderId id=${request.id.toInt()} character=$character" }

        return Tb.SC_29069.newBuilder()
            .setResult(0)
            .setFirstNode(1)
            .build()
    }
}

class FinishTbWorkHandler : PacketHandler {
    override val cmdId = 29070

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29071.newBuilder().setResult(1).build()

        val request = Tb.CS_29070.parseFrom(payload)
        val workId = request.workId.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val works = tb.getSiteWorks().toMutableList()
            works.remove(workId)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, siteWorks = works, fsmCurrentNode = 0))

            ResourceRepository.addResource(commanderId, GOLD_RESOURCE_ID, TB_WORK_GOLD_REWARD)
        }

        logger.info { "finish tb work: commander=$commanderId workId=$workId" }

        return Tb.SC_29071.newBuilder().setResult(0).build()
    }
}

class GetTbShopHandler : PacketHandler {
    override val cmdId = 29072

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29073.newBuilder().setResult(1).build()

        val request = Tb.CS_29072.parseFrom(payload)
        val tb = TbRepository.findTbByCommanderId(commanderId)
        val shops = tb?.getShops() ?: emptyList()

        logger.info { "get tb shop: commander=$commanderId id=${request.id.toInt()} shopCount=${shops.size}" }

        return Tb.SC_29073.newBuilder()
            .setResult(0)
            .addAllShops(shops)
            .build()
    }
}

class RefreshTbSiteHandler : PacketHandler {
    override val cmdId = 29090

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29091.newBuilder().setResult(1).build()

        val request = Tb.CS_29090.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 1))
        }

        logger.info { "refresh tb site: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29091.newBuilder()
            .setResult(0)
            .setFirstNode(1)
            .build()
    }
}

class RestartTbHandler : PacketHandler {
    override val cmdId = 29092

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29093.newBuilder().setResult(1).build()

        val request = Tb.CS_29092.parseFrom(payload)
        TbRepository.updateDifficulty(commanderId, request.difficulty.toInt())
        TbRepository.resetTb(commanderId)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        val tbInfo = if (tb != null) buildTbInfo(tb) else buildDefaultTbInfo()

        logger.info { "restart tb: commander=$commanderId id=${request.id.toInt()} difficulty=${request.difficulty.toInt()}" }

        return Tb.SC_29093.newBuilder()
            .setResult(0)
            .setTb(tbInfo)
            .build()
    }
}

class Nin1SelectHandler : PacketHandler {
    override val cmdId = 29101

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29102.newBuilder().setResult(1).build()

        val request = Tb.CS_29101.parseFrom(payload)
        val selectId = request.id.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val selects = tb.getNin1Selects().toMutableList()
            if (!selects.contains(selectId)) selects.add(selectId)
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb,
                nin1Selects = selects,
                fsmCurrentNode = 1
            ))
        }

        val drops = Tb.TBDROPS.newBuilder()
            .addBaseDrop(Tb.TBDROP.newBuilder().setType(1).setId(selectId).setNumber(1).build())
            .build()

        logger.info { "nin1 select: commander=$commanderId selectId=$selectId" }

        return Tb.SC_29102.newBuilder()
            .setResult(0)
            .setDrop(drops)
            .build()
    }
}

class Nin1ReselectHandler : PacketHandler {
    override val cmdId = 29103

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29104.newBuilder().setResult(1).build()

        val request = Tb.CS_29103.parseFrom(payload)
        val index = request.index.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            val rerollCount = tb.getNin1RerollCount() + 1
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, nin1RerollCount = rerollCount))
        }

        logger.info { "nin1 reselect: commander=$commanderId id=${request.id.toInt()} index=$index" }

        return Tb.SC_29104.newBuilder().setResult(0).build()
    }
}

class Nin1ConfirmHandler : PacketHandler {
    override val cmdId = 29105

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29106.newBuilder().setResult(1).build()

        val request = Tb.CS_29105.parseFrom(payload)
        val index = request.index.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        val cache = if (tb != null) {
            Tb.TBFSMCACHENIN1.newBuilder()
                .addAllSelects(tb.getNin1Selects().map { selectId ->
                    Tb.TBDROP.newBuilder().setType(1).setId(selectId).setNumber(1).build()
                })
                .addAllRerollCount(listOf(tb.getNin1RerollCount()))
                .build()
        } else {
            Tb.TBFSMCACHENIN1.newBuilder().build()
        }

        logger.info { "nin1 confirm: commander=$commanderId id=${request.id.toInt()} index=$index" }

        return Tb.SC_29106.newBuilder()
            .setResult(0)
            .setCache(cache)
            .build()
    }
}

class Nin1SkipHandler : PacketHandler {
    override val cmdId = 29107

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29108.newBuilder().setResult(1).build()

        val request = Tb.CS_29107.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        val cache = if (tb != null) {
            Tb.TBFSMCACHENIN1.newBuilder()
                .addAllSelects(tb.getNin1Selects().map { selectId ->
                    Tb.TBDROP.newBuilder().setType(1).setId(selectId).setNumber(1).build()
                })
                .addAllRerollCount(listOf(tb.getNin1RerollCount()))
                .build()
        } else {
            Tb.TBFSMCACHENIN1.newBuilder().build()
        }

        logger.info { "nin1 skip: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29108.newBuilder()
            .setResult(0)
            .setCache(cache)
            .build()
    }
}

class TarotSelectHandler : PacketHandler {
    override val cmdId = 29120

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29121.newBuilder().setResult(1).build()

        val request = Tb.CS_29120.parseFrom(payload)
        val tarotId = request.tarotId.toInt()

        val permanent = TbRepository.findPermanentByCommanderId(commanderId)
        if (permanent != null) {
            val tarotArchive = permanent.getTarotArchive().toMutableList()
            if (!tarotArchive.contains(tarotId)) tarotArchive.add(tarotId)
            TbRepository.updatePermanentExtraData(commanderId, buildPermanentExtraJson(permanent, tarotArchive = tarotArchive))
        }

        logger.info { "tarot select: commander=$commanderId id=${request.id.toInt()} tarotId=$tarotId" }

        return Tb.SC_29121.newBuilder().setResult(0).build()
    }
}

class AffixUpHandler : PacketHandler {
    override val cmdId = 29122

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29123.newBuilder().setResult(1).build()

        val request = Tb.CS_29122.parseFrom(payload)
        val affixid = request.affixid.toInt()

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateExtraData(commanderId, buildTbExtraJson(tb, fsmCurrentNode = 1))
        }

        logger.info { "affix up: commander=$commanderId id=${request.id.toInt()} affixid=$affixid" }

        return Tb.SC_29123.newBuilder().setResult(0).build()
    }
}

class EvalTbHandler : PacketHandler {
    override val cmdId = 29124

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29125.newBuilder().setResult(1).build()

        val request = Tb.CS_29124.parseFrom(payload)

        val tb = TbRepository.findTbByCommanderId(commanderId)
        if (tb != null) {
            TbRepository.updateEvalFail(commanderId, tb.evalFail + 1)
        }

        logger.info { "eval tb: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29125.newBuilder().setResult(0).build()
    }
}

class GetTbFsmLoginHandler : PacketHandler {
    override val cmdId = 29126

    override suspend fun handle(payload: ByteArray, client: ClientConnection): Message {
        val commanderId = client.commanderId
            ?: return Tb.SC_29127.newBuilder().setResult(1).build()

        val request = Tb.CS_29126.parseFrom(payload)
        val tb = TbRepository.findTbByCommanderId(commanderId)
        val fsm = if (tb != null) buildTbFsm(tb) else Tb.TBFSM.newBuilder().build()

        logger.info { "get tb fsm login: commander=$commanderId id=${request.id.toInt()}" }

        return Tb.SC_29127.newBuilder()
            .setResult(0)
            .setFsm(fsm)
            .build()
    }
}

private fun buildTbInfo(tb: TbRow): Tb.TBINFO {
    val builder = Tb.TBINFO.newBuilder()
        .setId(tb.tbId)
        .setName(tb.name)
        .setDifficulty(tb.difficulty)
        .setFavorLv(tb.favorLv)
        .setEvalFail(tb.evalFail)
        .setRound(Tb.TBROUND.newBuilder().setRound(tb.roundNum).setInTemp(tb.inTemp).setTempRound(tb.tempRound).build())
        .setRes(Tb.TBRES.newBuilder().build())
        .setTalent(Tb.TBTALENT.newBuilder().addAllTalents(tb.getTalents()).build())
        .setPlan(Tb.TBPLAN.newBuilder().addAllPlanUpgrade(tb.getPlanUpgrade()).build())
        .setSite(Tb.TBSITE.newBuilder()
            .addAllCharacters(tb.getSiteCharacters())
            .addAllWorks(tb.getSiteWorks())
            .build())
        .setBenefit(Tb.TBBENEFIT.newBuilder().build())
        .setFsm(buildTbFsm(tb))
        .setDisplay(Tb.TBDISPLAY.newBuilder().build())

    tb.getEvaluations().forEach { (key, value) ->
        builder.addEvaluations(Common.KVDATA.newBuilder().setKey(key).setValue(value).build())
    }

    return builder.build()
}

private fun buildTbFsm(tb: TbRow): Tb.TBFSM {
    val cachePlan = Tb.TBFSMCACHEPLAN.newBuilder()
        .setCurIndex(0)
        .addAllPlans(tb.getPlanUpgrade().mapIndexed { index, planId ->
            Common.KVDATA.newBuilder().setKey(index).setValue(planId).build()
        })
        .build()

    val cacheTalent = Tb.TBFSMCACHETALENT.newBuilder()
        .setFinished(if (tb.getTalents().isEmpty()) 0 else 1)
        .addAllTalents(tb.getTalents())
        .build()

    val cacheSite = Tb.TBFSMCACHESITE.newBuilder()
        .addAllEvents(tb.getSiteEvents())
        .addAllShops(tb.getShops())
        .addAllCharacterThisRound(tb.getSiteCharacters())
        .build()

    val cacheChat = Tb.TBFSMCACHECHAT.newBuilder()
        .setFinished(if (tb.getChats().isEmpty()) 0 else 1)
        .addAllChats(tb.getChats())
        .build()

    val cacheEnd = Tb.TBFSMCACHEEND.newBuilder()
        .addAllEnds(tb.getActiveEndings())
        .build()

    val cacheNin1 = Tb.TBFSMCACHENIN1.newBuilder()
        .addAllSelects(tb.getNin1Selects().map { selectId ->
            Tb.TBDROP.newBuilder().setType(1).setId(selectId).setNumber(1).build()
        })
        .addAllRerollCount(listOf(tb.getNin1RerollCount()))
        .build()

    val cacheEval = Tb.TBFSMCACHEEVAL.newBuilder()
        .setIsFinished(if (tb.evalFail > 0) 1 else 0)
        .build()

    val cache = Tb.TBFSMCACHE.newBuilder()
        .addCachePlan(cachePlan)
        .addCacheTalent(cacheTalent)
        .addCacheSite(cacheSite)
        .addCacheChat(cacheChat)
        .addCacheEnd(cacheEnd)
        .addCacheNin1(cacheNin1)
        .addCacheEval(cacheEval)
        .build()

    return Tb.TBFSM.newBuilder()
        .setSystemNo(tb.getFsmSystemNo())
        .setCurrentNode(tb.getFsmCurrentNode())
        .addCache(cache)
        .build()
}

private fun buildDefaultTbInfo(): Tb.TBINFO {
    return Tb.TBINFO.newBuilder()
        .setId(0)
        .setName("")
        .setDifficulty(0)
        .setFavorLv(0)
        .setEvalFail(0)
        .setRound(Tb.TBROUND.newBuilder().setRound(0).setInTemp(0).setTempRound(0).build())
        .setRes(Tb.TBRES.newBuilder().build())
        .setTalent(Tb.TBTALENT.newBuilder().build())
        .setPlan(Tb.TBPLAN.newBuilder().build())
        .setSite(Tb.TBSITE.newBuilder().build())
        .setBenefit(Tb.TBBENEFIT.newBuilder().build())
        .setFsm(Tb.TBFSM.newBuilder().build())
        .setDisplay(Tb.TBDISPLAY.newBuilder().build())
        .build()
}

private fun buildTbPermanent(permanent: com.azurlane.infra.database.repository.TbPermanentRow): Tb.TBPERMANENT {
    return Tb.TBPERMANENT.newBuilder()
        .setNgPlusCount(permanent.ngPlusCount)
        .setMaxRound(permanent.maxRound)
        .addAllPolaroids(permanent.getPolaroids())
        .addAllEndings(permanent.getEndings())
        .addAllActiveEndings(permanent.getActiveEndings())
        .addAllTarotArchive(permanent.getTarotArchive())
        .build()
}

private fun buildDefaultTbPermanent(): Tb.TBPERMANENT {
    return Tb.TBPERMANENT.newBuilder()
        .setNgPlusCount(0)
        .setMaxRound(0)
        .build()
}

private fun buildTbExtraJson(
    tb: TbRow,
    talents: List<Int> = tb.getTalents(),
    planUpgrade: List<Int> = tb.getPlanUpgrade(),
    fsmSystemNo: Int = tb.getFsmSystemNo(),
    fsmCurrentNode: Int = tb.getFsmCurrentNode(),
    chats: List<Int> = tb.getChats(),
    shops: List<Int> = tb.getShops(),
    siteCharacters: List<Int> = tb.getSiteCharacters(),
    siteEvents: List<Int> = tb.getSiteEvents(),
    siteWorks: List<Int> = tb.getSiteWorks(),
    nin1Selects: List<Int> = tb.getNin1Selects(),
    nin1RerollCount: Int = tb.getNin1RerollCount()
): String {
    return buildJsonObject {
        putJsonArray("talents") { talents.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("plan_upgrade") { planUpgrade.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("evaluations") {
            tb.getEvaluations().forEach { (k, v) ->
                add(buildJsonObject { put("key", k); put("value", v) })
            }
        }
        putJsonArray("polaroids") { tb.getPolaroids().forEach { add(JsonPrimitive(it)) } }
        putJsonArray("endings") { tb.getEndings().forEach { add(JsonPrimitive(it)) } }
        putJsonArray("active_endings") { tb.getActiveEndings().forEach { add(JsonPrimitive(it)) } }
        putJsonArray("tarot_archive") { tb.getTarotArchive().forEach { add(JsonPrimitive(it)) } }
        put("fsm_system_no", fsmSystemNo)
        put("fsm_current_node", fsmCurrentNode)
        putJsonArray("chats") { chats.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("shops") { shops.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("site_characters") { siteCharacters.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("site_events") { siteEvents.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("site_works") { siteWorks.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("nin1_selects") { nin1Selects.forEach { add(JsonPrimitive(it)) } }
        put("nin1_reroll_count", nin1RerollCount)
    }.toString()
}

private fun buildPermanentExtraJson(
    permanent: com.azurlane.infra.database.repository.TbPermanentRow,
    endings: List<Int> = permanent.getEndings(),
    activeEndings: List<Int> = permanent.getActiveEndings(),
    tarotArchive: List<Int> = permanent.getTarotArchive()
): String {
    return buildJsonObject {
        putJsonArray("polaroids") { permanent.getPolaroids().forEach { add(JsonPrimitive(it)) } }
        putJsonArray("endings") { endings.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("active_endings") { activeEndings.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("tarot_archive") { tarotArchive.forEach { add(JsonPrimitive(it)) } }
    }.toString()
}
