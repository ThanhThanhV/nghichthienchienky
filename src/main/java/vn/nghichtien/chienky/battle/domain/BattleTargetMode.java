package vn.nghichtien.chienky.battle.domain;

/**
 * Các chế độ lựa chọn mục tiêu của kỹ năng.
 *
 * <p>Phe địch hay đồng minh được xác định bởi loại hiệu ứng:
 * DAMAGE/DEBUFF tác động lên đối phương; HEAL/BUFF/SHIELD tác động lên đồng minh.</p>
 */
public enum BattleTargetMode {
    SELF,
    RANDOM,
    RANDOM_2,
    RANDOM_3,
    FRONT,
    FRONT_AND_MIDDLE,
    BACK,
    MOST_POPULATED_ROW,
    HIGHEST_ATK_3,
    LOWEST_HP,
    LOWEST_HP_PERCENT,
    HIGHEST_HP_PERCENT,
    HIGHEST_ATK,
    LOWEST_ATK,
    ALL_ENEMIES,
    ALL_ALLIES
}
