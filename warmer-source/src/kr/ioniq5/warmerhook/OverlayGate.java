package kr.ioniq5.warmerhook;

/** Research-only, synchronous fail-open decision for one OEM mD() invocation.
 * No implementation here can operate a heater or attach an Android window. */
final class OverlayGate {
    interface Panel {
        /** Must validate window attachment AND all backing controls and state. */
        boolean attachedAndUsable();
        /** Must safely dispose partially-created windows/listeners. */
        void dispose();
    }
    interface Backend {
        /** Must synchronously verify every safety condition and same owner/session. */
        boolean conditionsHold(Object manager);
        /** Must return only after a complete panel is attached; null means failure. */
        Panel attach(Object manager);
    }
    interface SkipOriginal { void skip(); }

    static boolean run(Backend backend, Object owner, boolean enabled, SkipOriginal skip) {
        if (!enabled || backend == null || owner == null || skip == null) return false;
        Panel panel = null;
        boolean suppressed = false;
        try {
            if (!backend.conditionsHold(owner)) return false;
            panel = backend.attach(owner);
            if (panel == null || !panel.attachedAndUsable() || !backend.conditionsHold(owner)) return false;
            skip.skip();
            suppressed = true;
            return true;
        } catch (Throwable unavailable) {
            return false;
        } finally {
            if (!suppressed && panel != null) try { panel.dispose(); } catch (Throwable ignored) { }
        }
    }
    private OverlayGate() { }
}
