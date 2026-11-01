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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý vòng đời trận đấu trong bộ nhớ của ứng dụng.
 *
 * <p>Phiên bản đầu dùng đội hình người chơi thật và đội PvE được dựng từ
 * danh mục nhân vật đang bật trong database. Trạng thái trận không được lưu
 * vào database và sẽ mất khi ứng dụng khởi động lại.</p>
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
    private final Map<String, BattleSession> sessions = new ConcurrentHashMap<>();

    public BattleSessionService(
            PlayerProfileRepository playerProfileRepository,
            PlayerFormationSlotRepository formationSlotRepository,
            HeroDefinitionRepository heroDefinitionRepository,
            HeroBattleUnitFactory heroBattleUnitFactory,
            HeroStatsService heroStatsService,
            BattleTurnProcessor battleTurnProcessor
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.formationSlotRepository = formationSlotRepository;
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroBattleUnitFactory = heroBattleUnitFactory;
        this.heroStatsService = heroStatsService;
        this.battleTurnProcessor = battleTurnProcessor;
    }

    @Transactional(readOnly = true)
    public BattlePlaybackDTO startBattle(String username) {
        PlayerProfile player = playerProfileRepository
                .findByAccountUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        List<PlayerFormationSlot> slots = formationSlotRepository
                .findAllByPlayerProfileIdOrderByRowAscColumnAsc(player.getId());

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
        List<String> playerHeroCodes = new ArrayList<>();
        int maxPlayerLevel = 1;

        for (PlayerFormationSlot slot : slots) {
            var instance = slot.getHeroInstance();
            BattleUnitState unit = heroBattleUnitFactory.create(
                    instance, BattleTeam.PLAYER, slot.getRow(), slot.getColumn()
            );
            units.add(unit);
            definitions.put(instance.getHeroCode(), instance.getHeroDefinition());
            levels.put(instance.getId(), instance.getHeroLevel());
            playerHeroCodes.add(instance.getHeroCode());
            maxPlayerLevel = Math.max(maxPlayerLevel, instance.getHeroLevel());
        }

        List<HeroDefinition> availableEnemies = heroDefinitionRepository
                .findAllByEnabledTrueOrderBySortOrderAscHeroNameAsc()
                .stream()
                .filter(definition -> !playerHeroCodes.contains(definition.getHeroCode()))
                .limit(MAX_TEAM_SIZE)
                .toList();

        if (availableEnemies.isEmpty()) {
            throw new IllegalStateException(
                    "Chưa có nhân vật đối thủ được kích hoạt trong danh mục nhân vật."
            );
        }

        for (int i = 0; i < availableEnemies.size(); i++) {
            HeroDefinition definition = availableEnemies.get(i);
            HeroStatsResponse response = heroStatsService.calculateStats(
                    definition.getHeroCode(), maxPlayerLevel
            );
            Map<String, BigDecimal> stats = response.getStats();
            long enemyInstanceId = -(i + 1L);
            int[] position = ENEMY_POSITIONS[i];

            BattleUnitState enemy = new BattleUnitState(
                    enemyInstanceId,
                    BattleTeam.ENEMY,
                    definition.getHeroCode(),
                    requiredStat(stats, "HP"),
                    requiredStat(stats, "ATK"),
                    requiredStat(stats, "DEF"),
                    requiredStat(stats, "SPEED"),
                    position[0],
                    position[1]
            );
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
            levels.put(enemyInstanceId, maxPlayerLevel);
        }

        BattleState battle = new BattleState(units);
        BattleSession session = new BattleSession(
                battle, Map.copyOf(definitions), Map.copyOf(levels), username
        );
        sessions.put(battle.getBattleId(), session);
        return toPlayback(session, List.of());
    }

    public void closeBattle(String battleId, String username) {
        BattleSession session = sessions.get(battleId);
        if (session != null && session.ownerUsername.equals(username)) {
            sessions.remove(battleId, session);
        }
    }

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

            List<BattleEventDTO> allEvents =
                    session.battle.getEventCollector().getEvents();
            List<BattleEventDTO> newEvents = List.copyOf(
                    allEvents.subList(session.emittedEventCount, allEvents.size())
            );
            session.emittedEventCount = allEvents.size();
            return toPlayback(session, newEvents);
        }
    }

    private BattlePlaybackDTO toPlayback(
            BattleSession session,
            List<BattleEventDTO> events
    ) {
        return new BattlePlaybackDTO(
                BattleScreenDTO.from(
                        session.battle, session.definitions, session.levels
                ),
                events
        );
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
        private int emittedEventCount;

        private BattleSession(
                BattleState battle,
                Map<String, HeroDefinition> definitions,
                Map<Long, Integer> levels,
                String ownerUsername
        ) {
            this.battle = battle;
            this.definitions = definitions;
            this.levels = levels;
            this.ownerUsername = ownerUsername;
        }
    }
}
