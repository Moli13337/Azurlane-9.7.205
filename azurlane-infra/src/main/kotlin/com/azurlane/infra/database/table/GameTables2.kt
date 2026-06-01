package com.azurlane.infra.database.table

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Fleets : Table("fleets") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val gameId = integer("game_id")
    val name = text("name").default("")
    val shipList = text("ship_list").default("[]")
    val fleetType = integer("fleet_type").default(0)
    val meowfficerList = text("meowfficer_list").default("[]")
    val commanderIds = text("commander_ids").default("[]")

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object ChallengeData : Table("challenge_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val activityId = integer("activity_id")
    val seasonMaxScore = integer("season_max_score").default(0)
    val activityMaxScore = integer("activity_max_score").default(0)
    val seasonMaxLevel = integer("season_max_level").default(0)
    val activityMaxLevel = integer("activity_max_level").default(0)
    val seasonId = integer("season_id").default(0)
    val currentScore = integer("current_score").default(0)
    val currentLevel = integer("current_level").default(0)
    val mode = integer("mode").default(0)
    val issl = integer("issl").default(0)
    val dungeonIdList = text("dungeon_id_list").default("[]")
    val buffList = text("buff_list").default("[]")
    val inChallenge = integer("in_challenge").default(0)

    override val primaryKey = PrimaryKey(commanderId, activityId)
    init { index(false, commanderId) }
}

object ChallengeGroups : Table("challenge_groups") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val activityId = integer("activity_id")
    val groupId = integer("group_id")
    val shipList = text("ship_list").default("[]")
    val commanders = text("commanders").default("[]")

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object ChallengeRewards : Table("challenge_rewards") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val challengeId = integer("challenge_id")
    val claimed = integer("claimed").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object GuildGameRooms : Table("guild_game_rooms") {
    val id = integer("id").autoIncrement()
    val type = integer("type").default(0)
    val gameType = integer("game_type").default(0)
    val name = text("name").default("")
    val playFlag = integer("play_flag").default(0)
    val createTime = long("create_time").default(0L)

    override val primaryKey = PrimaryKey(id)
}

object GuildGameRoomPlayers : Table("guild_game_room_players") {
    val id = integer("id").autoIncrement()
    val roomId = integer("room_id").references(GuildGameRooms.id, onDelete = ReferenceOption.CASCADE)
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val teamId = integer("team_id").default(0)
    val ready = integer("ready").default(0)
    val loadProgress = integer("load_progress").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, roomId); index(false, commanderId) }
}

object GuildGameScores : Table("guild_game_scores") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val gameType = integer("game_type").default(0)
    val score = integer("score").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object GuildGameUserViews : Table("guild_game_user_views") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val gameType = integer("game_type").default(0)
    val shipId = integer("ship_id").default(0)
    val skinId = integer("skin_id").default(0)
    val color = integer("color").default(0)
    val dressList = text("dress_list").default("[]")

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object Builds : Table("builds") {
    val id = integer("id").autoIncrement()
    val builderId = integer("builder_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val poolId = integer("pool_id")
    val finishesAt = long("finishes_at").default(0L)
    val isFinished = integer("is_finished").default(0)
    val pos = integer("pos").default(0)
    val shipId = integer("ship_id").default(0)
    val isConsumed = integer("is_consumed").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, builderId) }
}

object Mails : Table("mails") {
    val id = integer("id").autoIncrement()
    val receiverId = integer("receiver_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val senderId = integer("sender_id").default(0)
    val senderName = text("sender_name").default("")
    val title = text("title").default("")
    val body = text("body").default("")
    val attachFlag = integer("attach_flag").default(0)
    val customSender = text("custom_sender").nullable()
    val date = long("date").default(0L)
    val importantFlag = integer("important_flag").default(0)
    val readFlag = integer("read_flag").default(0)
    val isArchived = integer("is_archived").default(0)
    val createdAt = long("created_at").default(0L)

    override val primaryKey = PrimaryKey(id)
    init { index(false, receiverId) }
}

object MailAttachments : Table("mail_attachments") {
    val id = integer("id").autoIncrement()
    val mailId = integer("mail_id").references(Mails.id, onDelete = ReferenceOption.CASCADE)
    val type = integer("type").default(0)
    val itemId = integer("item_id").default(0)
    val quantity = integer("quantity").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, mailId) }
}

object ChapterProgress : Table("chapter_progress") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val chapterId = integer("chapter_id")
    val stars = integer("stars").default(0)
    val isCleared = integer("is_cleared").default(0)
    val attemptCount = integer("attempt_count").default(0)
    val lastAttemptAt = long("last_attempt_at").default(0L)
    val killBossCount = integer("kill_boss_count").default(0)
    val killEnemyCount = integer("kill_enemy_count").default(0)
    val takeBoxCount = integer("take_box_count").default(0)
    val defeatCount = integer("defeat_count").default(0)
    val todayDefeatCount = integer("today_defeat_count").default(0)
    val passCount = integer("pass_count").default(0)

    override val primaryKey = PrimaryKey(commanderId, chapterId)
    init { index(false, commanderId) }
}

object ChapterStates : Table("chapter_states") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val chapterId = integer("chapter_id")
    val state = blob("state")
    val currentFleetId = integer("current_fleet_id").default(0)
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId, chapterId)
    init { index(false, commanderId) }
}

object ChapterDrops : Table("chapter_drops") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val chapterId = integer("chapter_id")
    val shipId = integer("ship_id").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object EventCollections : Table("event_collections") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val collectionId = integer("collection_id")
    val startTime = integer("start_time").default(0)
    val finishTime = integer("finish_time").default(0)
    val shipIds = text("ship_ids").default("[]")

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object RemasterStates : Table("remaster_states") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val activeChapterId = integer("active_chapter_id").default(0)
    val ticketCount = integer("ticket_count").default(0)
    val dailyCount = integer("daily_count").default(0)
    val lastDailyReset = long("last_daily_reset").default(0L)

    override val primaryKey = PrimaryKey(commanderId)
}

object ApartmentData : Table("apartment_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val dailyVigorMax = integer("daily_vigor_max").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(commanderId)
}

object ApartmentShips : Table("apartment_ships") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipGroup = integer("ship_group")
    val favorLv = integer("favor_lv").default(1)
    val favorExp = integer("favor_exp").default(0)
    val dailyFavor = integer("daily_favor").default(0)
    val curSkin = integer("cur_skin").default(0)
    val name = text("name").default("")
    val nameCd = integer("name_cd").default(0)
    val visitTime = integer("visit_time").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(id)
}

object ApartmentRooms : Table("apartment_rooms") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val roomId = integer("room_id")
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(id)
}

object ApartmentIns : Table("apartment_ins") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipGroup = integer("ship_group")
    val careFlag = integer("care_flag").default(0)
    val curBack = integer("cur_back").default(0)
    val curCommId = integer("cur_comm_id").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(id)
}

object TbData : Table("tb_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val tbId = integer("tb_id").default(0)
    val name = text("name").default("")
    val difficulty = integer("difficulty").default(0)
    val favorLv = integer("favor_lv").default(0)
    val evalFail = integer("eval_fail").default(0)
    val roundNum = integer("round_num").default(0)
    val inTemp = integer("in_temp").default(0)
    val tempRound = integer("temp_round").default(0)
    val ngPlusCount = integer("ng_plus_count").default(0)
    val maxRound = integer("max_round").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(commanderId)
}

object TbPermanent : Table("tb_permanent") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val ngPlusCount = integer("ng_plus_count").default(0)
    val maxRound = integer("max_round").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(commanderId)
}

object TimeRewards : Table("time_rewards") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val rewardId = integer("reward_id")
    val timestamp = integer("timestamp").default(0)
    val sendTime = integer("send_time").default(0)
    val attachFlag = integer("attach_flag").default(0)
    val title = text("title").default("")
    val text = text("text").default("")
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(id)
}

object TimeRewardState : Table("time_reward_state") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val number = integer("number").default(0)
    val maxTimestamp = integer("max_timestamp").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object RemasterProgress : Table("remaster_progress") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val chapterId = integer("chapter_id")
    val pos = integer("pos")
    val count = integer("count").default(0)
    val received = integer("received").default(0)

    override val primaryKey = PrimaryKey(commanderId, chapterId, pos)
    init { index(false, commanderId) }
}

object Punishments : Table("punishments") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val reason = text("reason").default("")
    val expiresAt = long("expires_at").default(0L)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object ShopOffers : Table("shop_offers") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shopId = integer("shop_id")
    val payCount = integer("pay_count").default(0)
    val effects = text("effects").default("[]")
    val effectArgs = text("effect_args").nullable()
    val number = integer("number").default(0)
    val resourceNumber = integer("resource_number").default(0)
    val resourceId = integer("resource_id").default(0)
    val type = integer("type").default(0)
    val genre = text("genre").default("")
    val discount = integer("discount").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object MonthShopPurchases : Table("month_shop_purchases") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shopId = integer("shop_id")
    val count = integer("count").default(0)
    val month = integer("month").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object ConfigEntries : Table("config_entries") {
    val key = text("key")
    val value = text("value").default("")

    override val primaryKey = PrimaryKey(key)
}

object Friends : Table("friends") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val friendId = integer("friend_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val state = integer("state").default(0)
    val createdAt = long("created_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId, friendId)
    init { index(false, commanderId) }
}

object Guilds : Table("guilds") {
    val id = integer("id").autoIncrement()
    val name = text("name").default("")
    val leaderId = integer("leader_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val memberCount = integer("member_count").default(1)

    override val primaryKey = PrimaryKey(id)
}

object GuildMembers : Table("guild_members") {
    val guildId = integer("guild_id").references(Guilds.id, onDelete = ReferenceOption.CASCADE)
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val role = integer("role").default(0)

    override val primaryKey = PrimaryKey(guildId, commanderId)
    init { index(false, commanderId) }
}

object Tasks : Table("tasks") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val taskId = integer("task_id")
    val activityId = integer("activity_id").default(0)
    val progress = integer("progress").default(0)
    val finishFlag = integer("finish_flag").default(0)
    val acceptTime = long("accept_time").default(0L)
    val submitTime = long("submit_time").default(0L)

    override val primaryKey = PrimaryKey(commanderId, taskId)
    init { index(false, commanderId) }
}

object ActivityRecords : Table("activity_records") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val activityId = integer("activity_id")
    val data1 = integer("data1").default(0)
    val data2 = integer("data2").default(0)
    val data3 = integer("data3").default(0)
    val data4 = integer("data4").default(0)
    val stopTime = long("stop_time").default(0L)
    val startTime = long("start_time").default(0L)

    override val primaryKey = PrimaryKey(commanderId, activityId)
    init { index(false, commanderId) }
}

object WeeklyTasks : Table("weekly_tasks") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val taskId = integer("task_id")
    val progress = integer("progress").default(0)
    val finishFlag = integer("finish_flag").default(0)

    override val primaryKey = PrimaryKey(commanderId, taskId)
    init { index(false, commanderId) }
}

object WeeklyPtRewards : Table("weekly_pt_rewards") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val rewardId = integer("reward_id")

    override val primaryKey = PrimaryKey(commanderId, rewardId)
    init { index(false, commanderId) }
}

object WeeklyData : Table("weekly_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val pt = integer("pt").default(0)
    val rewardLv = integer("reward_lv").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object ActivityTasks : Table("activity_tasks") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val taskId = integer("task_id")
    val progress = integer("progress").default(0)
    val finishFlag = integer("finish_flag").default(0)

    override val primaryKey = PrimaryKey(commanderId, actId, taskId)
    init { index(false, commanderId) }
}

object ActivityTaskFinish : Table("activity_task_finish") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val taskId = integer("task_id")

    override val primaryKey = PrimaryKey(commanderId, actId, taskId)
    init { index(false, commanderId) }
}

object ChatMessages : Table("chat_messages") {
    val id = integer("id").autoIncrement()
    val roomId = integer("room_id").default(0)
    val channel = integer("channel").default(0)
    val senderId = integer("sender_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val content = text("content").default("")
    val timestamp = long("timestamp").default(0L)

    override val primaryKey = PrimaryKey(id)
    init { index(false, roomId); index(false, channel) }
}

object JuusLikes : Table("juus_likes") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val messageId = integer("message_id")

    override val primaryKey = PrimaryKey(commanderId, messageId)
    init { index(false, commanderId) }
}

object InsMessages : Table("ins_messages") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val npcId = integer("npc_id").default(0)
    val messageId = integer("message_id").default(0)
    val content = text("content").default("")
    val replyId = integer("reply_id").default(0)
    val isRead = integer("is_read").default(0)
    val timestamp = long("timestamp").default(0L)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object AchievementProgress : Table("achievement_progress") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val achievementId = integer("achievement_id")
    val progress = integer("progress").default(0)
    val timestamp = integer("timestamp").default(0)
    val isFinished = integer("is_finished").default(0)

    override val primaryKey = PrimaryKey(commanderId, achievementId)
    init { index(false, commanderId) }
}

object ShipStatistics : Table("ship_statistics") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipGroupId = integer("ship_group_id")
    val star = integer("star").default(0)
    val heartFlag = integer("heart_flag").default(0)
    val heartCount = integer("heart_count").default(0)
    val marryFlag = integer("marry_flag").default(0)
    val intimacyMax = integer("intimacy_max").default(0)
    val lvMax = integer("lv_max").default(0)

    override val primaryKey = PrimaryKey(commanderId, shipGroupId)
    init { index(false, commanderId) }
}

object ShipStatisticsAwards : Table("ship_statistics_awards") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipGroupId = integer("ship_group_id")
    val awardIndex = integer("award_index")
    val isClaimed = integer("is_claimed").default(0)

    override val primaryKey = PrimaryKey(commanderId, shipGroupId, awardIndex)
    init { index(false, commanderId) }
}

object ShipDiscussions : Table("ship_discussions") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipGroupId = integer("ship_group_id")
    val context = text("context").default("")
    val goodCount = integer("good_count").default(0)
    val badCount = integer("bad_count").default(0)
    val createdAt = long("created_at").default(0L)
    val isDeleted = integer("is_deleted").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, shipGroupId) }
}

object DiscussionLikes : Table("discussion_likes") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val discussionId = integer("discussion_id")
    val goodOrBad = integer("good_or_bad").default(1)

    override val primaryKey = PrimaryKey(commanderId, discussionId)
    init { index(false, commanderId) }
}

object PlayerVotes : Table("player_votes") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val type = integer("type")
    val dailyVote = integer("daily_vote").default(0)
    val loveVote = integer("love_vote").default(0)
    val dailyShipList = text("daily_ship_list").default("[]")
    val lastResetDate = integer("last_reset_date").default(0)

    override val primaryKey = PrimaryKey(commanderId, type)
    init { index(false, commanderId) }
}

object AppreciationFavorites : Table("appreciation_favorites") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val category = integer("category")
    val itemId = integer("item_id")
    val isFavorite = integer("is_favorite").default(1)

    override val primaryKey = PrimaryKey(commanderId, category, itemId)
    init { index(false, commanderId) }
}

object EqcodeShares : Table("eqcode_shares") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipGroupId = integer("ship_group_id").default(0)
    val eqcode = text("eqcode").default("")
    val likeCount = integer("like_count").default(0)
    val evalPoint = integer("eval_point").default(0)
    val state = integer("state").default(0)
    val createdAt = long("created_at").default(0L)
    val isDeleted = integer("is_deleted").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object EqcodeShareLikes : Table("eqcode_share_likes") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shareId = integer("share_id")

    override val primaryKey = PrimaryKey(commanderId, shareId)
    init { index(false, commanderId) }
}

object ExerciseData : Table("exercise_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val score = integer("score").default(0)
    val rank = integer("rank").default(0)
    val fightCount = integer("fight_count").default(0)
    val fightCountResetTime = integer("fight_count_reset_time").default(0)
    val flashTargetCount = integer("flash_target_count").default(0)
    val seasonId = integer("season_id").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object ExerciseFleet : Table("exercise_fleet") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val vanguardShipIds = text("vanguard_ship_ids").default("[]")
    val mainShipIds = text("main_ship_ids").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object ArenaShopPurchases : Table("arena_shop_purchases") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shopId = integer("shop_id")
    val purchaseCount = integer("purchase_count").default(0)

    override val primaryKey = PrimaryKey(commanderId, shopId)
    init { index(false, commanderId) }
}

object ArenaShopState : Table("arena_shop_state") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val type = integer("type")
    val flashCount = integer("flash_count").default(0)
    val nextFlashTime = integer("next_flash_time").default(0)
    val shopItems = text("shop_items").default("[]")

    override val primaryKey = PrimaryKey(commanderId, type)
}

object DormData : Table("dorm_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val lv = integer("lv").default(1)
    val food = integer("food").default(0)
    val foodMaxIncrease = integer("food_max_increase").default(0)
    val foodMaxIncreaseCount = integer("food_max_increase_count").default(0)
    val floorNum = integer("floor_num").default(1)
    val expPos = integer("exp_pos").default(0)
    val nextTimestamp = integer("next_timestamp").default(0)
    val loadExp = integer("load_exp").default(0)
    val loadFood = integer("load_food").default(0)
    val loadTime = integer("load_time").default(0)
    val name = text("name").default("")
    val isOpen = integer("is_open").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object DormShips : Table("dorm_ships") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipId = integer("ship_id")

    override val primaryKey = PrimaryKey(commanderId, shipId)
    init { index(false, commanderId) }
}

object DormFurniture : Table("dorm_furniture") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val furnitureId = integer("furniture_id")
    val count = integer("count").default(1)
    val getTime = integer("get_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, furnitureId)
    init { index(false, commanderId) }
}

object DormFurniturePut : Table("dorm_furniture_put") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val floor = integer("floor")
    val putData = text("put_data").default("[]")

    override val primaryKey = PrimaryKey(commanderId, floor)
    init { index(false, commanderId) }
}

object DormThemes : Table("dorm_themes") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val themeId = integer("theme_id")
    val name = text("name").default("")
    val furniturePutData = text("furniture_put_data").default("{}")
    val isUploaded = integer("is_uploaded").default(0)
    val uploadTime = long("upload_time").default(0L)
    val md5 = text("md5").default("")
    val likeCount = integer("like_count").default(0)
    val favCount = integer("fav_count").default(0)
    val isDeleted = integer("is_deleted").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object DormThemeFavorites : Table("dorm_theme_favorites") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val themeId = text("theme_id")
    val uploadTime = integer("upload_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, themeId)
    init { index(false, commanderId) }
}

object DormThemeLikes : Table("dorm_theme_likes") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val themeId = text("theme_id")

    override val primaryKey = PrimaryKey(commanderId, themeId)
    init { index(false, commanderId) }
}

object IslandData : Table("island_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val level = integer("level").default(1)
    val exp = integer("exp").default(0)
    val storageLevel = integer("storage_level").default(1)
    val name = text("name").default("")
    val prosperity = integer("prosperity").default(0)
    val prosperityRewarded = integer("prosperity_rewarded").default(0)
    val agoraLevel = integer("agora_level").default(1)
    val openFlag = integer("open_flag").default(0)
    val inviteCode = text("invite_code").default("")
    val dailyTimestamp = integer("daily_timestamp").default(0)
    val dailyList = text("daily_list").default("[]")
    val formulaNum = integer("formula_num").default(0)
    val whiteList = text("white_list").default("[]")
    val blackList = text("black_list").default("[]")
    val flagList = text("flag_list").default("[]")
    val actionList = text("action_list").default("[]")
    val actionFeedbackNpcList = text("action_feedback_npc_list").default("[]")
    val followShips = text("follow_ships").default("[]")
    val placedData = text("placed_data").default("{}")
    val abilityList = text("ability_list").default("[]")
    val treeGiftTimestamp = integer("tree_gift_timestamp").default(0)
    val treeGiftCount = integer("tree_gift_count").default(0)
    val treeGiftInvited = integer("tree_gift_invited").default(0)
    val treeGiftVisitor = integer("tree_gift_visitor").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandItems : Table("island_items") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val itemId = integer("item_id")
    val num = integer("num").default(0)

    override val primaryKey = PrimaryKey(commanderId, itemId)
    init { index(false, commanderId) }
}

object IslandFurniture : Table("island_furniture") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val furnitureId = integer("furniture_id")
    val count = integer("count").default(1)
    val getTime = integer("get_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, furnitureId)
    init { index(false, commanderId) }
}

object IslandShips : Table("island_ships") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipId = integer("ship_id")
    val lv = integer("lv").default(1)
    val exp = integer("exp").default(0)
    val breakLv = integer("break_lv").default(0)
    val skillLv = integer("skill_lv").default(1)
    val power = integer("power").default(100)
    val recoverTime = integer("recover_time").default(0)
    val buffList = text("buff_list").default("[]")
    val extraAttrList = text("extra_attr_list").default("[]")
    val upLimitState = integer("up_limit_state").default(0)
    val curSkinId = integer("cur_skin_id").default(0)
    val workPlaceType = integer("work_place_type").default(0)
    val workPlacePos = integer("work_place_pos").default(0)
    val energy = integer("energy").default(100)

    override val primaryKey = PrimaryKey(commanderId, shipId)
    init { index(false, commanderId) }
}

object IslandBuilds : Table("island_builds") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val buildId = integer("build_id")
    val shipAppointList = text("ship_appoint_list").default("[]")
    val awardList = text("award_list").default("[]")
    val appointList = text("appoint_list").default("[]")
    val buildCollect = text("build_collect").default("{}")
    val handList = text("hand_list").default("[]")
    val makeList = text("make_list").default("[]")
    val makeNum = integer("make_num").default(0)
    val level = integer("level").default(1)

    override val primaryKey = PrimaryKey(commanderId, buildId)
    init { index(false, commanderId) }
}

object IslandOrderSlots : Table("island_order_slots") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val slotId = integer("slot_id")
    val type = integer("type").default(0)
    val curSelect = integer("cur_select").default(0)
    val startTime = integer("start_time").default(0)
    val submitTime = integer("submit_time").default(0)
    val position = integer("position").default(0)
    val dialogId = integer("dialog_id").default(0)
    val cost = text("cost").default("[]")
    val orderLv = integer("order_lv").default(0)
    val viewFlag = integer("view_flag").default(0)

    override val primaryKey = PrimaryKey(commanderId, slotId)
    init { index(false, commanderId) }
}

object IslandOrderShipSlots : Table("island_order_ship_slots") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val slotId = integer("slot_id")
    val state = integer("state").default(0)
    val loadTime = integer("load_time").default(0)
    val getTime = integer("get_time").default(0)
    val cost = text("cost").default("[]")
    val reward = text("reward").default("[]")
    val finishNum = integer("finish_num").default(0)
    val autoTime = integer("auto_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, slotId)
    init { index(false, commanderId) }
}

object IslandOrderSystem : Table("island_order_system") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val favor = integer("favor").default(0)
    val getFavor = integer("get_favor").default(0)
    val dailySelect = integer("daily_select").default(0)
    val dailySlotNum = integer("daily_slot_num").default(0)
    val timeSlotNum = integer("time_slot_num").default(0)
    val speedList = text("speed_list").default("[]")
    val shipRefresh = integer("ship_refresh").default(0)
    val appointList = text("appoint_list").default("[]")
    val actGroup = text("act_group").default("{}")

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandTasks : Table("island_tasks") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val taskId = integer("task_id")
    val timestamp = integer("timestamp").default(0)
    val processList = text("process_list").default("[]")
    val isFinished = integer("is_finished").default(0)

    override val primaryKey = PrimaryKey(commanderId, taskId)
    init { index(false, commanderId) }
}

object IslandTaskRandom : Table("island_task_random") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val taskId = integer("task_id")
    val timestamp = integer("timestamp").default(0)

    override val primaryKey = PrimaryKey(commanderId, taskId)
    init { index(false, commanderId) }
}

object IslandSeasonData : Table("island_season_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val seasonId = integer("season_id").default(0)
    val pt = integer("pt").default(0)
    val fetchList = text("fetch_list").default("[]")
    val countList = text("count_list").default("[]")
    val seasonReviewList = text("season_review_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandTech : Table("island_tech") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val finishList = text("finish_list").default("[]")
    val repeatFinishList = text("repeat_finish_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandVisitors : Table("island_visitors") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val visitorId = integer("visitor_id")
    val visitorName = text("visitor_name").default("")
    val visitTime = integer("visit_time").default(0)
    val cmd = integer("cmd").default(0)
    val likeFlag = integer("like_flag").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object IslandThemes : Table("island_themes") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val themeId = integer("theme_id")
    val name = text("name").default("")
    val placedData = text("placed_data").default("{}")

    override val primaryKey = PrimaryKey(commanderId, themeId)
    init { index(false, commanderId) }
}

object IslandShops : Table("island_shops") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shopId = integer("shop_id")
    val existTime = integer("exist_time").default(0)
    val refreshTime = integer("refresh_time").default(0)
    val goodsList = text("goods_list").default("[]")
    val refreshCount = integer("refresh_count").default(0)

    override val primaryKey = PrimaryKey(commanderId, shopId)
    init { index(false, commanderId) }
}

object IslandGather : Table("island_gather") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val gatherId = integer("gather_id")
    val posX = float("pos_x").default(0f)
    val posY = float("pos_y").default(0f)
    val posZ = float("pos_z").default(0f)
    val state = integer("state").default(0)
    val mark = integer("mark").default(0)
    val refreshTime = integer("refresh_time").default(0)
    val pushType = integer("push_type").default(0)

    override val primaryKey = PrimaryKey(commanderId, gatherId)
    init { index(false, commanderId) }
}

object IslandCollectItems : Table("island_collect_items") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val collectId = integer("collect_id")
    val fragmentList = text("fragment_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId, collectId)
    init { index(false, commanderId) }
}

object IslandCollectFinish : Table("island_collect_finish") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val finishId = integer("finish_id")

    override val primaryKey = PrimaryKey(commanderId, finishId)
    init { index(false, commanderId) }
}

object IslandFishData : Table("island_fish_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val oldBait = integer("old_bait").default(0)
    val fishRod = integer("fish_rod").default(0)
    val fishWeight = text("fish_weight").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandTradeData : Table("island_trade_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val tradeId = integer("trade_id")
    val lv = integer("lv").default(1)
    val totalSell = integer("total_sell").default(0)
    val sellList = text("sell_list").default("[]")
    val restList = text("rest_list").default("[]")
    val postList = text("post_list").default("[]")
    val endTime = integer("end_time").default(0)
    val speedTime = integer("speed_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, tradeId)
    init { index(false, commanderId) }
}

object IslandTradeSys : Table("island_trade_sys") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val todayEvent = integer("today_event").default(0)
    val todayTrade = integer("today_trade").default(0)
    val effectFoodId = integer("effect_food_id").default(0)
    val effectAddPer = integer("effect_add_per").default(0)
    val todayNum = text("today_num").default("[]")
    val presellList = text("presell_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandAchievements : Table("island_achievements") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val eventArg = integer("event_arg").default(0)
    val eventType = integer("event_type").default(0)
    val value = integer("value").default(0)

    override val primaryKey = PrimaryKey(commanderId, eventArg, eventType)
    init { index(false, commanderId) }
}

object IslandAchievementFinish : Table("island_achievement_finish") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val finishId = integer("finish_id")

    override val primaryKey = PrimaryKey(commanderId, finishId)
    init { index(false, commanderId) }
}

object IslandDressData : Table("island_dress_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val curDressType = integer("cur_dress_type").default(0)
    val curDressId = integer("cur_dress_id").default(0)
    val hadDress = text("had_dress").default("[]")
    val capList = text("cap_list").default("[]")
    val wearList = text("wear_list").default("[]")
    val skinList = text("skin_list").default("[]")
    val dressColor = integer("dress_color").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandSpeedTickets : Table("island_speed_tickets") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val ticketKey = integer("ticket_key")
    val num = integer("num").default(0)

    override val primaryKey = PrimaryKey(commanderId, ticketKey)
    init { index(false, commanderId) }
}

object IslandViewBook : Table("island_view_book") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val condList = text("cond_list").default("[]")
    val bookList = text("book_list").default("[]")
    val bookAwards = text("book_awards").default("[]")
    val bookCollects = text("book_collects").default("[]")
    val itemList = text("item_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandGlobalBuff : Table("island_global_buff") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val foreverList = text("forever_list").default("[]")
    val limitList = text("limit_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandTreasure : Table("island_treasure") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val weekBuyNum = integer("week_buy_num").default(0)
    val sellList = text("sell_list").default("[]")
    val priceList = text("price_list").default("[]")
    val inviteList = text("invite_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandPlayerPos : Table("island_player_pos") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val mapId = integer("map_id").default(0)
    val posX = float("pos_x").default(0f)
    val posY = float("pos_y").default(0f)
    val posZ = float("pos_z").default(0f)
    val rotX = float("rot_x").default(0f)
    val rotY = float("rot_y").default(0f)
    val rotZ = float("rot_z").default(0f)
    val rotW = float("rot_w").default(1f)

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandNpcData : Table("island_npc_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val npcId = integer("npc_id")
    val objectId = integer("object_id").default(0)

    override val primaryKey = PrimaryKey(commanderId, npcId)
    init { index(false, commanderId) }
}

object IslandSocialData : Table("island_social_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val picture = integer("picture").default(0)
    val visitWord = text("visit_word").default("")
    val socialFlag = integer("social_flag").default(0)
    val labelViewFlag = integer("label_view_flag").default(0)
    val labelList = text("label_list").default("[]")
    val achieveNum = integer("achieve_num").default(0)
    val visitNum = integer("visit_num").default(0)
    val goodNum = integer("good_num").default(0)
    val shipNum = integer("ship_num").default(0)
    val bookNum = integer("book_num").default(0)
    val labelFlag = integer("label_flag").default(0)
    val goodFlag = integer("good_flag").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object IslandInviteList : Table("island_invite_list") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val shipId = integer("ship_id")

    override val primaryKey = PrimaryKey(commanderId, shipId)
    init { index(false, commanderId) }
}

object IslandGameTypeShips : Table("island_game_type_ships") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val gameType = integer("game_type")
    val shipId = integer("ship_id")

    override val primaryKey = PrimaryKey(commanderId, gameType, shipId)
    init { index(false, commanderId) }
}

object IslandImageList : Table("island_image_list") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val imageId = integer("image_id")
    val num = integer("num").default(0)

    override val primaryKey = PrimaryKey(commanderId, imageId)
    init { index(false, commanderId) }
}

object NavalAcademyData : Table("naval_academy_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val oilWellLevel = integer("oil_well_level").default(1)
    val oilWellLvUpTime = integer("oil_well_lv_up_time").default(0)
    val goldWellLevel = integer("gold_well_level").default(1)
    val goldWellLvUpTime = integer("gold_well_lv_up_time").default(0)
    val classLv = integer("class_lv").default(1)
    val classLvUpTime = integer("class_lv_up_time").default(0)
    val proficiency = integer("proficiency").default(0)
    val skillClassNum = integer("skill_class_num").default(0)
    val dailyFinishBuffCnt = integer("daily_finish_buff_cnt").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object SkillClassSlots : Table("skill_class_slots") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val roomId = integer("room_id")
    val shipId = integer("ship_id").default(0)
    val startTime = integer("start_time").default(0)
    val finishTime = integer("finish_time").default(0)
    val skillPos = integer("skill_pos").default(0)
    val exp = integer("exp").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object ShoppingStreetData : Table("shopping_street_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val lv = integer("lv").default(1)
    val nextFlashTime = integer("next_flash_time").default(0)
    val lvUpTime = integer("lv_up_time").default(0)
    val flashCount = integer("flash_count").default(0)

    override val primaryKey = PrimaryKey(commanderId)
}

object StreetGoods : Table("street_goods") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val goodsId = integer("goods_id")
    val discount = integer("discount").default(100)
    val buyCount = integer("buy_count").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object TutHandbooks : Table("tut_handbooks") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val handbookId = integer("handbook_id")
    val pt = integer("pt").default(0)
    val award = integer("award").default(0)
    val finishedTaskIds = text("finished_task_ids").default("[]")

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object MeowfficerData : Table("meowfficer_data") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val templateId = integer("template_id")
    val level = integer("level").default(1)
    val exp = integer("exp").default(0)
    val isLocked = integer("is_locked").default(0)
    val abilityList = text("ability_list").default("[]")
    val abilityOriginList = text("ability_origin_list").default("[]")
    val abilityTime = integer("ability_time").default(0)
    val skillList = text("skill_list").default("[]")
    val usedPt = integer("used_pt").default(0)
    val name = text("name").default("")
    val renameTime = integer("rename_time").default(0)
    val homeCleanTime = integer("home_clean_time").default(0)
    val homePlayTime = integer("home_play_time").default(0)
    val homeFeedTime = integer("home_feed_time").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object MeowfficerBoxes : Table("meowfficer_boxes") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val boxId = integer("box_id")
    val poolId = integer("pool_id").default(0)
    val finishTime = integer("finish_time").default(0)
    val beginTime = integer("begin_time").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object MeowfficerPresets : Table("meowfficer_presets") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val presetId = integer("preset_id")
    val name = text("name").default("")
    val commandersJson = text("commanders_json").default("[]")

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object MeowfficerHomeSlots : Table("meowfficer_home_slots") {
    val id = integer("id").autoIncrement()
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val slotId = integer("slot_id")
    val opFlag = integer("op_flag").default(0)
    val expTime = integer("exp_time").default(0)
    val meowfficerId = integer("meowfficer_id").default(0)
    val style = integer("style").default(0)
    val cacheExp = integer("cache_exp").default(0)

    override val primaryKey = PrimaryKey(id)
    init { index(false, commanderId) }
}

object MeowfficerHomeData : Table("meowfficer_home_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val type = integer("type")
    val level = integer("level").default(1)
    val exp = integer("exp").default(0)
    val clean = integer("clean").default(0)

    override val primaryKey = PrimaryKey(commanderId, type)
    init { index(false, commanderId) }
}

object ValentineData : Table("valentine_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val groupId = integer("group_id")
    val level = integer("level").default(0)
    val exp = integer("exp").default(0)

    override val primaryKey = PrimaryKey(commanderId, groupId)
}

object ValentineLetters : Table("valentine_letters") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val groupId = integer("group_id")
    val letterId = integer("letter_id")

    override val primaryKey = PrimaryKey(commanderId, groupId, letterId)
}

object ChildData : Table("child_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val tid = integer("tid").default(0)
    val mood = integer("mood").default(0)
    val money = integer("money").default(0)
    val siteNumber = integer("site_number").default(0)
    val curTimeMonth = integer("cur_time_month").default(0)
    val curTimeWeek = integer("cur_time_week").default(0)
    val curTimeDay = integer("cur_time_day").default(0)
    val isEnding = integer("is_ending").default(0)
    val newGamePlusCount = integer("new_game_plus_count").default(0)
    val userName = text("user_name").default("")
    val target = integer("target").default(0)
    val hadTargetStageAward = integer("had_target_stage_award").default(0)
    val hadAdjustment = integer("had_adjustment").default(0)
    val isSpecialSecretaryValid = integer("is_special_secretary_valid").default(0)
    val endingBuyCount = integer("ending_buy_count").default(0)
    val memoryBuyCount = integer("memory_buy_count").default(0)
    val polaroidBuyCount = integer("polaroid_buy_count").default(0)
    val favorLv = integer("favor_lv").default(0)
    val favorExp = integer("favor_exp").default(0)
    val canTriggerHomeEvent = integer("can_trigger_home_event").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(commanderId)
}

object ValentineRewards : Table("valentine_rewards") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val rewardId = integer("reward_id")

    override val primaryKey = PrimaryKey(commanderId, rewardId)
}

object EquipSkins : Table("equip_skins") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val skinId = integer("skin_id")
    val count = integer("count").default(1)

    override val primaryKey = PrimaryKey(commanderId, skinId)
}

object CommanderStories : Table("commander_stories") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val storyId = integer("story_id")

    override val primaryKey = PrimaryKey(commanderId, storyId)
}

object ExpeditionCounts : Table("expedition_counts") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val expeditionId = integer("expedition_id")
    val count = integer("count").default(0)
    val lastResetDate = text("last_reset_date").default("")

    override val primaryKey = PrimaryKey(commanderId, expeditionId)
}

object EscortData : Table("escort_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val lineId = integer("line_id").default(0)
    val awardTimestamp = integer("award_timestamp").default(0)
    val flashTimestamp = integer("flash_timestamp").default(0)
    val mapData = text("map_data").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object SubmarineData : Table("submarine_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val refreshCount = integer("refresh_count").default(4)
    val nextRefreshTime = integer("next_refresh_time").default(0)
    val progress = integer("progress").default(0)
    val chapterList = text("chapter_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId)
}

object Activity26Coloring : Table("activity26_coloring") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val cellList = text("cell_list").default("[]")
    val colorList = text("color_list").default("[]")
    val awardList = text("award_list").default("[]")
    val startTime = integer("start_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, actId)
    init { index(false, commanderId) }
}

object WorldData : Table("world_data") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val mapId = integer("map_id").default(0)
    val time = integer("time").default(0)
    val round = integer("round").default(0)
    val submarineState = integer("submarine_state").default(0)
    val actionPower = integer("action_power").default(0)
    val actionPowerExtra = integer("action_power_extra").default(0)
    val lastRecoverTimestamp = integer("last_recover_timestamp").default(0)
    val actionPowerFetchCount = integer("action_power_fetch_count").default(0)
    val lastChangeGroupTimestamp = integer("last_change_group_timestamp").default(0)
    val enterMapId = integer("enter_map_id").default(0)
    val sirenChapter = integer("siren_chapter").default(0)
    val monthBoss = integer("month_boss").default(0)
    val camp = integer("camp").default(0)
    val isWorldOpen = integer("is_world_open").default(0)
    val cleanChapter = integer("clean_chapter").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(commanderId)
    init { index(false, commanderId) }
}

object FriendRequests : Table("friend_requests") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val requesterId = integer("requester_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val content = text("content").default("")
    val createdAt = long("created_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId, requesterId)
    init { index(false, commanderId) }
}

object Blacklist : Table("blacklist") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val blockedId = integer("blocked_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId, blockedId)
    init { index(false, commanderId) }
}

object Legions : Table("legions") {
    val id = integer("id").autoIncrement()
    val name = text("name").uniqueIndex()
    val faction = integer("faction").default(0)
    val policy = integer("policy").default(0)
    val level = integer("level").default(1)
    val exp = integer("exp").default(0)
    val announce = text("announce").default("")
    val manifesto = text("manifesto").default("")
    val memberCount = integer("member_count").default(1)
    val changeFactionCd = integer("change_faction_cd").default(0)
    val kickLeaderCd = integer("kick_leader_cd").default(0)
    val capital = integer("capital").default(0)
    val benefitFinishTime = integer("benefit_finish_time").default(0)
    val retreatCnt = integer("retreat_cnt").default(0)
    val techCancelCnt = integer("tech_cancel_cnt").default(0)
    val lastBenefitFinishTime = integer("last_benefit_finish_time").default(0)
    val activeEventCnt = integer("active_event_cnt").default(0)
    val weeklyTaskId = integer("weekly_task_id").default(0)
    val weeklyTaskProgress = integer("weekly_task_progress").default(0)
    val weeklyTaskMonday = integer("weekly_task_monday").default(0)
    val technologys = text("technologys").default("[]")
    val extraData = text("extra_data").default("{}")
    val createdAt = long("created_at").default(0L)

    override val primaryKey = PrimaryKey(id)
}

object LegionMembers : Table("legion_members") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val legionId = integer("legion_id").references(Legions.id, onDelete = ReferenceOption.CASCADE)
    val duty = integer("duty").default(0)
    val liveness = integer("liveness").default(0)
    val joinTime = integer("join_time").default(0)
    val donateCount = integer("donate_count").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(commanderId)
    init {
        index(false, commanderId)
        index(false, legionId)
    }
}

object LegionRequests : Table("legion_requests") {
    val id = integer("id").autoIncrement()
    val legionId = integer("legion_id").references(Legions.id, onDelete = ReferenceOption.CASCADE)
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val content = text("content").default("")
    val createdAt = long("created_at").default(0L)

    override val primaryKey = PrimaryKey(id)
    init {
        index(false, legionId)
        index(false, commanderId)
    }
}

object LegionActivity : Table("legion_activity") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val legionId = integer("legion_id").references(Legions.id, onDelete = ReferenceOption.CASCADE)
    val chapterId = integer("chapter_id").default(0)
    val operationId = integer("operation_id").default(0)
    val dailyCount = integer("daily_count").default(0)
    val joinTimes = integer("join_times").default(0)
    val isParticipant = integer("is_participant").default(0)
    val formation = text("formation").default("{}")
    val bossFleet = text("boss_fleet").default("{}")
    val events = text("events").default("{}")
    val bossData = text("boss_data").default("{}")
    val reports = text("reports").default("[]")
    val rewards = text("rewards").default("[]")
    val startTime = long("start_time").default(0L)
    val extraData = text("extra_data").default("{}")
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId)
    init { index(false, legionId) }
}

object LegionBattle : Table("legion_battle") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val legionId = integer("legion_id").references(Legions.id, onDelete = ReferenceOption.CASCADE)
    val donateCount = integer("donate_count").default(0)
    val donateTasks = text("donate_tasks").default("[]")
    val weeklyTaskId = integer("weekly_task_id").default(0)
    val weeklyTaskProgress = integer("weekly_task_progress").default(0)
    val weeklyTaskFlag = integer("weekly_task_flag").default(0)
    val benefitFinishTime = integer("benefit_finish_time").default(0)
    val techId = integer("tech_id").default(0)
    val techState = integer("tech_state").default(0)
    val techProgress = integer("tech_progress").default(0)
    val extraDonate = integer("extra_donate").default(0)
    val extraOperation = integer("extra_operation").default(0)
    val capitalLog = text("capital_log").default("{}")
    val rewards = text("rewards").default("[]")
    val extraData = text("extra_data").default("{}")
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId)
    init { index(false, legionId) }
}

object Technology : Table("technology") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val refreshFlag = integer("refresh_flag").default(0)
    val refreshList = text("refresh_list").default("[]")
    val queue = text("queue").default("[]")
    val catchupVersion = integer("catchup_version").default(0)
    val catchupTarget = integer("catchup_target").default(0)
    val catchupPursuings = text("catchup_pursuings").default("[]")
    val extraData = text("extra_data").default("{}")
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId)
}

object Blueprint : Table("blueprint") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val blueprintId = integer("blueprint_id").default(0)
    val shipId = integer("ship_id").default(0)
    val startTime = integer("start_time").default(0)
    val level = integer("level").default(0)
    val exp = integer("exp").default(0)
    val coldTime = integer("cold_time").default(0)
    val dailyCatchupStrengthen = integer("daily_catchup_strengthen").default(0)
    val dailyCatchupStrengthenUr = integer("daily_catchup_strengthen_ur").default(0)
    val blueprintList = text("blueprint_list").default("[]")
    val extraData = text("extra_data").default("{}")
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId)
}

object MetaCharacter : Table("meta_character") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val metaCharList = text("meta_char_list").default("[]")
    val skillData = text("skill_data").default("{}")
    val extraData = text("extra_data").default("{}")
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId)
}

object FleetTech : Table("fleet_tech") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val techList = text("tech_list").default("[]")
    val techsetList = text("techset_list").default("[]")
    val extraData = text("extra_data").default("{}")
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(commanderId)
}

object WorldChapters : Table("world_chapters") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val chapterId = integer("chapter_id")
    val stateFlag = integer("state_flag").default(0)
    val cellData = text("cell_data").default("[]")
    val landData = text("land_data").default("[]")
    val posData = text("pos_data").default("[]")
    val awardFlag = integer("award_flag").default(0)

    override val primaryKey = PrimaryKey(commanderId, chapterId)
    init { index(false, commanderId) }
}

object WorldTasks : Table("world_tasks") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val taskId = integer("task_id")
    val progress = integer("progress").default(0)
    val acceptTime = integer("accept_time").default(0)
    val submitTime = integer("submit_time").default(0)
    val eventMapId = integer("event_map_id").default(0)

    override val primaryKey = PrimaryKey(commanderId, taskId)
    init { index(false, commanderId) }
}

object WorldPorts : Table("world_ports") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val portId = integer("port_id")
    val taskData = text("task_data").default("[]")
    val goodsData = text("goods_data").default("[]")
    val nextRefreshTime = integer("next_refresh_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, portId)
    init { index(false, commanderId) }
}

object WorldTargets : Table("world_targets") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val targetId = integer("target_id")
    val processData = text("process_data").default("[]")
    val fetchStarData = text("fetch_star_data").default("[]")

    override val primaryKey = PrimaryKey(commanderId, targetId)
    init { index(false, commanderId) }
}

object WorldBoss : Table("world_boss") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val bossId = integer("boss_id")
    val templateId = integer("template_id").default(0)
    val lv = integer("lv").default(0)
    val hp = integer("hp").default(0)
    val owner = integer("owner").default(0)
    val lastTime = integer("last_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, bossId)
    init { index(false, commanderId) }
}

object Activity26Anniversary : Table("activity26_anniversary") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val registerDate = integer("register_date").default(0)
    val guildName = text("guild_name").default("")
    val chapterId = integer("chapter_id").default(0)
    val marryNumber = integer("marry_number").default(0)
    val medalNumber = integer("medal_number").default(0)
    val furnitureNumber = integer("furniture_number").default(0)
    val furnitureWorth = integer("furniture_worth").default(0)
    val characterId = integer("character_id").default(0)
    val firstLadyId = integer("first_lady_id").default(0)
    val firstLadyName = text("first_lady_name").default("")
    val firstLadyTime = integer("first_lady_time").default(0)
    val firstOnline = integer("first_online").default(0)
    val worldMaxTask = integer("world_max_task").default(0)
    val collectNum = integer("collect_num").default(0)
    val combat = integer("combat").default(0)
    val shipNumTotal = integer("ship_num_total").default(0)
    val shipNum120 = integer("ship_num_120").default(0)
    val shipNum125 = integer("ship_num_125").default(0)
    val love200Num = integer("love200_num").default(0)
    val skinNum = integer("skin_num").default(0)
    val skinShipNum = integer("skin_ship_num").default(0)

    override val primaryKey = PrimaryKey(commanderId, actId)
    init { index(false, commanderId) }
}

object Activity26WorldBoss : Table("activity26_world_boss") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val bossHp = integer("boss_hp").default(0)
    val milestones = text("milestones").default("[]")
    val death = integer("death").default(0)
    val point = integer("point").default(0)

    override val primaryKey = PrimaryKey(commanderId, actId)
    init { index(false, commanderId) }
}

object Activity26Shop : Table("activity26_shop") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val startTime = integer("start_time").default(0)
    val stopTime = integer("stop_time").default(0)
    val goodsJson = text("goods_json").default("[]")

    override val primaryKey = PrimaryKey(commanderId, actId)
    init { index(false, commanderId) }
}

object Activity26ShopBuyRecord : Table("activity26_shop_buy_record") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val goodsId = integer("goods_id")
    val boughtList = text("bought_list").default("[]")

    override val primaryKey = PrimaryKey(commanderId, actId, goodsId)
    init { index(false, commanderId) }
}

object Activity26Cooking : Table("activity26_cooking") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val itemsJson = text("items_json").default("[]")
    val recipesJson = text("recipes_json").default("[]")
    val slotsJson = text("slots_json").default("[]")

    override val primaryKey = PrimaryKey(commanderId, actId)
    init { index(false, commanderId) }
}

object Activity26Ninja : Table("activity26_ninja") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val ptB = integer("pt_b").default(0)
    val ptM = integer("pt_m").default(0)
    val ptK = integer("pt_k").default(0)
    val builds = text("builds").default("[]")
    val roles = text("roles").default("[]")
    val recruits = text("recruits").default("[]")
    val buffs = text("buffs").default("[]")
    val maxLevel = integer("max_level").default(0)
    val curLevel = integer("cur_level").default(0)
    val maxDisplay = integer("max_display").default(0)
    val adjustTime = integer("adjust_time").default(0)
    val adjustHpB = integer("adjust_hp_b").default(0)
    val adjustHpM = integer("adjust_hp_m").default(0)
    val adjustHpK = integer("adjust_hp_k").default(0)
    val adjustMaxLevel = integer("adjust_max_level").default(0)
    val summaryPtB = integer("summary_pt_b").default(0)
    val summaryPtM = integer("summary_pt_m").default(0)
    val summaryPtK = integer("summary_pt_k").default(0)

    override val primaryKey = PrimaryKey(commanderId, actId)
    init { index(false, commanderId) }
}

object Activity26MiniGame : Table("activity26_minigame") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val hubId = integer("hub_id")
    val availableCnt = integer("available_cnt").default(0)
    val usedCnt = integer("used_cnt").default(0)
    val ultimate = integer("ultimate").default(0)
    val maxscoresJson = text("maxscores_json").default("[]")
    val datasJson = text("datas_json").default("[]")
    val kvDataJson = text("kv_data_json").default("[]")

    override val primaryKey = PrimaryKey(commanderId, hubId)
    init { index(false, commanderId) }
}

object Activity26GameRoom : Table("activity26_game_room") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val roomId = integer("room_id")
    val maxScore = integer("max_score").default(0)
    val weeklyFree = integer("weekly_free").default(0)
    val monthlyTicket = integer("monthly_ticket").default(0)
    val payCoinCount = integer("pay_coin_count").default(0)
    val firstEnter = integer("first_enter").default(0)

    override val primaryKey = PrimaryKey(commanderId, roomId)
    init { index(false, commanderId) }
}

object Activity26FlashSale : Table("activity26_flash_sale") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val type = integer("type")
    val goodsJson = text("goods_json").default("[]")
    val nextFlashTime = integer("next_flash_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, type)
    init { index(false, commanderId) }
}

object Activity26Party : Table("activity26_party") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val partyRolesJson = text("party_roles_json").default("[]")
    val specialRolesJson = text("special_roles_json").default("[]")
    val refreshTime = integer("refresh_time").default(0)

    override val primaryKey = PrimaryKey(commanderId, actId)
    init { index(false, commanderId) }
}

object Activity26Boss4th : Table("activity26_boss4th") {
    val actId = integer("act_id")
    val bossId = integer("boss_id")
    val bossHp = integer("boss_hp").default(0)
    val death = integer("death").default(0)
    val hourTraffic = integer("hour_traffic").default(0)
    val hourOff = integer("hour_off").default(0)

    override val primaryKey = PrimaryKey(actId, bossId)
}

object Activity26MiniGameIsland : Table("activity26_minigame_island") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val actId = integer("act_id")
    val itemListJson = text("item_list_json").default("[]")
    val nodeListJson = text("node_list_json").default("[]")

    override val primaryKey = PrimaryKey(commanderId, actId)
    init { index(false, commanderId) }
}

object MetaShips : Table("meta_ships") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val groupId = integer("group_id")
    val pt = integer("pt").default(0)
    val fetchData = text("fetch_data").default("[]")

    override val primaryKey = PrimaryKey(commanderId, groupId)
    init { index(false, commanderId) }
}

object MetaBoss : Table("meta_boss") {
    val commanderId = integer("commander_id").references(Commanders.commanderId, onDelete = ReferenceOption.CASCADE)
    val fightCount = integer("fight_count").default(0)
    val fightCountUpdateTime = integer("fight_count_update_time").default(0)
    val summonPt = integer("summon_pt").default(0)
    val summonPtOld = integer("summon_pt_old").default(0)
    val summonPtDailyAcc = integer("summon_pt_daily_acc").default(0)
    val summonPtOldDailyAcc = integer("summon_pt_old_daily_acc").default(0)
    val summonFree = integer("summon_free").default(0)
    val autoFightFinishTime = integer("auto_fight_finish_time").default(0)
    val defaultBossId = integer("default_boss_id").default(0)
    val autoFightMaxDamage = integer("auto_fight_max_damage").default(0)
    val guildSupport = integer("guild_support").default(0)
    val friendSupport = integer("friend_support").default(0)
    val worldSupport = integer("world_support").default(0)
    val selfBossLv = integer("self_boss_lv").default(0)
    val extraData = text("extra_data").default("{}")

    override val primaryKey = PrimaryKey(commanderId)
    init { index(false, commanderId) }
}
