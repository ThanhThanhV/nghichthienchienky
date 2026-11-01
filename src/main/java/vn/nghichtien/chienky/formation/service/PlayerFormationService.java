package vn.nghichtien.chienky.formation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.formation.domain.PlayerFormationSlot;
import vn.nghichtien.chienky.formation.dto.FormationResponse;
import vn.nghichtien.chienky.formation.dto.FormationSlotRequest;
import vn.nghichtien.chienky.formation.dto.FormationSlotResponse;
import vn.nghichtien.chienky.formation.dto.UpdateFormationRequest;
import vn.nghichtien.chienky.formation.repository.PlayerFormationSlotRepository;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.dto.HeroResponse;
import vn.nghichtien.chienky.hero.repository.HeroInstanceRepository;
import vn.nghichtien.chienky.hero.service.HeroService;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import vn.nghichtien.chienky.player.service.PlayerInitializationService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quản lý đội hình 3x3 của người chơi.
 */
@Service
public class PlayerFormationService {

    private static final int MAX_TEAM_SIZE = 5;

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerFormationSlotRepository formationSlotRepository;
    private final HeroInstanceRepository heroInstanceRepository;
    private final HeroService heroService;
    private final PlayerInitializationService playerInitializationService;

    public PlayerFormationService(
            PlayerProfileRepository playerProfileRepository,
            PlayerFormationSlotRepository formationSlotRepository,
            HeroInstanceRepository heroInstanceRepository,
            HeroService heroService,
            PlayerInitializationService playerInitializationService
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.formationSlotRepository = formationSlotRepository;
        this.heroInstanceRepository = heroInstanceRepository;
        this.heroService = heroService;
        this.playerInitializationService = playerInitializationService;
    }

    /**
     * Lấy toàn bộ nhân vật sở hữu và đội hình hiện tại.
     */
    @Transactional
    public FormationResponse getMyFormation(String username) {
        PlayerProfile player = getPlayer(username);

        // Tài khoản cũ chưa có nhân vật hoặc đội hình sẽ được khởi tạo một lần.
        playerInitializationService.ensureStarterFormation(player.getId());

        return buildResponse(username, player.getId());
    }

    /**
     * Thay thế đội hình hiện tại bằng danh sách vị trí mới.
     */
    @Transactional
    public FormationResponse updateMyFormation(
            String username,
            UpdateFormationRequest request
    ) {
        if (request == null || request.slots() == null
                || request.slots().isEmpty()
                || request.slots().size() > MAX_TEAM_SIZE) {
            throw new IllegalArgumentException(
                    "Đội hình phải có từ 1 đến 5 nhân vật."
            );
        }

        PlayerProfile player = getPlayer(username);
        playerInitializationService.ensureStarterFormation(player.getId());

        List<HeroInstance> ownedHeroes =
                heroInstanceRepository.findAllByPlayerProfileIdOrderByObtainedAtDesc(
                        player.getId()
                );

        Map<Long, HeroInstance> ownedById = new HashMap<>();
        for (HeroInstance hero : ownedHeroes) {
            ownedById.put(hero.getId(), hero);
        }

        Set<Long> selectedHeroIds = new HashSet<>();
        Set<String> occupiedPositions = new HashSet<>();
        List<PlayerFormationSlot> newSlots = new ArrayList<>();

        for (FormationSlotRequest slot : request.slots()) {
            validateSlot(slot);

            if (!selectedHeroIds.add(slot.instanceId())) {
                throw new IllegalArgumentException(
                        "Không thể xếp trùng một nhân vật trong đội hình."
                );
            }

            String position = slot.row() + ":" + slot.column();
            if (!occupiedPositions.add(position)) {
                throw new IllegalArgumentException(
                        "Một vị trí không thể chứa nhiều hơn một nhân vật."
                );
            }

            HeroInstance hero = ownedById.get(slot.instanceId());
            if (hero == null) {
                throw new IllegalArgumentException(
                        "Nhân vật không tồn tại hoặc không thuộc tài khoản hiện tại: "
                                + slot.instanceId()
                );
            }

            newSlots.add(new PlayerFormationSlot(
                    player,
                    hero,
                    slot.row(),
                    slot.column()
            ));
        }

        // Xóa đội hình cũ và flush trước khi thêm để không vi phạm unique constraint.
        formationSlotRepository.deleteAllByPlayerProfileId(player.getId());
        formationSlotRepository.flush();
        formationSlotRepository.saveAll(newSlots);

        return buildResponse(username, player.getId());
    }

    private void validateSlot(FormationSlotRequest slot) {
        if (slot == null || slot.instanceId() == null || slot.instanceId() <= 0
                || slot.row() == null || slot.row() < 0 || slot.row() > 2
                || slot.column() == null || slot.column() < 0 || slot.column() > 2) {
            throw new IllegalArgumentException(
                    "Vị trí đội hình không hợp lệ. Hàng và cột phải nằm trong khoảng 0..2."
            );
        }
    }

    private FormationResponse buildResponse(String username, Long playerId) {
        List<HeroResponse> heroes = heroService.getMyHeroes(username);

        List<FormationSlotResponse> slots = formationSlotRepository
                .findAllByPlayerProfileIdOrderByRowAscColumnAsc(playerId)
                .stream()
                .map(slot -> new FormationSlotResponse(
                        slot.getHeroInstance().getId(),
                        slot.getRow(),
                        slot.getColumn()
                ))
                .toList();

        return new FormationResponse(heroes, slots);
    }

    private PlayerProfile getPlayer(String username) {
        return playerProfileRepository.findByAccountUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy hồ sơ người chơi"
                ));
    }
}