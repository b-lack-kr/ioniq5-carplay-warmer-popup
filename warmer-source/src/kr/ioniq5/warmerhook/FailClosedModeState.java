package kr.ioniq5.warmerhook;

/** Synchronous *candidate* guard. Unknown values, service replacement or any
 * exception deny the overlay. A true result does not prove a live CP session. */
final class FailClosedModeState {
    static final String DIPO = "com.daudio.app.dipo.ui.DipoActivity";
    interface Access {
        boolean onMainThread() throws Exception;
        boolean ignitionOn(Object owner) throws Exception;
        Object service() throws Exception;
        boolean alive(Object service) throws Exception;
        boolean sameService(Object service) throws Exception;
        Boolean flag(Object service, String method, String arg) throws Exception;
    }
    static boolean safe(Access access, Object owner) {
        try {return access!=null&&access.onMainThread()&&safeWorker(access,owner);}catch(Throwable ignored){return false;}
    }
    static boolean safeWorker(Access access,Object owner) {
        return diagnostic(access,owner)==0;
    }
    // Coarse non-identifying reason only; same reads, same short-circuit order.
    static int diagnostic(Access access,Object owner) {
        if (access == null || owner == null) return 1;
        try {
            if (!access.ignitionOn(owner)) return 2;
            Object s = access.service();
            if (s == null || !access.alive(s)) return 3;
            if (!Boolean.TRUE.equals(access.flag(s, "isScreenOn", null))) return 4;
            if (!Boolean.TRUE.equals(access.flag(s, "isForegroundActivity", DIPO))) return 5;
            if (!Boolean.FALSE.equals(access.flag(s, "isRearViewOn", null))) return 6;
            if (!Boolean.FALSE.equals(access.flag(s, "isCallingStatus", null))) return 7;
            if (!Boolean.FALSE.equals(access.flag(s, "isPopUpActivityShowing", null))) return 8;
            return access.alive(s) && access.sameService(s) && access.ignitionOn(owner) ? 0 : 9;
        } catch (Throwable unavailable) { return 10; }
    }
    private FailClosedModeState() { }
}
