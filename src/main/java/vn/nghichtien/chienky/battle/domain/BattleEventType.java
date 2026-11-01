
package vn.nghichtien.chienky.battle.domain;

/**
 * Định nghĩa các loại sự kiện phát sinh trong trận chiến.
 */
public enum BattleEventType {

    BATTLE_START,
    BATTLE_END,

    ROUND_START,
    ROUND_END,

    ACTION_START,
    ACTION_END,

    BASIC_ATTACK,
    ACTIVE_SKILL,
    ULTIMATE_SKILL,

    DAMAGE,
    HEAL,
    SHIELD,
    DEATH,
    REVIVE,

    BUFF,
    DEBUFF,
    CC,
    DISPEL,
    IMMUNITY,

    COUNTER,
    EXTRA_ACTION,

    EVASION,
    BLOCK
}
