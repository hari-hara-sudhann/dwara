package me.hari.dwara.entities.enums;

/**
 * Direction the app declares for a gate action (spec §3.3/§3.5, interview
 * round 6). The app UI only offers the intent that matches the session state,
 * and the backend re-checks it against that state.
 */
public enum GateIntent {
    ENTRY,
    EXIT
}
