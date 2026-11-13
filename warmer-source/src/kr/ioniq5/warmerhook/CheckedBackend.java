package kr.ioniq5.warmerhook;

/** Wires the guard to the synchronous panel contract; no stock bypass unless
 * a separately verified provider exists. Production wiring remains disabled. */
final class CheckedBackend implements OverlayGate.Backend {
    interface Provider {
        /** Must be side-effect free and false while unconfigured. */
        boolean implementedAndReady();
        OverlayGate.Panel attach(Object owner);
    }
    private final FailClosedModeState.Access mode;
    private final Provider panel;
    CheckedBackend(FailClosedModeState.Access mode, Provider panel) { this.mode = mode; this.panel = panel; }
    public boolean conditionsHold(Object owner) {
        try { return panel != null && panel.implementedAndReady() && FailClosedModeState.safe(mode, owner); }
        catch (Throwable unavailable) { return false; }
    }
    public OverlayGate.Panel attach(Object owner) { return panel == null ? null : panel.attach(owner); }
}
