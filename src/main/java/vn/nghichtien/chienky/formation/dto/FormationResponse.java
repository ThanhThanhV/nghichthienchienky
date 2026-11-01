package vn.nghichtien.chienky.formation.dto;

import vn.nghichtien.chienky.hero.dto.HeroResponse;

import java.util.List;

/**
 * Danh sách nhân vật sở hữu và đội hình hiện tại của người chơi.
 */
public record FormationResponse(
        List<HeroResponse> heroes,
        List<FormationSlotResponse> slots
) {
}