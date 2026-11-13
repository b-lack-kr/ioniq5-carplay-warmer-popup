package kr.ioniq5.warmerhook;

/** Research-only one-shot recovery coordinator. No UI or vehicle is wired to it.
 * Invocation success means only that the test/hook invocation returned; it
 * does not prove the OEM popup appeared or that another mod allowed it. */
final class StockRecovery {
    enum Outcome { NONE, DENIED, CANCELLED, INVOKED_UNVERIFIED, HOOK_NOT_OBSERVED, FAILED }
    interface Safety {
        /** Must prove main thread, same still-live session/owner, ignition on,
         * screen on, no camera, call or competing popup. Unknown means false. */
        boolean mayShowStockNow(Object owner, Object session);
    }
    interface Invoker { void invoke(Object owner) throws Throwable; }
    private enum State { PENDING, SUPPRESSED, CLAIMED, CLOSED, FINISHED }
    private static final class Permit {
        final Object owner; boolean consumed;
        Permit(Object owner) { this.owner = owner; }
    }
    private static final ThreadLocal<Permit> BYPASS = new ThreadLocal<Permit>();
    private final Object owner, session, panel;
    private State state = State.PENDING;
    StockRecovery(Object owner, Object session, Object panel) {
        if (owner == null || session == null || panel == null) throw new IllegalArgumentException("identity");
        this.owner = owner; this.session = session; this.panel = panel;
    }
    /** Caller may commit only after the hook actually set a replace result. */
    synchronized boolean suppressionCommitted(Object owner, Object session, Object panel) {
        if (state != State.PENDING || this.owner != owner || this.session != session || this.panel != panel) return false;
        state = State.SUPPRESSED; return true;
    }
    synchronized void normalClose(Object panel) {
        if (this.panel == panel && state != State.FINISHED) state = State.CLOSED;
    }
    /** Called by the hooked mD callback before considering any suppression. A
     * permit is single-use, thread-local, and matches the exact manager object. */
    static boolean consumeBypass(Object owner) {
        Permit p = BYPASS.get();
        if (p == null || p.consumed || p.owner != owner) return false;
        p.consumed = true; return true;
    }
    Outcome unexpectedLoss(Object owner, Object session, Object panel, Safety safety, Invoker invoker) {
        synchronized (this) {
            if (this.owner != owner || this.session != session || this.panel != panel || state != State.SUPPRESSED) return Outcome.NONE;
            state = State.CLAIMED; // consume first, including failure/exception; no unattended retries
        }
        try {
            if (safety == null || invoker == null || !safety.mayShowStockNow(owner, session)) return Outcome.DENIED;
            synchronized (this) { if (state == State.CLOSED) return Outcome.CANCELLED; }
            if (!safety.mayShowStockNow(owner, session)) return Outcome.DENIED;
            synchronized (this) { if (state == State.CLOSED) return Outcome.CANCELLED; }
            if (BYPASS.get() != null) return Outcome.DENIED; // no nested permit overwrite
            Permit permit = new Permit(owner);
            BYPASS.set(permit);
            try {
                invoker.invoke(owner);
                return permit.consumed ? Outcome.INVOKED_UNVERIFIED : Outcome.HOOK_NOT_OBSERVED;
            } finally { BYPASS.remove(); }
        } catch (Throwable e) { return Outcome.FAILED; }
        finally { synchronized (this) { state = State.FINISHED; } }
    }
}
