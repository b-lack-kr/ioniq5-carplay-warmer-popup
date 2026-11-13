package kr.ioniq5.warmerui;

/** Host-testable OEM seat cycle, no Android, vehicle I/O or command dispatch. */
public final class SeatCycle {
    public enum Mode { HEAT, VENT }
    public static final int UNKNOWN = -1;
    private static final int OFF = 2;

    private SeatCycle() { }

    /**
     * A fresh, valid OEM reading and a validated option are mandatory. Unknown
     * or stale inputs return UNKNOWN and must not produce a vehicle command.
     * ascending is the inverse of this firmware's ClimateManager.ni().
     */
    public static int next(int option, Mode mode, int current, boolean fresh, Boolean ascending) {
        if (!fresh || mode == null || ascending == null || current < OFF || current > 8) return UNKNOWN;
        final int[] cycle = cycle(option, mode, ascending.booleanValue());
        if (cycle == null) return UNKNOWN;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) return cycle[(i + 1) % cycle.length];
        }
        // A valid current state in the other mode selects this mode's first
        // state, matching the captured OEM getNextState behavior.
        return cycle[0];
    }

    private static int[] cycle(int option, Mode mode, boolean ascending) {
        final boolean heat = mode == Mode.HEAT;
        if (heat && (option == 1 || option == 5)) return ascending ? new int[]{6,8,OFF} : new int[]{8,6,OFF};
        if (heat && (option == 2 || option == 6)) return ascending ? new int[]{6,7,8,OFF} : new int[]{8,7,6,OFF};
        if (!heat && (option == 3 || option == 5)) return ascending ? new int[]{3,5,OFF} : new int[]{5,3,OFF};
        if (!heat && (option == 4 || option == 6)) return ascending ? new int[]{3,4,5,OFF} : new int[]{5,4,3,OFF};
        return null;
    }
}
