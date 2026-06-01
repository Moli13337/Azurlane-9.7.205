package com.azurlane.server.packet

import com.azurlane.infra.network.PacketRegistry
import com.azurlane.server.handler.auth.*
import com.azurlane.server.handler.academy.*
import com.azurlane.server.handler.activity.*
import com.azurlane.server.handler.challenge.*
import com.azurlane.server.handler.meowfficer.*
import com.azurlane.server.handler.guild.*
import com.azurlane.server.handler.arena.*
import com.azurlane.server.handler.chapter.*
import com.azurlane.server.handler.collection.*
import com.azurlane.server.handler.dorm.*
import com.azurlane.server.handler.equipment.*
import com.azurlane.server.handler.island.*
import com.azurlane.server.handler.item.*
import com.azurlane.server.handler.player.*
import com.azurlane.server.handler.ship.*
import com.azurlane.server.handler.shop.*
import com.azurlane.server.handler.task.*
import com.azurlane.server.handler.activity26.*
import com.azurlane.server.handler.apartment.*
import com.azurlane.server.handler.child.*
import com.azurlane.server.handler.tb.*
import com.azurlane.server.handler.mailv2.*
import com.azurlane.server.handler.world.*
import com.azurlane.server.handler.meta.*
import com.azurlane.server.handler.battle.*
import com.azurlane.server.handler.friend.*
import com.azurlane.server.handler.legion.*
import com.azurlane.server.handler.technology.*

object PacketRegistryInit {
    fun registerAll(registry: PacketRegistry) {
        // 账号相关 (10001-10027)
        registry.register(RegisterAccountHandler())    // 10001 - 注册账号
        registry.register(ServerListHandler())         // 10018 - 服务器状态检查
        registry.register(LoginHandler())              // 10020 - 登录认证
        registry.register(JoinServerHandler())         // 10022 - 加入服务器
        registry.register(CreateNewPlayerHandler())    // 10024 - 创建新玩家
        registry.register(PlayerExistHandler())        // 10026 - 玩家是否存在

        // 心跳 (10100-10101)
        registry.register(HeartbeatHandler())          // 10100 - 心跳

        // 资源版本 (10700-10701)
        registry.register(ResourceVersionHandler())    // 10700 - 资源版本检查

        // 网关登录 (10800-10803)
        registry.register(GatewayLoginHandler())       // 10800 - 网关登录(旧版)
        registry.register(GatewayLoginV2Handler())     // 10802 - 网关登录(新版)

        // 服务器列表HTTP (8239)
        registry.register(Cs8239Handler())             // 8239 - HTTP服务器列表

        // 追踪/埋点 (10991-10995)
        registry.register(TrackDataHandler())          // 10991 - 批量追踪上报
        registry.register(TrackSingleHandler())        // 10992 - 单条追踪上报
        registry.register(TrackActionHandler())        // 10993 - 行为追踪上报
        registry.register(TrackTypeHandler())          // 10994 - 追踪类型请求

        // 版本检查 (10996-10997)
        registry.register(VersionCheckHandler())       // 10996 - 版本检查

        // 玩家数据相关 (11000-11033)
        registry.register(LoginTimeSyncHandler())       // 11000 - 登录时间同步
        registry.register(PlayerLoginHandler())          // 11001 - 玩家登录聚合请求
        registry.register(PlayerInfoHandler())           // 11003 - 玩家详细信息
        registry.register(ResourceSyncHandler())         // 11004 - 资源同步
        registry.register(AttireApplyHandler())          // 11005 - 装扮应用
        registry.register(ChangeNameHandler())           // 11007 - 修改名称
        registry.register(ChangeManifestoHandler())      // 11009 - 修改签名
        registry.register(UpdateSecretaryHandler())      // 11011 - 更新秘书舰
        registry.register(GiveResourceHandler())         // 11013 - 资源发放(GM)
        registry.register(GMCommandHandler())            // 11100 - GM命令
        registry.register(PlayerBuffsHandler())          // 11015 - 玩家Buff列表
        registry.register(UpdateGuideIndexHandler())     // 11016 - 更新引导索引
        registry.register(UpdateStoryHandler())           // 11017 - 更新剧情
        registry.register(UpdateCommonFlagHandler())     // 11019 - 更新通用标记
        registry.register(CancelCommonFlagHandler())     // 11021 - 取消通用标记
        registry.register(RefundInfoHandler())           // 11023 - 退款信息
        registry.register(SurveyHandler())               // 11025 - 问卷请求
        registry.register(SurveyStatusHandler())         // 11027 - 问卷状态
        registry.register(SceneTrackHandler())            // 11029 - 主场景追踪
        registry.register(ChangeLivingAreaCoverHandler()) // 11030 - 修改生活区封面
        registry.register(BatchUpdateStoryHandler())      // 11032 - 批量更新剧情

        // 舰船/建造相关 (12001-12048)
        registry.register(PlayerDockHandler())            // 12001 - 船坞列表
        registry.register(ShipBuildHandler())             // 12002 - 建造舰船
        registry.register(RetireShipHandler())            // 12004 - 退役舰船
        registry.register(EquipToShipHandler())           // 12006 - 装备上舰
        registry.register(BuildQuickFinishHandler())      // 12008 - 立即完成建造
        registry.register(RemouldShipHandler())           // 12011 - 改造舰船
        registry.register(ModShipHandler())               // 12017 - 强化舰船
        registry.register(ShipActionValidateHandler())    // 12020 - 验证舰船操作
        registry.register(ChangeShipLockHandler())        // 12022 - 锁定/解锁舰船
        registry.register(OngoingBuildsHandler())         // 12024 - 建造队列信息
        registry.register(GetShipHandler())               // 12025 - 领取建造舰船
        registry.register(UpgradeStarHandler())           // 12027 - 突破舰船
        registry.register(ShipActionListHandler())        // 12029 - 舰船操作列表
        registry.register(FleetEnergyRecoverTimeHandler()) // 12031 - 士气恢复时间
        registry.register(ProposeShipHandler())           // 12032 - 誓约舰船
        registry.register(RenameProposedShipHandler())    // 12034 - 重命名誓约舰船
        registry.register(UpdateEquipSkinHandler())       // 12036 - 更换装备皮肤
        registry.register(UpgradeMaxLevelHandler())       // 12038 - 提升等级上限
        registry.register(SetFavoriteShipHandler())       // 12040 - 设置收藏舰船
        registry.register(BuildFinishHandler())           // 12043 - 查询建造完成列表
        registry.register(ConfirmShipHandler())           // 12045 - 确认舰船
        registry.register(ExchangeShipHandler())          // 12047 - 兑换舰船

        // 舰队相关 (12101-12106)
        registry.register(FleetListHandler())             // 12101 - 舰队列表
        registry.register(EditFleetHandler())             // 12102 - 编辑舰队
        registry.register(RenameFleetHandler())           // 12104 - 修改舰队名称

        // 皮肤相关 (12201-12299)
        registry.register(OwnedSkinsHandler())            // 12201 - 皮肤列表
        registry.register(ChangeSkinHandler())            // 12202 - 更换皮肤
        registry.register(RandomSecretaryToggleHandler()) // 12204 - 随机秘书舰开关
        registry.register(RandomSecretaryModeHandler())   // 12206 - 随机秘书舰模式
        registry.register(ShipShadowListHandler())        // 12208 - 舰船幻影列表
        registry.register(SetShipShadowHandler())         // 12210 - 设置舰船幻影
        registry.register(BatchShipCountHandler())        // 12212 - 批量获取舰船数量
        registry.register(SkinCountHandler())             // 12299 - 皮肤数量请求

        // 批量查询 (12301-12302)
        registry.register(BatchShipInfoHandler())         // 12301 - 批量获取舰船信息

        // 情人节活动 (12400-12411)
        registry.register(ValentineClaimHandler())        // 12400 - 情人节领取
        registry.register(ValentineBatchClaimHandler())   // 12402 - 情人节批量领取
        registry.register(ValentineRealizeGiftHandler())  // 12404 - 兑现情书礼物
        registry.register(ValentineQueryHandler())        // 12406 - 情人节查询
        registry.register(ValentineSelectHandler())       // 12408 - 情人节选择
        registry.register(ValentineLetterHandler())       // 12410 - 情人节写信

        // 章节相关 (13000-13508)
        registry.register(ChapterInitHandler())            // 13000 - 大世界初始化数据
        registry.register(ChapterListHandler())            // 13001 - 章节列表
        registry.register(CollectionListHandler())         // 13002 - 收藏信息
        registry.register(EventCollectionStartHandler())   // 13003 - 开始委托
        registry.register(EventFinishHandler())            // 13005 - 完成委托
        registry.register(EventGiveUpHandler())            // 13007 - 放弃委托
        registry.register(EventFlushHandler())             // 13009 - 刷新委托
        registry.register(ChapterTrackingHandler())        // 13101 - 章节追踪/进入
        registry.register(ChapterActionHandler())          // 13103 - 章节行动
        registry.register(ChapterBattleResultHandler())    // 13106 - 战斗结算
        registry.register(SwitchFleetHandler())            // 13107 - 切换编队
        registry.register(ChapterDropShipListHandler())    // 13109 - 掉落舰船列表
        registry.register(RemoveEliteTargetShipHandler())  // 13111 - 移除精英目标
        registry.register(EscortQueryHandler())            // 13301 - 护航查询
        registry.register(ExpeditionCountHandler())        // 13201 - 远征计数
        registry.register(SubmarineExpeditionHandler())    // 13401 - 潜艇远征
        registry.register(SubmarineChapterInfoHandler())   // 13403 - 潜艇章节
        registry.register(RemasterSetActiveChapterHandler()) // 13501 - 设置活跃章节
        registry.register(RemasterTicketsHandler())        // 13503 - 复刻票券
        registry.register(RemasterInfoHandler())           // 13505 - 复刻进度
        registry.register(RemasterAwardReceiveHandler())   // 13507 - 复刻奖励领取

        // 装备相关 (14001-14210)
        registry.register(EquipListHandler())             // 14001 - 装备数据初始化
        registry.register(UpgradeEquipOnShipHandler())    // 14002 - 舰船上装备强化
        registry.register(UpgradeEquipInBagHandler())     // 14004 - 背包中装备强化
        registry.register(ComposeEquipHandler())          // 14006 - 装备合成
        registry.register(DestroyEquipmentsHandler())     // 14008 - 批量装备分解
        registry.register(RevertEquipHandler())           // 14010 - 装备还原
        registry.register(TransformEquipOnShipHandler())  // 14013 - 舰船上装备改造
        registry.register(TransformEquipInBagHandler())   // 14015 - 背包中装备改造
        registry.register(EquipSkinListHandler())         // 14101 - 装备皮肤数据同步
        registry.register(SpWeaponListHandler())          // 14200 - 兵装数据同步
        registry.register(EquipSpWeaponHandler())         // 14201 - 兵装穿戴
        registry.register(UpgradeSpWeaponHandler())       // 14203 - 兵装强化
        registry.register(ReforgeSpWeaponHandler())       // 14205 - 兵装重铸
        registry.register(ConfirmReforgeSpWeaponHandler()) // 14207 - 确认兵装重铸
        registry.register(CompositeSpWeaponHandler())     // 14209 - 兵装合成

        // 物品相关 (15001-15013)
        registry.register(OwnedItemsHandler())            // 15001 - 物品数据初始化
        registry.register(UseItemHandler())               // 15002 - 使用物品
        registry.register(SellItemLegacyHandler())        // 15004 - 出售物品(遗留)
        registry.register(ComposeItemHandler())           // 15006 - 合成物品
        registry.register(SellItemHandler())              // 15008 - 批量出售物品
        registry.register(OpenItemHandler())              // 15010 - 打开/查看物品
        registry.register(BatchUseItemHandler())          // 15012 - 批量使用物品
        registry.register(ItemVersionCheckHandler())      // 15300 - 物品版本校验

        // 充值相关 (11501-11511)
        registry.register(ChargeStartHandler())            // 11501 - 充值发起
        registry.register(ChargeConfirmHandler())          // 11504 - 充值确认
        registry.register(ChargeStateHandler())            // 11506 - 充值状态查询
        registry.register(ChargeFailureHandler())          // 11510 - 充值失败通知

        // 商店相关 (16001-16206)
        registry.register(BuyShopItemHandler())           // 16001 - 购买商品
        registry.register(GachaDrawHandler())             // 16100 - 抽奖
        registry.register(ShopListHandler())              // 16104 - 请求商店列表
        registry.register(FlashShopListHandler())         // 16106 - 请求限时商店
        registry.register(BuyFlashShopItemHandler())      // 16108 - 购买限时商品
        registry.register(MonthShopPushHandler())         // 16200 - 月度商店推送
        registry.register(BuyMonthShopItemHandler())      // 16201 - 购买月度商店商品
        registry.register(RefreshMonthShopHandler())      // 16203 - 刷新月度商店
        registry.register(BuyCryptolaliaHandler())        // 16205 - 购买解密物

        // 任务/活动相关 (11200-11212)
        registry.register(TaskListHandler())              // 11200 - 任务/活动列表推送
        registry.register(TaskActionHandler())            // 11202 - 任务操作
        registry.register(TaskGroupHandler())             // 11204 - 任务组查询
        registry.register(ActivityInfoHandler())          // 11206 - 活动信息查询
        registry.register(ActivityInfo2Handler())         // 11208 - 活动信息查询2
        registry.register(TrackHandler())                 // 11212 - 追踪请求

        // 邮件相关 (11300-11308)
        registry.register(MailListHandler())              // 11300 - 邮件列表推送
        registry.register(ReadMailHandler())              // 11301 - 读取邮件
        registry.register(DeleteMailHandler())            // 11303 - 删除邮件
        registry.register(ClaimMailAttachmentHandler())   // 11305 - 领取邮件附件
        registry.register(BatchDeleteMailHandler())       // 11307 - 批量删除邮件

        // 好友相关 (11401-11414)
        registry.register(FriendListHandler())            // 11401 - 好友列表
        registry.register(AddFriendHandler())             // 11403 - 添加好友
        registry.register(AcceptFriendHandler())          // 11405 - 接受好友
        registry.register(RejectFriendHandler())          // 11407 - 拒绝好友
        registry.register(RemoveFriendHandler())          // 11409 - 删除好友
        registry.register(SearchFriendHandler())          // 11411 - 搜索好友
        registry.register(FriendRequestListHandler())     // 11413 - 好友请求列表

        // 聊天相关 (11601-11614)
        registry.register(ChatEmojiHandler())             // 11601 - 聊天表情
        registry.register(ChatStateHandler())             // 11603 - 聊天状态
        registry.register(CreateChatRoomHandler())        // 11605 - 创建聊天室
        registry.register(JoinChatRoomHandler())          // 11607 - 加入聊天室
        registry.register(LeaveChatRoomHandler())         // 11609 - 退出聊天室
        registry.register(SendChatMessageHandler())       // 11611 - 发送聊天消息
        registry.register(GetChatMessagesHandler())       // 11613 - 获取聊天消息

        // 社交/INS/JUUS相关 (11701-11802)
        registry.register(InsJuusActionHandler())         // 11701 - INS/JUUS操作
        registry.register(InsMessageHandler())            // 11703 - INS消息
        registry.register(InsAction2Handler())            // 11705 - INS操作2
        registry.register(JuusActionHandler())            // 11710 - JUUS操作
        registry.register(JuusChatHandler())              // 11712 - JUUS聊天
        registry.register(JuusLikeHandler())              // 11714 - JUUS点赞
        registry.register(JuusCommentHandler())           // 11716 - JUUS评论
        registry.register(JuusShareHandler())             // 11718 - JUUS分享
        registry.register(JuusDeleteHandler())            // 11720 - JUUS删除
        registry.register(JuusUnlockHandler())            // 11722 - JUUS解锁
        registry.register(RefluxRequestHandler())           // 11751 - 回流玩家请求
        registry.register(RefluxSignInHandler())            // 11753 - 回流签到
        registry.register(RefluxTaskHandler())              // 11755 - 回流任务
        registry.register(JuusDataHandler())              // 11800 - JUUS数据请求

        // 收藏/社交相关 (17005-17608)
        registry.register(ClaimAchievementAwardHandler())  // 17005 - 领取成就奖励
        registry.register(GetShipDiscussHandler())         // 17101 - 请求舰船评论列表
        registry.register(PostDiscussHandler())            // 17103 - 发表评论
        registry.register(DiscussVoteHandler())            // 17105 - 评论点赞/踩
        registry.register(GetShipDiscuss2Handler())        // 17107 - 请求评论列表(另一种)
        registry.register(ReportDiscussHandler())          // 17109 - 举报评论
        registry.register(GetVoteInfoHandler())            // 17201 - 请求投票信息
        registry.register(VoteActionHandler())             // 17203 - 投票操作
        registry.register(UseAttireHandler())              // 17301 - 装扮使用
        registry.register(EquipMedalHandler())             // 17401 - 勋章佩戴
        registry.register(GalleryRequestHandler())         // 17501 - 画廊请求
        registry.register(MusicRequestHandler())           // 17503 - 音乐请求
        registry.register(GalleryFavoriteHandler())        // 17505 - 画廊收藏操作
        registry.register(MusicFavoriteHandler())          // 17507 - 音乐收藏操作
        registry.register(AppreciationQueryHandler())      // 17509 - 鉴赏查询
        registry.register(AppreciationFavoriteHandler())   // 17511 - 鉴赏收藏操作
        registry.register(PlayMusicHandler())              // 17513 - 播放音乐
        registry.register(GetShareListHandler())           // 17601 - 请求分享列表
        registry.register(ShareEqcodeHandler())            // 17603 - 分享编队
        registry.register(LikeShareHandler())              // 17605 - 点赞分享
        registry.register(ReportShareHandler())            // 17607 - 举报分享

        // 演习/竞技场相关 (18001-18204)
        registry.register(GetExerciseEnemiesHandler())     // 18001 - 请求演习对手列表
        registry.register(RefreshExerciseRivalsHandler())  // 18003 - 刷新演习对手
        registry.register(GetExercisePowerRankHandler())   // 18006 - 请求战力排行列表
        registry.register(UpdateExerciseFleetHandler())    // 18008 - 更新演习舰队
        registry.register(ExerciseFightSettlementHandler()) // 18010 - 演习战斗结算
        registry.register(GetArenaShopHandler())           // 18100 - 获取军需商店
        registry.register(RefreshArenaShopHandler())       // 18102 - 刷新军需商店
        registry.register(GetRivalInfoHandler())           // 18104 - 获取对手详情
        registry.register(ArenaShopBuyHandler())           // 18106 - 购买军需商店商品
        registry.register(BillboardRankListPageHandler())  // 18201 - 请求排行榜分页
        registry.register(BillboardMyRankHandler())        // 18203 - 请求我的排名

        // 宿舍/后院相关 (19002-19131)
        registry.register(AddDormShipHandler())            // 19002 - 添加舰娘到宿舍
        registry.register(RemoveDormShipHandler())         // 19004 - 从宿舍移除舰娘
        registry.register(BuyFurnitureHandler())           // 19006 - 购买家具
        registry.register(SaveFurniturePutHandler())       // 19008 - 保存家具摆放
        registry.register(DormInteract1Handler())          // 19011 - 交互请求1
        registry.register(DormInteract2Handler())          // 19013 - 交互请求2
        registry.register(ToggleDormOpenHandler())         // 19015 - 开关宿舍
        registry.register(RenameDormHandler())             // 19016 - 修改宿舍名
        registry.register(GetDormThemeListHandler())       // 19018 - 获取主题列表
        registry.register(SaveDormThemeHandler())          // 19020 - 保存主题
        registry.register(DeleteDormThemeHandler())        // 19022 - 删除主题
        registry.register(UseDormThemeHandler())           // 19024 - 使用主题
        registry.register(VisitDormHandler())              // 19101 - 访问他人宿舍
        registry.register(GetOssTokenHandler())            // 19103 - 获取OSS凭证
        registry.register(GetRecommendThemesHandler())     // 19105 - 获取推荐主题
        registry.register(GetLatestThemesHandler())        // 19107 - 获取最新主题
        registry.register(UploadThemeHandler())            // 19109 - 上传主题
        registry.register(DeleteUploadedThemeHandler())    // 19111 - 删除已上传主题
        registry.register(GetThemeDetailHandler())         // 19113 - 获取主题详情
        registry.register(GetFavoriteThemesHandler())      // 19115 - 获取收藏主题列表
        registry.register(SearchThemesHandler())           // 19117 - 搜索主题
        registry.register(FavoriteThemeHandler())          // 19119 - 收藏主题
        registry.register(UnfavoriteThemeHandler())        // 19121 - 取消收藏主题
        registry.register(LikeThemeHandler())              // 19123 - 点赞主题
        registry.register(UnlikeThemeHandler())            // 19125 - 取消点赞主题
        registry.register(ReportThemeHandler())            // 19127 - 举报主题
        registry.register(ReportThemeDetailHandler())      // 19129 - 举报主题详情
        registry.register(GetThemeMd5Handler())            // 19131 - 批量获取主题MD5

        // 任务系统相关 (20005-20209)
        registry.register(ClaimTaskAwardHandler())         // 20005 - 领取任务奖励
        registry.register(AcceptTaskHandler())             // 20007 - 接受任务
        registry.register(UpdateTaskProgressHandler())     // 20009 - 更新任务进度
        registry.register(BatchClaimTaskAwardHandler())    // 20011 - 一键领取奖励
        registry.register(ClaimTaskAwardWithCostHandler()) // 20013 - 花费道具领取奖励
        registry.register(TriggerTaskEventHandler())       // 20016 - 触发任务事件
        registry.register(ClaimWeeklyTaskAwardHandler())   // 20106 - 领取周常任务奖励
        registry.register(ClaimWeeklyPtAwardHandler())     // 20108 - 领取周常积分奖励
        registry.register(ClaimWeeklyAwardWithCostHandler()) // 20110 - 花费道具领取周常奖励
        registry.register(ClaimActivityTaskAwardHandler()) // 20205 - 领取活动任务奖励
        registry.register(ClaimActivityTaskAwardWithCostHandler()) // 20207 - 花费道具领取活动任务奖励
        registry.register(UpdateActivityTaskProgressHandler()) // 20209 - 更新活动任务进度

        // 岛屿/大世界相关 (21000-21703)
        registry.register(EnterIslandHandler())                 // 21000 - 进入岛屿
        registry.register(SetIslandFlagHandler())               // 21002 - 设置开关标记
        registry.register(ModifyIslandNameHandler())            // 21004 - 修改岛屿名
        registry.register(SetIslandMarkHandler())               // 21006 - 设置标记
        registry.register(GetInviteCodeHandler())               // 21008 - 获取邀请码
        registry.register(UpgradeIslandHandler())               // 21010 - 升级岛屿
        registry.register(UpgradeIslandTypeHandler())           // 21012 - 升级类型
        registry.register(UseIslandItemHandler())               // 21014 - 使用物品
        registry.register(OpenIslandShopHandler())              // 21016 - 打开商店
        registry.register(BuyIslandGoodsHandler())              // 21018 - 购买商品
        registry.register(RefreshIslandShopHandler())           // 21020 - 刷新商店
        registry.register(ClaimSeasonAwardHandler())            // 21022 - 领取季节奖励
        registry.register(SettleSeasonHandler())                // 21024 - 结算季节
        registry.register(UseFormulaHandler())                  // 21026 - 使用配方
        registry.register(GetIslandTaskListHandler())           // 21030 - 获取任务列表
        registry.register(ClaimIslandTaskAwardHandler())        // 21032 - 领取任务奖励
        registry.register(AcceptIslandTaskHandler())            // 21034 - 接受任务
        registry.register(UpdateIslandTaskProgressHandler())    // 21036 - 更新任务进度
        registry.register(AbandonIslandTaskHandler())           // 21038 - 放弃任务
        registry.register(BatchClaimIslandTaskAwardHandler())   // 21041 - 批量领取任务奖励
        registry.register(UpgradeIslandTechHandler())           // 21050 - 升级科技
        registry.register(TriggerIslandEventHandler())          // 21052 - 触发事件
        registry.register(StartFishingHandler())                // 21060 - 钓鱼开始
        registry.register(EndFishingHandler())                  // 21062 - 钓鱼结束
        registry.register(ChangeBaitHandler())                  // 21064 - 更换鱼饵
        registry.register(IslandMakeHandler())                  // 21066 - 制作
        registry.register(EnterIslandSceneHandler())            // 21200 - 进入岛屿场景
        registry.register(VisitIslandHandler())                 // 21202 - 访问岛屿
        registry.register(LeaveIslandHandler())                 // 21204 - 离开岛屿
        registry.register(IslandLoadCompleteHandler())          // 21208 - 加载完成
        registry.register(IslandInteractHandler())              // 21209 - 交互操作
        registry.register(SyncIslandObjectHandler())            // 21211 - 同步对象
        registry.register(SwitchIslandMapHandler())             // 21213 - 切换地图
        registry.register(RequestIslandVisitorsHandler())       // 21215 - 请求访客列表
        registry.register(SaveIslandPositionHandler())          // 21229 - 保存位置
        registry.register(RequestFishingStateHandler())         // 21230 - 请求钓鱼状态
        registry.register(SellTreasureHandler())                // 21240 - 出售宝物
        registry.register(RequestTreasurePriceHandler())        // 21243 - 请求宝物价格
        registry.register(InviteFriendToIslandHandler())        // 21245 - 邀请好友
        registry.register(OpenIslandHandler())                  // 21300 - 开启岛屿
        registry.register(KickIslandPlayerHandler())            // 21302 - 踢出玩家
        registry.register(RequestIslandDataHandler())           // 21305 - 请求岛屿数据
        registry.register(UpdateIslandDataHandler())            // 21307 - 更新岛屿数据
        registry.register(IslandCollectHandler())               // 21310 - 采集
        registry.register(SendIslandGiftHandler())              // 21312 - 赠送礼物
        registry.register(ViewIslandGiftHandler())              // 21315 - 查看礼物
        registry.register(SaveIslandThemeHandler())             // 21317 - 保存主题
        registry.register(DeleteIslandThemeHandler())           // 21319 - 删除主题
        registry.register(GetIslandThemeListHandler())          // 21321 - 获取主题列表
        registry.register(SendIslandMessageHandler())           // 21323 - 发送消息
        registry.register(ViewIslandPlayerHandler())            // 21326 - 查看玩家信息
        registry.register(SetIslandPictureHandler())            // 21328 - 设置头像
        registry.register(SetIslandVisitWordHandler())          // 21330 - 设置留言
        registry.register(SetIslandLabelHandler())              // 21332 - 设置标签
        registry.register(LikeIslandHandler())                  // 21334 - 点赞
        registry.register(SetIslandUserLabelHandler())          // 21336 - 设置用户标签
        registry.register(SetIslandGroupHandler())              // 21338 - 设置分组
        registry.register(UnlockBookCondHandler())              // 21340 - 解锁图鉴条件
        registry.register(ClaimBookAwardHandler())              // 21343 - 领取图鉴奖励
        registry.register(ViewBookCollectHandler())             // 21345 - 查看图鉴收集
        registry.register(UpgradeBookHandler())                 // 21347 - 升级图鉴
        registry.register(AcceptOrderHandler())                 // 21401 - 接受订单
        registry.register(SubmitOrderHandler())                 // 21403 - 提交订单
        registry.register(FinishOrderHandler())                 // 21405 - 完成订单
        registry.register(ShipOrderOpHandler())                 // 21408 - 舰船订单操作
        registry.register(RefreshShipOrderHandler())            // 21410 - 刷新舰船订单
        registry.register(UpgradeOrderHandler())                // 21412 - 升级订单
        registry.register(FinishShipOrderHandler())             // 21414 - 完成舰船订单
        registry.register(LoadShipOrderHandler())               // 21416 - 装载舰船订单
        registry.register(TradeHandler())                       // 21418 - 交易
        registry.register(CancelTradeHandler())                 // 21420 - 取消交易
        registry.register(SpeedUpHandler())                     // 21423 - 加速
        registry.register(UseSpeedKeyHandler())                 // 21425 - 使用加速钥匙
        registry.register(SpeedUpAreaHandler())                 // 21427 - 加速区域
        registry.register(ClaimAppointAwardHandler())           // 21429 - 领取任命奖励
        registry.register(ViewAppointHandler())                 // 21431 - 查看任命
        registry.register(AppointShipHandler())                 // 21501 - 任命舰娘
        registry.register(CancelAppointHandler())               // 21503 - 取消任命
        registry.register(CollectBuildProductHandler())         // 21505 - 领取建筑产物
        registry.register(RefreshBuildHandler())                // 21507 - 刷新建筑
        registry.register(HandMakeHandler())                    // 21509 - 手工制作
        registry.register(OneKeyCollectHandler())               // 21511 - 一键领取
        registry.register(BatchMakeHandler())                   // 21516 - 批量制作
        registry.register(ResearchTechHandler())                // 21520 - 研究科技
        registry.register(FinishTechHandler())                  // 21522 - 完成科技
        registry.register(GatherHandler())                      // 21524 - 采集
        registry.register(CancelGatherHandler())                // 21526 - 取消采集
        registry.register(CollectFragmentHandler())             // 21529 - 收集碎片
        registry.register(CancelCollectFragmentHandler())       // 21531 - 取消收集碎片
        registry.register(CollectItemHandler())                 // 21533 - 收集
        registry.register(AddMakeNumHandler())                  // 21537 - 增加制作数量
        registry.register(BatchShipOpHandler())                 // 21539 - 批量操作舰娘
        registry.register(RequestGatherListHandler())           // 21541 - 请求采集列表
        registry.register(InviteIslandShipHandler())            // 21601 - 邀请舰娘
        registry.register(DismissIslandShipHandler())           // 21603 - 送走舰娘
        registry.register(FeedIslandShipHandler())              // 21605 - 喂食舰娘
        registry.register(GiftIslandShipHandler())              // 21607 - 赠送舰娘礼物
        registry.register(ViewIslandShipHandler())              // 21609 - 查看舰娘
        registry.register(RestIslandShipHandler())              // 21611 - 休息舰娘
        registry.register(GiftIslandShipSpecificHandler())      // 21613 - 赠送特定礼物
        registry.register(DressIslandShipHandler())             // 21617 - 换装
        registry.register(ChangeIslandShipColorHandler())       // 21619 - 更换颜色
        registry.register(BuyIslandDressHandler())              // 21621 - 购买装扮
        registry.register(BuyIslandDress2Handler())             // 21624 - 购买装扮2
        registry.register(VisitorDressHandler())                // 21626 - 访客换装
        registry.register(SetDressColorHandler())               // 21628 - 设置装扮颜色
        registry.register(ShipInteractHandler())                // 21630 - 舰娘互动
        registry.register(NpcInteractHandler())                 // 21700 - NPC交互
        registry.register(NpcFeedbackHandler())                 // 21702 - NPC反馈

        // 海军学院 (22001-22305)
        registry.register(AcademyDataPushHandler())             // 22001 - 海军学院数据推送
        registry.register(ShoppingStreetHandler())              // 22101 - 商业街
        registry.register(BuyStreetGoodsHandler())             // 22103 - 购买商业街商品
        registry.register(StartSkillClassHandler())             // 22201 - 技能上课
        registry.register(CancelSkillClassHandler())            // 22203 - 取消技能课
        registry.register(FinishSkillClassHandler())            // 22205 - 加速完成技能课
        registry.register(UpgradeWellHandler())                 // 22009 - 升级油井/金井
        registry.register(FeedBookHandler())                    // 22011 - 喂书
        registry.register(UpgradeClassRoomHandler())            // 22014 - 升级教室
        registry.register(TutHandbookPushHandler())             // 22300 - 教程手册推送
        registry.register(FinishHandbookTaskHandler())          // 22302 - 完成教程任务
        registry.register(ClaimHandbookRewardHandler())         // 22304 - 领取教程奖励

        // 大舰队游戏中心 (23001-23117)
        registry.register(GetRoomListHandler())              // 23001 - 获取房间列表
        registry.register(CreateRoomHandler())               // 23003 - 创建房间
        registry.register(MatchHandler())                    // 23005 - 匹配
        registry.register(JoinRoomHandler())                 // 23007 - 加入房间
        registry.register(SwitchTeamHandler())               // 23009 - 切换队伍
        registry.register(GetLoadProgressHandler())          // 23011 - 获取加载进度
        registry.register(KickPlayerHandler())               // 23013 - 踢出玩家
        registry.register(LeaveRoomHandler())                // 23015 - 退出房间
        registry.register(InvitePlayerHandler())             // 23017 - 邀请玩家
        registry.register(ReadyHandler())                    // 23019 - 准备
        registry.register(SpectateHandler())                 // 23021 - 观战
        registry.register(RoomChatHandler())                 // 23023 - 聊天
        registry.register(GetRankingHandler())               // 23025 - 排行榜
        registry.register(LoadCompleteHandler())             // 23027 - 加载完成
        registry.register(ChangeShipHandler())               // 23029 - 更换舰船
        registry.register(CheatBarActionHandler())           // 23103 - 炸牌操作
        registry.register(CheatBarSettleHandler())           // 23106 - 炸牌结算
        registry.register(CheatBarAutoHandler())             // 23113 - 炸牌自动

        // 挑战 (24002-24100)
        registry.register(StartChallengeHandler())            // 24002 - 开始挑战
        registry.register(GetChallengeInfoHandler())          // 24004 - 查询挑战信息
        registry.register(ChallengeScorePushHandler())        // 24010 - 挑战分数推送
        registry.register(GiveUpChallengeHandler())           // 24011 - 放弃挑战
        registry.register(ChallengeSettleHandler())           // 24020 - 挑战结算
        registry.register(ClaimChallengeRewardHandler())      // 24022 - 领取挑战奖励
        registry.register(ChallengeScoreUpdateHandler())      // 24100 - 挑战分数更新

        // 指挥喵 (25002-25039)
        registry.register(OpenBoxHandler())                  // 25002 - 开箱
        registry.register(DrawMeowfficerHandler())           // 25004 - 抽取指挥喵
        registry.register(ArrangeMeowfficerHandler())        // 25006 - 编排指挥喵
        registry.register(StrengthenMeowfficerHandler())     // 25008 - 强化指挥喵
        registry.register(ResetAbilityHandler())             // 25010 - 重置技能
        registry.register(ReplaceAbilityHandler())           // 25012 - 替换技能
        registry.register(LockMeowfficerHandler())           // 25014 - 锁定/解锁指挥喵
        registry.register(SetMeowfficerFlagHandler())        // 25016 - 设置标记
        registry.register(GetMeowfficerAwardHandler())       // 25018 - 领取奖励
        registry.register(RenameMeowfficerHandler())         // 25020 - 重命名指挥喵
        registry.register(SavePresetHandler())               // 25022 - 保存预设编组
        registry.register(RenamePresetHandler())             // 25024 - 重命名预设编组
        registry.register(GetHomeInfoHandler())              // 25026 - 查询指挥喵之家
        registry.register(HarvestHomeHandler())              // 25028 - 收获指挥喵之家
        registry.register(PutMeowfficerInHomeHandler())      // 25030 - 放入家位
        registry.register(ChangeHomeStyleHandler())          // 25032 - 更换家位样式
        registry.register(GetBoxListHandler())               // 25034 - 查询箱子列表
        registry.register(SetBoxOpenStateHandler())          // 25036 - 设置开箱状态
        registry.register(BatchFinishBoxHandler())           // 25037 - 批量完成箱子
        registry.register(MeowfficerListPushHandler())       // 25039 - 指挥喵列表推送

        // 活动综合协议 (26001-26161)
        registry.register(ColoringDataPushHandler())         // 26001 - 涂色活动数据推送
        registry.register(ColoringRequestHandler())          // 26002 - 涂色活动请求
        registry.register(ColoringSubmitHandler())           // 26004 - 涂色提交
        registry.register(ColoringAwardHandler())            // 26006 - 涂色领奖
        registry.register(ColoringInfoHandler())             // 26008 - 涂色活动信息
        registry.register(AnniversaryRequestHandler())       // 26021 - 周年庆活动
        registry.register(WorldBossRequestHandler())         // 26031 - 世界Boss
        registry.register(WorldBossPointPushHandler())       // 26033 - 世界Boss积分推送
        registry.register(ActivityShopRequestHandler())      // 26041 - 活动商店
        registry.register(ActivityShopBuyHandler())          // 26043 - 活动商店购买
        registry.register(CookingInfoHandler())              // 26051 - 烹饪信息
        registry.register(CookingCraftHandler())             // 26053 - 烹饪制作
        registry.register(CookingBuffHandler())              // 26055 - 烹饪放置Buff
        registry.register(NinjaInfoHandler())                // 26060 - 忍者活动信息
        registry.register(NinjaRoleHandler())                // 26062 - 忍者角色出战
        registry.register(NinjaBuildHandler())               // 26064 - 忍者建筑升级
        registry.register(NinjaGachaHandler())               // 26066 - 忍者抽卡
        registry.register(NinjaSettleHandler())              // 26068 - 忍者结算
        registry.register(NinjaLevelHandler())               // 26070 - 忍者关卡
        registry.register(NinjaBossHandler())                // 26072 - 忍者Boss
        registry.register(Boss4thRequestHandler())           // 26081 - 四周年Boss
        registry.register(MiniGameHubListHandler())          // 26101 - 小游戏大厅列表
        registry.register(MiniGameCmdHandler())              // 26103 - 小游戏命令
        registry.register(MiniGameBatchCmdHandler())         // 26105 - 小游戏批量命令
        registry.register(MiniGameIslandHandler())           // 26106 - 活动岛屿
        registry.register(MiniGameIslandNodeHandler())       // 26108 - 岛屿节点
        registry.register(MiniGameTimeHandler())             // 26110 - 小游戏时间
        registry.register(MiniGameRankHandler())             // 26111 - 小游戏排行榜
        registry.register(MiniGameRoomPushHandler())         // 26120 - 小游戏房间推送
        registry.register(MiniGameEnterHandler())            // 26122 - 小游戏进入
        registry.register(MiniGameTimesHandler())            // 26124 - 小游戏次数
        registry.register(MiniGameSettleHandler())           // 26126 - 小游戏结算
        registry.register(MiniGameExitHandler())             // 26128 - 小游戏退出
        registry.register(FlashSaleRequestHandler())         // 26150 - 限时闪购
        registry.register(FlashSaleBuyHandler())             // 26152 - 限时闪购购买
        registry.register(FlashSaleRefreshHandler())         // 26154 - 限时闪购刷新
        registry.register(PartyRequestHandler())             // 26156 - 派对活动
        registry.register(PartyRefreshHandler())             // 26158 - 派对角色刷新
        registry.register(PartyCommonHandler())              // 26160 - 派对通用

        // 育幼系统 (27000-27050)
        registry.register(GetChildInfoHandler())             // 27000 - 获取育幼信息
        registry.register(AdvanceScheduleHandler())          // 27002 - 推进日程
        registry.register(SelectSiteOptionHandler())         // 27004 - 选择地点选项
        registry.register(CollectScheduleAwardHandler())     // 27006 - 领取日程奖励
        registry.register(ConfirmEndingHandler())            // 27008 - 结局确认
        registry.register(GetEndingListHandler())            // 27010 - 查询结局列表
        registry.register(SetSchedulePlanHandler())          // 27012 - 设置日程计划
        registry.register(TriggerEventHandler())             // 27014 - 触发事件
        registry.register(ProcessEventHandler())             // 27016 - 处理事件
        registry.register(DeleteMemoryHandler())             // 27019 - 删除记忆
        registry.register(ClaimChildTaskAwardHandler())      // 27023 - 领取任务奖励
        registry.register(TriggerSpecEventHandler())         // 27027 - 触发特殊事件
        registry.register(ResetChildHandler())               // 27029 - 重置育幼
        registry.register(RenameChildHandler())              // 27031 - 修改名字
        registry.register(BuyChildGoodsHandler())            // 27033 - 购买商品
        registry.register(CollectChildAwardHandler())        // 27035 - 领取收集奖励
        registry.register(UpdateChildProgressHandler())      // 27037 - 更新进度
        registry.register(ResetChildAttrHandler())           // 27039 - 重置属性
        registry.register(BuyEndingHandler())                // 27041 - 购买结局
        registry.register(GetChildShopHandler())             // 27043 - 查询商店
        registry.register(GetSiteOptionsHandler())           // 27045 - 查询地点选项
        registry.register(SkipAnimationHandler())            // 27047 - 跳过动画
        registry.register(BatchBuyChildGoodsHandler())       // 27049 - 批量购买

        // 公寓系统 (28001-28090)
        registry.register(EnterRoomHandler())                // 28001 - 进入房间
        registry.register(TriggerInteractionHandler())       // 28003 - 触发交互
        registry.register(CollectInteractionAwardHandler())  // 28005 - 领取互动奖励
        registry.register(PlaceFurnitureHandler())           // 28007 - 放置家具
        registry.register(GiveGiftHandler())                 // 28009 - 送礼
        registry.register(CollectCollectionHandler())        // 28011 - 收藏
        registry.register(ApartmentChangeSkinHandler())     // 28013 - 换装
        registry.register(DialogHandler())                   // 28015 - 对话
        registry.register(RenameHandler())                   // 28017 - 改名
        registry.register(VisitHandler())                    // 28019 - 拜访
        registry.register(SetShipNameHandler())              // 28021 - 设置名字
        registry.register(ApartmentTriggerEventHandler())  // 28023 - 触发事件
        registry.register(InteractHandler())                 // 28026 - 互动操作
        registry.register(CommHandler())                     // 28028 - 通讯操作
        registry.register(SetBackgroundHandler())            // 28030 - 设置背景
        registry.register(SetMoodHandler())                  // 28032 - 设置心情值
        registry.register(CommDetailHandler())               // 28034 - 通讯详情
        registry.register(GetHiddenPartsHandler())           // 28036 - 隐藏部位
        registry.register(SetHiddenPartsHandler())           // 28038 - 设置隐藏部位
        registry.register(TrackEventHandler())               // 28090 - 埋点上报

        // 大碧蓝冒险/TB系统 (29001-29127)
        registry.register(GetTbInfoHandler())               // 29001 - 获取TB信息
        registry.register(GetTbEndingsHandler())             // 29003 - 查询结局列表
        registry.register(ConfirmTbEndingHandler())          // 29005 - 确认结局
        registry.register(SetTbDifficultyHandler())          // 29007 - 设置难度
        registry.register(RenameTbHandler())                 // 29009 - 修改名字
        registry.register(StartTbGameHandler())              // 29011 - 开始游戏
        registry.register(SelectTbRankHandler())             // 29013 - 选择排名
        registry.register(GetTbChatsHandler())               // 29015 - 获取聊天
        registry.register(SelectTbChatHandler())             // 29017 - 选择聊天
        registry.register(GetTbTalentsHandler())             // 29019 - 获取天赋
        registry.register(SelectTbTalentHandler())           // 29021 - 选择天赋
        registry.register(ResetTbTalentHandler())            // 29023 - 重置天赋
        registry.register(ExecuteTbPlanHandler())            // 29025 - 执行计划
        registry.register(SettleTbHandler())                 // 29027 - 结算
        registry.register(SelectTbBranchHandler())           // 29030 - 选择分支
        registry.register(GetTbFsmHandler())                 // 29032 - 获取FSM状态
        registry.register(SetTbPlansHandler())               // 29040 - 设置计划
        registry.register(NextTbRoundHandler())              // 29042 - 执行下一轮
        registry.register(BatchTbPlanHandler())              // 29044 - 批量执行计划
        registry.register(RestTbHandler())                   // 29046 - 休息
        registry.register(GoHomeTbHandler())                 // 29048 - 回家
        registry.register(NextWeekTbHandler())               // 29050 - 下一周
        registry.register(EnterTbSiteHandler())              // 29060 - 进入地点
        registry.register(StartTbWorkHandler())              // 29062 - 开始工作
        registry.register(TriggerTbEventHandler())           // 29064 - 触发事件
        registry.register(BuyTbShopHandler())                // 29066 - 购买商品
        registry.register(MeetTbCharacterHandler())          // 29068 - 遇见角色
        registry.register(FinishTbWorkHandler())             // 29070 - 完成工作
        registry.register(GetTbShopHandler())                // 29072 - 获取商店
        registry.register(RefreshTbSiteHandler())            // 29090 - 刷新地点
        registry.register(RestartTbHandler())                // 29092 - 重新开始
        registry.register(Nin1SelectHandler())               // 29101 - nin1选择
        registry.register(Nin1ReselectHandler())             // 29103 - nin1重选
        registry.register(Nin1ConfirmHandler())              // 29105 - nin1确认
        registry.register(Nin1SkipHandler())                 // 29107 - nin1跳过
        registry.register(TarotSelectHandler())              // 29120 - 塔罗选择
        registry.register(AffixUpHandler())                  // 29122 - 词缀升级
        registry.register(EvalTbHandler())                   // 29124 - 评估
        registry.register(GetTbFsmLoginHandler())            // 29126 - 获取FSM(登录)

        // 邮件v2系统 (30002-30018)
        registry.register(GetMailListHandler())               // 30002 - 请求邮件列表
        registry.register(GetSimpleMailListHandler())         // 30004 - 请求简单邮件列表
        registry.register(MailMatchOperationHandler())        // 30006 - 邮件匹配操作
        registry.register(DeleteSingleMailHandler())       // 30008 - 删除邮件
        registry.register(OneClickOperationHandler())         // 30010 - 一键操作
        registry.register(MailExchangeHandler())              // 30012 - 邮件兑换
        registry.register(BatchIdOperationHandler())          // 30014 - 批量ID操作
        registry.register(RequestMailWithGroupHandler())      // 30016 - 请求带分组
        registry.register(RequestMailWithYearHandler())       // 30018 - 请求带年份和分组

        // 计时奖励系统 (30102-30104)
        registry.register(GetTimeRewardListHandler())         // 30102 - 请求计时奖励列表
        registry.register(ClaimTimeRewardHandler())           // 30104 - 领取计时奖励

        // 大世界系统 (33000-33602)
        registry.register(EnterWorldHandler())                // 33000 - 进入大世界
        registry.register(EnterChapterHandler())              // 33101 - 进入章节地图
        registry.register(WorldChapterActionHandler())        // 33103 - 编队行动
        registry.register(GetMapInfoHandler())                // 33106 - 查询地图信息
        registry.register(ExitChapterHandler())               // 33108 - 退出章节
        registry.register(AiCommandHandler())                 // 33110 - AI指令
        registry.register(UseStrategyHandler())               // 33112 - 使用策略
        registry.register(AcceptWorldTaskHandler())           // 33205 - 接受大世界任务
        registry.register(SubmitWorldTaskHandler())           // 33207 - 提交大世界任务
        registry.register(UseWorldItemHandler())              // 33301 - 使用世界物品
        registry.register(EnterPortHandler())                 // 33401 - 进入港口
        registry.register(BuyPortGoodsHandler())              // 33403 - 购买港口商品
        registry.register(ChangeWorldFleetHandler())          // 33405 - 变更舰队编成
        registry.register(ChangeWorldShipEquipHandler())      // 33407 - 变更舰船装备
        registry.register(SetEliteFleetHandler())             // 33409 - 设置精英舰队
        registry.register(RefreshPortTaskHandler())           // 33413 - 刷新港口任务
        registry.register(SubmitPortTaskHandler())            // 33415 - 提交港口任务
        registry.register(WorldBossActionHandler())           // 33509 - 世界Boss操作
        registry.register(FetchWorldTargetHandler())          // 33602 - 领取世界目标奖励

        // META舰船系统 (34001-34527)
        registry.register(GetMetaShipListHandler())           // 34001 - 获取META舰船列表
        registry.register(MetaShipPtRewardHandler())          // 34003 - 领取META舰船点数奖励
        registry.register(GetMetaBossDataHandler())           // 34501 - 获取META Boss数据
        registry.register(GetOtherBossListHandler())          // 34503 - 获取其他玩家Boss列表
        registry.register(GetMetaBossRankHandler())           // 34505 - 获取Boss排行榜
        registry.register(MetaBossFightHandler())             // 34509 - META Boss战斗开始
        registry.register(MetaBossFinishHandler())            // 34511 - META Boss战斗结算
        registry.register(MetaBossAutoFightHandler())         // 34513 - META Boss自动战斗
        registry.register(SetMetaBossHelpHandler())           // 34515 - 设置META Boss求助
        registry.register(GetMetaBossSimpleListHandler())     // 34517 - 获取META Boss简要列表
        registry.register(GetMetaBossSupportFleetHandler())   // 34519 - 获取META Boss支援舰队
        registry.register(SummonMetaBossHandler())            // 34521 - 召唤META Boss
        registry.register(StartMetaAutoFightHandler())        // 34523 - 开始自动战斗
        registry.register(GetMetaAutoFightDamageHandler())    // 34525 - 获取自动战斗伤害
        registry.register(FinishMetaAutoFightHandler())       // 34527 - 结束自动战斗

        // 战斗系统 (40001-40007)
        registry.register(BattleStartHandler())               // 40001 - 战斗开始
        registry.register(BattleFinishHandler())              // 40003 - 战斗结算
        registry.register(BattleQuitHandler())                // 40005 - 战斗中止
        registry.register(QuickBattleHandler())               // 40007 - 快速战斗

        // 好友/广告系统 (50001-50113)
        registry.register(SearchPlayerHandler())              // 50001 - 搜索玩家
        registry.register(SendFriendRequestHandler())         // 50003 - 发送好友请求
        registry.register(AcceptFriendRequestHandler())       // 50006 - 接受好友请求
        registry.register(RejectFriendRequestHandler())       // 50009 - 拒绝好友请求
        registry.register(RemoveFriendP50Handler())           // 50011 - 删除好友
        registry.register(GetRecommendListHandler())          // 50014 - 获取推荐好友列表
        registry.register(GetBlacklistHandler())              // 50016 - 获取黑名单
        registry.register(GetFriendInfoListHandler())         // 50018 - 批量查询好友信息
        registry.register(AdActionHandler())                  // 50102 - 广告操作
        registry.register(SendChatHandler())                  // 50105 - 发送聊天消息
        registry.register(DeleteChatHandler())                // 50107 - 删除聊天消息
        registry.register(ReadChatHandler())                  // 50109 - 标记聊天已读
        registry.register(ReportChatHandler())                // 50111 - 举报聊天消息
        registry.register(GetPlayerInfoHandler())             // 50113 - 获取玩家信息

        // 大舰队系统 (60001-60102)
        registry.register(CreateLegionHandler())              // 60001 - 创建大舰队
        registry.register(GetJoinRequestsHandler())           // 60003 - 获取加入申请列表
        registry.register(SendJoinRequestHandler())           // 60005 - 发送加入申请
        registry.register(SendLegionChatHandler())            // 60007 - 发送大舰队聊天
        registry.register(AcceptJoinRequestHandler())         // 60010 - 接受加入申请
        registry.register(SetMemberDutyHandler())             // 60012 - 设置成员职位
        registry.register(KickMemberHandler())                // 60014 - 踢出成员
        registry.register(TransferLeaderHandler())            // 60016 - 转让队长
        registry.register(QuitLegionHandler())                // 60018 - 退出大舰队
        registry.register(CancelJoinRequestHandler())         // 60020 - 取消加入申请
        registry.register(RejectJoinRequestHandler())         // 60022 - 拒绝加入申请
        registry.register(GetLegionListHandler())             // 60024 - 获取大舰队列表
        registry.register(SetLegionSettingsHandler())         // 60026 - 设置大舰队信息
        registry.register(SearchLegionHandler())              // 60028 - 搜索大舰队
        registry.register(GetLegionShopHandler())             // 60033 - 获取大舰队商店
        registry.register(BuyLegionShopItemHandler())         // 60035 - 购买大舰队商店物品
        registry.register(GetGuildInfoHandler())               // 60037 - 获取军团信息
        registry.register(GetChatHistoryHandler())            // 60100 - 获取聊天历史
        registry.register(GetUserGuildInfoHandler())          // 60102 - 获取玩家大舰队信息

        // 大舰队活动 (61001-61037)
        registry.register(SelectChapterHandler())              // 61001 - 选择活动章节
        registry.register(SetFormationHandler())               // 61003 - 设置编队
        registry.register(GetOperationHandler())               // 61005 - 获取当前活动操作数据
        registry.register(JoinEventHandler())                  // 61007 - 加入活动事件
        registry.register(GetPersonShipsHandler())             // 61009 - 获取个人舰船
        registry.register(GetFleetHandler())                   // 61011 - 获取编队信息
        registry.register(SetBossFleetHandler())               // 61013 - 设置Boss战编队
        registry.register(LegionActivityActionHandler())       // 61015 - 活动操作
        registry.register(GetReportHandler())                  // 61017 - 获取战报
        registry.register(GetRewardHandler())                  // 61019 - 领取奖励
        registry.register(GetEventHandler())                   // 61023 - 获取事件详情
        registry.register(SubmitPerformanceHandler())          // 61025 - 提交表现数据
        registry.register(GetBossEventHandler())               // 61027 - 获取Boss事件
        registry.register(GetRankHandler())                    // 61029 - 获取排行榜
        registry.register(QuitActivityHandler())               // 61031 - 退出活动
        registry.register(RecommendHandler())                  // 61033 - 推荐操作
        registry.register(LegionActivityRecommendListHandler()) // 61035 - 获取推荐列表
        registry.register(GetDamageRankHandler())              // 61037 - 获取伤害排行

        // 大舰队战 (62002-62100)
        registry.register(DonateTaskHandler())                  // 62002 - 捐献任务
        registry.register(LegionBattleDonateHandler())          // 62007 - 捐献
        registry.register(GetDonateRewardHandler())             // 62009 - 领取捐献奖励
        registry.register(GetCapitalLogHandler())               // 62011 - 获取资金日志
        registry.register(StartTechResearchHandler())           // 62013 - 开始科技研究
        registry.register(CancelTechResearchHandler())          // 62015 - 取消科技研究
        registry.register(FinishTechResearchHandler())          // 62020 - 完成科技研究
        registry.register(GetProgressHandler())                 // 62022 - 获取进度
        registry.register(GetCapitalHandler())                  // 62024 - 获取资金
        registry.register(LegionBattleGetRankHandler())         // 62029 - 获取排行榜
        registry.register(GetTechnologyHandler())               // 62100 - 获取科技列表

        // 科研系统 (63001-63015)
        registry.register(TechStartResearchHandler())            // 63001 - 开始研发
        registry.register(TechFinishResearchHandler())           // 63003 - 完成研发
        registry.register(TechAccelerateHandler())               // 63005 - 加速研发
        registry.register(TechRefreshHandler())                  // 63007 - 刷新研发列表
        registry.register(TechSetCatchupTargetHandler())         // 63009 - 设置追赶目标
        registry.register(TechSwitchCatchupVersionHandler())     // 63011 - 切换追赶版本
        registry.register(TechAbandonHandler())                  // 63013 - 放弃研发
        registry.register(TechGetCatchupRewardHandler())         // 63015 - 领取追赶奖励

        // 蓝图系统 (63200-63214)
        registry.register(StartBlueprintHandler())              // 63200 - 开始蓝图研发
        registry.register(FinishBlueprintHandler())             // 63202 - 完成蓝图研发
        registry.register(StrengthenBlueprintHandler())         // 63204 - 蓝图强化
        registry.register(AccelerateBlueprintHandler())         // 63206 - 蓝图加速
        registry.register(CancelBlueprintHandler())             // 63208 - 取消蓝图
        registry.register(SubmitBlueprintTaskHandler())         // 63210 - 提交蓝图任务
        registry.register(CatchupStrengthenHandler())           // 63212 - 蓝图追赶强化
        registry.register(UrStrengthenHandler())                // 63214 - UR蓝图强化

        // META角色系统 (63301-63319)
        registry.register(MetaRepairHandler())                  // 63301 - META角色修复
        registry.register(MetaAwakenHandler())                  // 63303 - META角色觉醒
        registry.register(MetaUnlockHandler())                  // 63305 - META角色解锁
        registry.register(MetaSkillOnHandler())                 // 63307 - 开启META技能
        registry.register(MetaSkillOffHandler())                // 63309 - 关闭META技能
        registry.register(MetaSkillSwitchHandler())             // 63311 - 切换META技能
        registry.register(MetaGetExpHandler())                  // 63313 - 领取META经验
        registry.register(MetaGetSkillInfoHandler())            // 63317 - 批量查询META技能
        registry.register(MetaSkillLevelUpHandler())            // 63319 - META技能升级

        // 舰队科技 (64001-64009)
        registry.register(FleetTechResearchHandler())            // 64001 - 研究科技
        registry.register(FleetTechAccelerateHandler())          // 64003 - 加速研究
        registry.register(FleetTechGetRewardHandler())           // 64005 - 领取科技奖励
        registry.register(FleetTechGetSetRewardHandler())        // 64007 - 领取套装奖励
        registry.register(FleetTechUpdateSetHandler())           // 64009 - 更新科技套装

        // META角色系统 (70001-70005)
        registry.register(MetaCharSetAttrHandler())              // 70001 - 设置META角色属性
        registry.register(MetaCharActionHandler())               // 70003 - META角色操作
        registry.register(MetaCharUnlockHandler())               // 70005 - META角色解锁
    }
}
