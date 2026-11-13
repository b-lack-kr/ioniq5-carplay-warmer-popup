package kr.ioniq5.warmerhook;

/** One bounded background cache comparison at a time; no value is logged. */
final class AsyncCacheProbe {
    interface Enabled { boolean enabled(); }
    interface Launch { boolean submit(Runnable r); }
    interface Report { void cacheCompared(Object owner,CacheBootstrap.Sample sample,long checkpoint); }
    private final CacheBootstrap.Source source;private final Enabled enabled;private final Launch launch;private final Report report;
    private boolean busy;private long generation;
    AsyncCacheProbe(CacheBootstrap.Source source,Enabled enabled,Launch launch,Report report){
        if(source==null||enabled==null||launch==null||report==null)throw new IllegalArgumentException();
        this.source=source;this.enabled=enabled;this.launch=launch;this.report=report;
    }
    synchronized boolean request(final Object owner,final long checkpoint){
        if(owner==null||busy||!safeEnabled())return false;busy=true;final long requestGeneration=generation;
        try {
            boolean scheduled=launch.submit(new Runnable(){public void run(){
                try {
                    if(!current(requestGeneration))return;
                    CacheBootstrap.Sample sample=CacheBootstrap.read(owner,source);
                    deliver(requestGeneration,owner,sample,checkpoint);
                }catch(Throwable ignored){}finally{done();}
            }});
            if(!scheduled){busy=false;return false;}return true;
        }catch(Throwable ignored){busy=false;return false;}
    }
    private boolean safeEnabled(){try{return enabled.enabled();}catch(Throwable e){return false;}}
    private synchronized boolean current(long expected){return generation==expected&&safeEnabled();}
    private synchronized void deliver(long expected,Object owner,CacheBootstrap.Sample sample,long checkpoint){
        if(generation==expected&&safeEnabled())report.cacheCompared(owner,sample,checkpoint);
    }
    synchronized void invalidate(){generation++;}
    private synchronized void done(){busy=false;}
}
