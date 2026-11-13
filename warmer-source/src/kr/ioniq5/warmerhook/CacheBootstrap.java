package kr.ioniq5.warmerhook;

/**
 * Compares a pinned ClimateManager's cached values to the USM server's cache.
 * This is useful for matching the stock popup at startup before a change-only
 * callback arrives. It is NOT evidence of a fresh MICOM/actuator reading.
 * It never writes the vehicle, opens a window, or suppresses stock behavior.
 */
final class CacheBootstrap {
    interface Source {
        /** Returns a token for the exact live USM binder, or null. */
        Object open() throws Exception;
        /** Must check binder alive and identical current service. */
        boolean unchanged(Object token) throws Exception;
        /** Must use the exact manager object and fixed seven-int whitelist. */
        int[] managerValues(Object owner) throws Exception;
        /** Must call the seven matching IUSMService cache getters. */
        int[] serviceValues(Object token) throws Exception;
        long elapsedMillis();
    }
    static final class Sample {
        final long at; private final int[] data;
        private Sample(long at,int[] data){this.at=at;this.data=data.clone();}
        int[] values(){return data.clone();}
    }
    private static boolean valid(int[] a){
        return a!=null&&a.length==7&&a[0]>=1&&a[0]<=6&&a[1]>=1&&a[1]<=6
            &&a[2]>=2&&a[2]<=8&&a[3]>=2&&a[3]<=8
            &&a[4]>=0&&a[4]<=1&&a[5]>=0&&a[5]<=1&&a[6]>=0&&a[6]<=1;
    }
    static Sample read(Object owner,Source source){
        if(owner==null||source==null)return null;
        try {
            Object binder=source.open();if(binder==null||!source.unchanged(binder))return null;
            long start=source.elapsedMillis();if(start<0)return null;
            int[] manager1=source.managerValues(owner),service1=source.serviceValues(binder);
            if(!valid(manager1)||!valid(service1)||!java.util.Arrays.equals(manager1,service1))return null;
            int[] manager2=source.managerValues(owner),service2=source.serviceValues(binder);
            long end=source.elapsedMillis();
            if(end<start||!valid(manager2)||!valid(service2)||!java.util.Arrays.equals(manager1,manager2)
                    ||!java.util.Arrays.equals(manager1,service2)||!source.unchanged(binder))return null;
            return new Sample(end,manager2);
        }catch(Throwable unavailable){return null;}
    }
    private CacheBootstrap(){}
}
