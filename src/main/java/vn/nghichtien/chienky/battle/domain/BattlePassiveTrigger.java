package vn.nghichtien.chienky.battle.domain;

/**
 * Các thời điểm hệ thống tự động kiểm tra và kích hoạt kỹ năng nội tại.
 */
public enum BattlePassiveTrigger {
    BATTLE_START,
    ROUND_START,
    BEFORE_ACTION,
    AFTER_ACTION,
    ON_ATTACK,
    ON_DAMAGED,
    ON_KILL
}
