package vn.nghichtien.chienky.battle.domain;

/**
 * Xác định hai đội tham gia trận chiến.
 */
public enum BattleTeam {
    PLAYER,
    ENEMY;

    /**
     * Lấy đội đối phương.
     *
     * @return Đội đối phương.
     */
    public BattleTeam opponent() {
        return this == PLAYER ? ENEMY : PLAYER;
    }
}