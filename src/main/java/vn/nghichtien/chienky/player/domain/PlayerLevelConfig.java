package vn.nghichtien.chienky.player.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Ngưỡng tổng PLAYER_EXP cần có để đạt một cấp người chơi. */
@Entity
@Table(name = "player_level_configs")
public class PlayerLevelConfig {

    @Id
    @Column(name = "player_level", nullable = false)
    private Integer playerLevel;

    @Column(name = "required_total_exp", nullable = false, precision = 24, scale = 6)
    private BigDecimal requiredTotalExp;

    protected PlayerLevelConfig() {
    }

    public PlayerLevelConfig(Integer playerLevel, BigDecimal requiredTotalExp) {
        if (playerLevel == null || playerLevel < 1
                || requiredTotalExp == null || requiredTotalExp.signum() < 0) {
            throw new IllegalArgumentException("Player level config is invalid");
        }
        this.playerLevel = playerLevel;
        this.requiredTotalExp = requiredTotalExp;
    }

    public Integer getPlayerLevel() {
        return playerLevel;
    }

    public BigDecimal getRequiredTotalExp() {
        return requiredTotalExp;
    }
}
