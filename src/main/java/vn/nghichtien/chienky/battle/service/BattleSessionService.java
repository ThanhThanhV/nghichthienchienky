package vn.nghichtien.chienky.battle.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;
import vn.nghichtien.chienky.battle.dto.BattleEventDTO;
import vn.nghichtien.chienky.battle.dto.BattlePlaybackDTO;
import vn.nghichtien.chienky.battle.dto.BattleScreenDTO;
import vn.nghichtien.chienky.battle.engine.BattleTurnProcessor;
import vn.nghichtien.chienky.battle.engine.HeroBattleUnitFactory;
import vn.nghichtien.chienky.formation.domain.PlayerFormationSlot;
import vn.nghichtien.chienky.formation.repository.PlayerFormationSlotRepository;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.dto.HeroStatsResponse;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.service.HeroStatsService;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import vn.nghichtien.chienky.player.service.PlayerLevelProgressionService;
import vn.nghichtien.chienky.pve.domain.PlayerCampaignProgress;
import vn.nghichtien.chienky.pve.domain.PveCampaignStage;
import vn.nghichtien.chienky.pve.repository.PlayerCampaignProgressRepository;
import vn.nghichtien.chienky.pve.service.PveCampaignCatalog;
import vn.nghichtien.chienky.resource.dto.ResourceChangeRequest;
import vn.nghichtien.chienky.resource.service.ResourceTransactionService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý vòng đời trận đấu trong bộ nhớ của ứng dụng.
 * Kết quả vượt ải và phần thưởng Campaign được lưu trong database.
 */
@Service
public class BattleSessionService {

    private static final int MAX_TEAM_SIZE = 5;
    private static final int[][] ENEMY_POSITIONS = {
            {0, 1}, {1, 0}, {1, 1}, {1, 2}, {2, 1}
    };

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerFormationSlotRepository formationSlotRepository;
    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroBattleUnitFactory heroBattleUnitFactory;
    private final HeroStatsService heroStatsService;
    private final BattleTurnProcessor battleTurnProcessor;
    private final PlayerCampaignProgressRepository campaignProgressRepository;
    private final PveCampaignCatalog campaignCatalog;
    private final ResourceTransactionService resourceTransactionService;
    private final PlayerLevelProgressionService playerLevelProgressionService;
    private final Map<String, BattleSession> sessions = new ConcurrentHashMap<>();

    public BattleSessionService(
            PlayerProfileRepository playerProfileRepository,
            PlayerFormationSlotRepository formationSlotRepository,
            HeroDefinitionRepository heroDefinitionRepository,
            HeroBattleUnitFactory heroBattleUnitFactory,
            HeroStatsService heroStatsService,
            BattleTurnProcessor battleTurnProcessor,
            PlayerCampaignProgressRepository campaignProgressRepository,
            PveCampaignCatalog campaignCatalog,
            ResourceTransactionService resourceTransactionService,
            PlayerLevelProgressionService playerLevelProgressionService
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.formationSlotRepository = formationSlotRepository;
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroBattleUnitFactory = heroBattleUnitFactory;
        this.heroStatsService = heroStatsService;
        this.battleTurnProcessor = battleTurnProcessor;
        this.campaignProgressRepository = campaignProgressRepository;
        this.campaignCatalog = campaignCatalog;
        this.resourceTransactionService = resourceTransactionService;
        this.playerLevelProgressionService = playerLevelProgressionService;
    }

    @Transactional(readOnly = true)
    public BattlePlaybackDTO startBattle(String username) {
        return createBattle(username, null, null);
    }

    @Transactional(readOnly = true)
    public BattlePlaybackDTO startCampaignBattle(String username, int stageNumber) {
        PveCampaignStage stage = campaignCatalog.get(stageNumber);
        PlayerProfile player = getPlayer(username);

        int highestUnlocked = highestUnlockedStage(player.getId());

        if (stageNumber > highestUnlocked) {
            throw new IllegalStateException(
                    "Ải chưa được mở khóa. Hãy vượt qua ải trước đó."
            );
        }

        return createBattle(username, stage, null);
    }

    /** Tạo trận khiêu chiến với đội hình chính của người chơi khác. */
    @Transactional(readOnly = true)
    public BattlePlaybackDTO startPlayerChallenge(String username, Long opponentPlayerId) {
        if (opponentPlayerId == null || opponentPlayerId <= 0) {
            throw new IllegalArgumentException("Người chơi đối thủ không hợp lệ.");
        }
        return createBattle(username, null, opponentPlayerId);
    }

    /**
     * Chỉ mở ải kế tiếp trong chuỗi vượt ải liên tục; dữ liệu rời rạc không
     * được phép mở khóa vượt qua một ải chưa hoàn thành.
     */
    private int highestUnlockedStage(Long playerId) {
        int nextStage = 1;
        for (PlayerCampaignProgress progress :
                campaignProgressRepository.findAllByPlayerIdOrderByStageNumberAsc(playerId)) {
            if (progress.getStageNumber() == nextStage) {
                nextStage++;
            } else if (progress.getStageNumber() > nextStage) {
                break;
            }
        }
        return Math.min(campaignCatalog.getAll().size(), nextStage);
    }

    private BattlePlaybackDTO createBattle(
            String username,
            PveCampaignStage campaignStage,
            Long opponentPlayerId
    ) {
        PlayerProfile player = getPlayer(username);
        if (opponentPlayerId != null && player.getId().equals(opponentPlayerId)) {
            throw new IllegalArgumentException("Không thể khiêu chiến chính mình.");
        }

        List<PlayerFormationSlot> slots = formationSlotRepository
                .findAllByPlayerProfileIdOrderByRowAscColumnAsc(player.getId());
        List<PlayerFormationSlot> opponentSlots = List.of();
        if (opponentPlayerId != null) {
            PlayerProfile opponent = playerProfileRepository.findById(opponentPlayerId)
                    .filter(profile -> profile.getAccount() != null
                            && profile.getAccount().isEnabled())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy người chơi đối thủ."
                    ));
            opponentSlots = formationSlotRepository
                    .findAllByPlayerProfileIdOrderByRowAscColumnAsc(opponent.getId());
            if (opponentSlots.isEmpty() || opponentSlots.size() > MAX_TEAM_SIZE) {
                throw new IllegalStateException(
                        "Người chơi này chưa có đội hình chính hợp lệ."
                );
            }
        }

        if (slots.isEmpty()) {
            throw new IllegalStateException(
                    "Mày chưa xếp đội hình. Hãy xếp ít nhất một nhân vật trước khi chiến đấu."
            );
        }
        if (slots.size() > MAX_TEAM_SIZE) {
            throw new IllegalStateException(
                    "Đội hình chiến đấu chỉ được tối đa 5 nhân vật."
            );
        }

        List<BattleUnitState> units = new ArrayList<>();
        Map<String, HeroDefinition> definitions = new HashMap<>();
        Map<Long, Integer> levels = new HashMap<>();
        int maxPlayerLevel = 1;

        for (PlayerFormationSlot slot : slots) {
            var instance = slot.getHeroInstance();
            BattleUnitState unit = heroBattleUnitFactory.create(
                    instance, BattleTeam.PLAYER, slot.getRow(), slot.getColumn()
            );
            units.add(unit);
            definitions.put(instance.getHeroCode(), instance.getHeroDefinition());
            levels.put(instance.getId(), instance.getHeroLevel());
            maxPlayerLevel = Math.max(maxPlayerLevel, instance.getHeroLevel());
        }

        if (opponentPlayerId != null) {
            for (PlayerFormationSlot slot : opponentSlots) {
                var instance = slot.getHeroInstance();
                BattleUnitState enemy = heroBattleUnitFactory.create(
                        instance, BattleTeam.ENEMY, slot.getRow(), slot.getColumn()
                );
                units.add(enemy);
                definitions.put(instance.getHeroCode(), instance.getHeroDefinition());
                levels.put(instance.getId(), instance.getHeroLevel());
            }
        } else {
            List<HeroDefinition> enemies;
            if (campaignStage == null) {
                List<String> playerHeroCodes = slots.stream()
                        .map(slot -> slot.getHeroInstance().getHeroCode())
                        .toList();
                enemies = heroDefinitionRepository
                        .findAllByEnabledTrueOrderBySortOrderAscHeroNameAsc()
                        .stream()
                        .filter(definition -> !playerHeroCodes.contains(definition.getHeroCode()))
                        .limit(MAX_TEAM_SIZE)
                        .toList();
            } else {
                enemies = campaignStage.enemyHeroCodes().stream()
                        .map(code -> heroDefinitionRepository
                                .findByHeroCodeAndEnabled(code, true)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Thiếu cấu hình tướng địch của ải: " + code
                                )))
                        .toList();
            }

            if (enemies.isEmpty()) {
                throw new IllegalStateException(
                        "Chưa có nhân vật đối thủ được kích hoạt trong danh mục nhân vật."
                );
            }

            int enemyLevel = campaignStage == null
                    ? maxPlayerLevel
                    : maxPlayerLevel + (campaignStage.stageNumber() - 1) * 2;
            BigDecimal stageMultiplier = campaignStage == null
                    ? BigDecimal.ONE
                    : BigDecimal.ONE.add(
                            BigDecimal.valueOf(campaignStage.stageNumber() - 1L)
                                    .multiply(new BigDecimal("0.10"))
                    );

            for (int i = 0; i < enemies.size(); i++) {
                HeroDefinition definition = enemies.get(i);
                HeroStatsResponse response = heroStatsService.calculateStats(
                        definition.getHeroCode(), enemyLevel
                );
                Map<String, BigDecimal> stats = response.getStats();
                long enemyInstanceId = -(i + 1L);
                int[] position = ENEMY_POSITIONS[i];

                BattleUnitState enemy = new BattleUnitState(
                        enemyInstanceId,
                        BattleTeam.ENEMY,
                        definition.getHeroCode(),
                        scaledStat(stats, "HP", stageMultiplier),
                        scaledStat(stats, "ATK", stageMultiplier),
                        scaledStat(stats, "DEF", stageMultiplier),
                        requiredStat(stats, "SPEED"),
                        position[0],
                        position[1]
                );
                enemy.setRole(definition.getRole());
                enemy.setCombatStats(
                        statOrZero(stats, "CRIT"),
                        statOrZero(stats, "CRIT_DAMAGE"),
                        statOrZero(stats, "ACCURACY"),
                        statOrZero(stats, "RESISTANCE"),
                        statOrZero(stats, "PEN_FLAT"),
                        statOrZero(stats, "PEN_PERCENT"),
                        statOrZero(stats, "LIFESTEAL")
                );

                units.add(enemy);
                definitions.put(definition.getHeroCode(), definition);
                levels.put(enemyInstanceId, enemyLevel);
            }
        }

        BattleState battle = new BattleState(units);

        // BATTLE_START phải chạy ở round 0 ngay khi trận được tạo,
        // không đợi request advanceRound đầu tiên.
        battleTurnProcessor.initializeBattle(battle);
        List<BattleEventDTO> initialEvents = List.copyOf(
                battle.getEventCollector().getEvents()
        );

        BattleSession session = new BattleSession(
                battle,
                Map.copyOf(definitions),
                Map.copyOf(levels),
                username,
                campaignStage
        );
        // Đánh dấu event khởi tạo đã được gửi trong response startBattle,
        // tránh phát lặp lại ở lần advanceRound tiếp theo.
        session.emittedEventCount = initialEvents.size();
        sessions.put(battle.getBattleId(), session);
        return toPlayback(session, initialEvents);
    }

    public void closeBattle(String battleId, String username) {
        BattleSession session = sessions.get(battleId);
        if (session != null && session.ownerUsername.equals(username)) {
            sessions.remove(battleId, session);
        }
    }

    @Transactional
    public BattlePlaybackDTO advanceRound(String battleId, String username) {
        BattleSession session = sessions.get(battleId);
        if (session == null || !session.ownerUsername.equals(username)) {
            throw new IllegalArgumentException(
                    "Không tìm thấy trận đấu hoặc phiên chiến đấu đã hết hạn."
            );
        }

        synchronized (session) {
            if (!session.battle.isFinished()) {
                battleTurnProcessor.processRound(session.battle);
            }

            if (session.battle.isFinished()
                    && session.battle.getWinner() == BattleTeam.PLAYER
                    && session.campaignStage != null
                    && !session.rewardProcessed) {
                grantCampaignReward(session);
            }

            List<BattleEventDTO> allEvents =
                    session.battle.getEventCollector().getEvents();
            List<BattleEventDTO> newEvents = List.copyOf(
                    allEvents.subList(session.emittedEventCount, allEvents.size())
            );
            session.emittedEventCount = allEvents.size();
            return toPlayback(session, newEvents);
        }
    }

    private void grantCampaignReward(BattleSession session) {
        PveCampaignStage stage = session.campaignStage;
        PlayerProfile player = getPlayer(session.ownerUsername);

        // Khóa hồ sơ trước khi kiểm tra first-clear để tuần tự hóa các battle session
        // khác nhau của cùng người chơi. Lock được giữ đến khi transaction advanceRound commit.
        player = playerProfileRepository.findByIdForUpdate(player.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        if (campaignProgressRepository.existsByPlayerIdAndStageNumber(
                player.getId(), stage.stageNumber()
        )) {
            session.rewardProcessed = true;
            return;
        }

        campaignProgressRepository.save(
                new PlayerCampaignProgress(player, stage.stageNumber())
        );

        Map<String, BigDecimal> rewards = Map.of(
                "GOLD", stage.goldReward(),
                "PLAYER_EXP", stage.playerExpReward()
        );

        resourceTransactionService.changeResources(
                player.getId(),
                new ResourceChangeRequest(
                        "PVE_CAMPAIGN_CLEAR_" + stage.stageNumber(),
                        "PVE_CAMPAIGN",
                        (long) stage.stageNumber(),
                        "Thưởng lần đầu vượt ải " + stage.stageName(),
                        rewards
                )
        );

        // EXP được cộng theo giao dịch tài nguyên; đồng bộ cấp ngay trong cùng transaction.
        playerLevelProgressionService.refreshLevel(player.getId());

        session.campaignFirstClear = true;
        session.campaignRewards = rewards;
        session.rewardProcessed = true;
    }

    private BattlePlaybackDTO toPlayback(
            BattleSession session,
            List<BattleEventDTO> events
    ) {
        return new BattlePlaybackDTO(
                BattleScreenDTO.from(
                        session.battle,
                        session.definitions,
                        session.levels,
                        session.campaignStage == null ? null : session.campaignStage.stageNumber(),
                        session.campaignStage == null ? null : session.campaignStage.stageName(),
                        session.campaignFirstClear,
                        session.campaignRewards
                ),
                events
        );
    }

    private PlayerProfile getPlayer(String username) {
        return playerProfileRepository.findByAccountUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy hồ sơ người chơi"
                ));
    }

    private BigDecimal requiredStat(
            Map<String, BigDecimal> stats,
            String statCode
    ) {
        BigDecimal value = stats.get(statCode);
        if (value == null) {
            throw new IllegalStateException(
                    "Thiếu chỉ số bắt buộc của nhân vật: " + statCode
            );
        }
        return value;
    }

    private BigDecimal scaledStat(
            Map<String, BigDecimal> stats,
            String statCode,
            BigDecimal multiplier
    ) {
        return requiredStat(stats, statCode)
                .multiply(multiplier)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal statOrZero(
            Map<String, BigDecimal> stats,
            String statCode
    ) {
        return stats.getOrDefault(statCode, BigDecimal.ZERO);
    }

    private static final class BattleSession {
        private final BattleState battle;
        private final Map<String, HeroDefinition> definitions;
        private final Map<Long, Integer> levels;
        private final String ownerUsername;
        private final PveCampaignStage campaignStage;
        private int emittedEventCount;
        private boolean rewardProcessed;
        private boolean campaignFirstClear;
        private Map<String, BigDecimal> campaignRewards = Map.of();

        private BattleSession(
                BattleState battle,
                Map<String, HeroDefinition> definitions,
                Map<Long, Integer> levels,
                String ownerUsername,
                PveCampaignStage campaignStage
        ) {
            this.battle = battle;
            this.definitions = definitions;
            this.levels = levels;
            this.ownerUsername = ownerUsername;
            this.campaignStage = campaignStage;
        }
    }
}
