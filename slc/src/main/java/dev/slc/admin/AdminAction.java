package dev.slc.admin;

/** Every button is translated into one explicit, server-authorized action. */
public enum AdminAction {
    ROUND_START,
    ROUND_RESTART,
    ROUND_END_DRAW,
    ROUND_END_FOUNDATION,
    ROUND_END_CHAOS,
    ROUND_END_SCP,
    FORCE_MTF_WAVE,
    FORCE_CHAOS_WAVE,
    SET_ROLE,
    TOGGLE_GOD,
    TOGGLE_NOCLIP,
    GIVE_ITEM,
    KICK,
    BAN,
    KIT_REQUEST,
    KIT_SAVE_HAND,
    KIT_CLEAR,
    GIVE_DUMMY_SPAWNER,
    GIVE_ZONE_MARKER,
    GIVE_079_MAP
}
