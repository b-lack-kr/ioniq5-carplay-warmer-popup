package kr.ioniq5.warmerhook;

/** Host-only model of user-close versus unexpected disappearance. It performs
 * no Android callbacks or OEM invocation and is not wired into WarmerHook. */
final class PanelLifecycle {
    enum Action { NONE, CONTINUE_STOCK, DEFER_UNTIL_SKIP_RESULT, REQUEST_STOCK_RECOVERY, UNKNOWN_SKIP_OUTCOME }
    private enum State { PREPARING, READY, SUPPRESSING, SUPPRESSED, STOCK, CLOSED, RECOVERY_REQUESTED, UNKNOWN }
    private State state = State.PREPARING;
    private boolean lostDuringSuppress;

    synchronized boolean attachedAndReady() {
        if (state != State.PREPARING) return false;
        state = State.READY; return true;
    }
    synchronized boolean beginSuppression() {
        if (state != State.READY) return false;
        state = State.SUPPRESSING; return true;
    }
    synchronized Action unexpectedLoss() {
        switch (state) {
            case PREPARING: case READY: state = State.STOCK; return Action.CONTINUE_STOCK;
            case SUPPRESSING: lostDuringSuppress = true; return Action.DEFER_UNTIL_SKIP_RESULT;
            case SUPPRESSED: state = State.RECOVERY_REQUESTED; return Action.REQUEST_STOCK_RECOVERY;
            default: return Action.NONE;
        }
    }
    synchronized Action suppressionSucceeded() {
        if (state != State.SUPPRESSING) return Action.NONE;
        if (lostDuringSuppress) { state = State.RECOVERY_REQUESTED; return Action.REQUEST_STOCK_RECOVERY; }
        state = State.SUPPRESSED; return Action.NONE;
    }
    /** Hook-engine exception does not prove whether setResult already applied. */
    synchronized Action suppressionOutcomeUnknown() {
        if (state != State.SUPPRESSING) return Action.NONE;
        state = State.UNKNOWN; return Action.UNKNOWN_SKIP_OUTCOME;
    }
    synchronized Action userClosed() {
        switch (state) {
            case PREPARING: case READY: state = State.STOCK; return Action.CONTINUE_STOCK;
            case SUPPRESSING: state = State.UNKNOWN; return Action.UNKNOWN_SKIP_OUTCOME;
            case SUPPRESSED: state = State.CLOSED; return Action.NONE;
            default: return Action.NONE;
        }
    }
}
