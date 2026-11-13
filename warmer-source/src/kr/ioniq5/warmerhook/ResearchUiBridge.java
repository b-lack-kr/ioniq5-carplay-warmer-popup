package kr.ioniq5.warmerhook;

import android.content.Context;
import kr.ioniq5.warmerui.ResearchOverlay;

/** Host-testable bridge to the research UI. Not registered in WarmerHook. */
final class ResearchUiBridge implements CheckedBackend.Provider {
    private static final String CLIMATE = "com.hkmc.telematics.service.apps.climate";
    interface Factory {
        /** The factory must prove its controller/session/lifecycle are ready. */
        boolean verifiedAndReady();
        Context climateContext(Object owner);
        ResearchOverlay.Controller controller(Object owner);
    }
    private final Factory factory;
    /** Explicit host-test hook only. WarmerHook never enables this bridge. */
    static final class UiPanel implements OverlayGate.Panel {
        private final ResearchOverlay panel;
        UiPanel(ResearchOverlay panel) { this.panel = panel; }
        public boolean attachedAndUsable() { return panel.attachedAndUsable(); }
        public void dispose() { panel.dispose(); }
        void markSuppressionCommitted() { panel.markSuppressionCommitted(); }
    }
    ResearchUiBridge(Factory factory) { this.factory = factory; }
    public boolean implementedAndReady() {
        try { return factory != null && factory.verifiedAndReady(); }
        catch (Throwable ignored) { return false; }
    }
    public OverlayGate.Panel attach(Object owner) {
        ResearchOverlay attached = null;
        try {
            if (owner == null || !implementedAndReady()) return null;
            Context context = factory.climateContext(owner);
            ResearchOverlay.Controller control = factory.controller(owner);
            if (context == null || !CLIMATE.equals(context.getPackageName())
                    || control == null || !control.safeNow()) return null;
            attached = ResearchOverlay.attach(context, control);
            if (attached == null) return null;
            if (!control.safeNow() || !attached.attachedAndUsable()) { attached.dispose(); return null; }
            return new UiPanel(attached);
        } catch (Throwable ignored) {
            if (attached != null) try { attached.dispose(); } catch (Throwable ignored2) { }
            return null;
        }
    }
}
