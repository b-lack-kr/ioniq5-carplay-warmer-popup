package kr.ioniq5.warmerhook;

import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;
import hmgapi.hook.Bridge;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import kr.ioniq5.warmerui.CallbackRegistry;

/**
 * EXPERIMENTAL PAYLOAD. Exact 260902 hashes and a default-off user setting
 * gate a candidate overlay; vehicle behavior is not verified. Not a production installer.
 */
public final class WarmerHook extends ModEntry {
    private static final String PKG="com.hkmc.telematics.service.apps.climate";
    private static final String MANAGER=PKG+".ClimateManager", LISTENER=PKG+".j";
    private static final String OPEN="climate.warmer.open", FRONT="climate.listener.front", WHEEL="climate.listener.wheel", REAR="climate.listener.rear";
    private static final String APK="/system/app/HKMC_Climate.apk", ODEX="/system/app/HKMC_Climate.odex";
    private static final String APK_SHA="09e68a1d6369824d80128a980fe00222c8ef25cdcae9db53c8dc7e13ff55167a";
    private static final String ODEX_SHA="03d71240cebdd857c4ff0045e24ac61ee03a6c78d29103690a3f75752d2f94a6";
    private volatile boolean matched, registered;
    private CallbackForwarder forwarder;
    private AsyncCacheProbe cacheProbe;
    private CachePrewarm prewarm;
    private AsyncCandidateBackend asyncBackend;
    /** Default-off candidate. Dipo foreground is not proof of a CarPlay session. */
    private AsyncUiOpener ui;

    private static String digest(String path)throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        FileInputStream in=new FileInputStream(path);
        try{byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}finally{in.close();}
        StringBuilder out=new StringBuilder();
        for(byte b:md.digest()){int v=b&255;out.append("0123456789abcdef".charAt(v>>4));out.append("0123456789abcdef".charAt(v&15));}
        return out.toString();
    }
    @Override public void zygoteInit(ModContext mod){
        try{matched=APK_SHA.equals(digest(APK))&&ODEX_SHA.equals(digest(ODEX));}
        catch(Throwable ignored){matched=false;}
        try{mod.log("WARMER_INIT fingerprint_match="+(matched?1:0));}catch(Throwable ignored){}
    }
    private static void check(Method method,String owner,String name,Class<?>[] args)throws Exception {
        if(method==null||!owner.equals(method.getDeclaringClass().getName())||!name.equals(method.getName())
                ||method.getReturnType()!=Void.TYPE||Modifier.isStatic(method.getModifiers())
                ||!java.util.Arrays.equals(args,method.getParameterTypes()))throw new NoSuchMethodException(name);
    }
    @Override public synchronized void appLoaded(final ModContext mod,LoadedApp app){
        if(app==null||!PKG.equals(app.packageName))return;
        try{mod.log("WARMER_INIT runtime_version=0.0.13");}catch(Throwable ignored){}
        boolean context=app.context!=null, process=PKG.equals(app.processName);
        try{mod.log("WARMER_INIT climate=1 fingerprint_match="+(matched?1:0)+" context="+(context?1:0)+" process="+(process?1:0)+" registered="+(registered?1:0));}catch(Throwable ignored){}
        if(!matched||registered||!context||!process)return;
        String stage="classes";
        try {
            Class<?> manager=Class.forName(MANAGER,false,app.classLoader),listener=Class.forName(LISTENER,false,app.classLoader);
            stage="methods";
            Class<?>[] types={Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE};
            Method open=manager.getDeclaredMethod("mD"),front=listener.getDeclaredMethod("onFrontSeatHeatStatusReceived",types),wheel=listener.getDeclaredMethod("onSWRCStatusReceived",types),rear=listener.getDeclaredMethod("onRearSeatHeatStatusReceived",types);
            check(open,MANAGER,"mD",new Class<?>[]{});
            Class<?>[] four={Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE};
            check(front,LISTENER,"onFrontSeatHeatStatusReceived",four);
            check(wheel,LISTENER,"onSWRCStatusReceived",four);
            check(rear,LISTENER,"onRearSeatHeatStatusReceived",four);
            final CallbackRegistry next=new CallbackRegistry(open.getDeclaringClass(),front.getDeclaringClass(),new CallbackRegistry.Clock(){
                public long elapsedMillis(){return SystemClock.elapsedRealtime();}
            });
            final CallbackForwarder input=new CallbackForwarder(next);
            final AndroidCacheSource source=new AndroidCacheSource(app.classLoader,open.getDeclaringClass());
            final AsyncCacheProbe cache=new AsyncCacheProbe(source,
                new AsyncCacheProbe.Enabled(){public boolean enabled(){return registered&&mod.enabled();}},
                new AsyncCacheProbe.Launch(){public boolean submit(Runnable r){
                    try {Thread t=new Thread(r,"warmer-cache-observe");t.setDaemon(true);t.start();return true;}
                    catch(Throwable unavailable){return false;}
                }},
                new AsyncCacheProbe.Report(){public void cacheCompared(Object owner,CacheBootstrap.Sample sample,long checkpoint){
                    boolean seeded=sample!=null&&next.seedConsistentCache(owner,sample.values(),sample.at,checkpoint);
                    mod.log("WARMER_ASYNC_RESEARCH oem_cache_equal="+(sample!=null?1:0)+" seed="+(seeded?1:0)+" freshness_unverified=1");
                }});
            final CandidateBackend base=new CandidateBackend(mod,new AndroidModeAccess(app.classLoader),source,next,open);
            final AsyncCandidateBackend async=new AsyncCandidateBackend(base);
            asyncBackend=async;
            forwarder=input;
            cacheProbe=cache;
            stage="hook_inventory";
            synchronized(Bridge.class) {
                java.lang.reflect.Field f=Bridge.class.getDeclaredField("hooked"); f.setAccessible(true);
                Object existing=f.get(null);
                if(!(existing instanceof java.util.Map))throw new IllegalStateException("hook inventory unavailable");
                java.util.Map<?,?> hooks=(java.util.Map<?,?>)existing;
                if(hooks.containsKey(open)||hooks.containsKey(front)||hooks.containsKey(wheel)||hooks.containsKey(rear))throw new IllegalStateException("conflicting hook");
            stage="register_callbacks";
            Bridge.hookMethod(front,new MethodHook(){
                protected void afterHookedMethod(Param p){if(registered&&mod.enabled()){input.front(p.thisObject,p.args,p.getThrowable()==null);async.changed(p.thisObject);}}
            });
            Bridge.hookMethod(wheel,new MethodHook(){
                protected void afterHookedMethod(Param p){if(registered&&mod.enabled()){input.wheel(p.thisObject,p.args,p.getThrowable()==null);async.changed(p.thisObject);}}
            });
            Bridge.hookMethod(rear,new MethodHook(){
                protected void afterHookedMethod(Param p){if(registered&&mod.enabled()){input.rear(p.thisObject,p.args,p.getThrowable()==null);async.changed(p.thisObject);}}
            });
            Bridge.hookMethod(open,new MethodHook(){
                protected void beforeHookedMethod(final Param p){
                    if(StockRecovery.consumeBypass(p.thisObject))return;
                    if(!registered||!mod.enabled())return;
                    // Default-off candidate; uncertainty leaves the stock handler in place.
                    async.newRequest(p.thisObject);
                    CallbackRegistry.Pair pair=next.snapshot(p.thisObject);
                    mod.log("WARMER_ASYNC_RESEARCH stock=1 status_pair="+(pair!=null?1:0));
                    cache.request(p.thisObject,next.checkpoint());
                    AsyncUiOpener opener=ui;
                    if(opener!=null)opener.open(p.thisObject,new AsyncUiOpener.HookSkip(){
                        public void skip(){if(!registered||!mod.enabled())throw new IllegalStateException("disabled");p.setResult(null);}
                    });
                }
            });
            }
            stage="start_runtime";
            ui=new AsyncUiOpener(async,1500);
            ui.setForegroundProbe(new AsyncUiOpener.ForegroundProbe(){public Boolean check(Object owner){return base.foreground(owner);}},
                new AsyncUiOpener.ForegroundExit(){public void exited(Object owner){async.newRequest(owner);}});
            registered=true;
            final Handler h=new Handler(Looper.getMainLooper());
            prewarm=new CachePrewarm(open.getDeclaringClass(),next,cache,new CachePrewarm.Scheduler(){
                public boolean postDelayed(Runnable r,long delay){return h.postDelayed(r,delay);}
                public void remove(Runnable r){h.removeCallbacks(r);}
            },new CachePrewarm.Warm(){public void prepare(Object owner){async.prepare(owner);}});
            if(mod.enabled()){input.start();prewarm.start();}
            mod.log("WARMER_ASYNC_RESEARCH callback_hooks=1 default_overlay=0");
        }catch(Throwable failure){
            registered=false;
            if(forwarder!=null)forwarder.stop();
            if(prewarm!=null)prewarm.stop();
            if(cacheProbe!=null)cacheProbe.invalidate();
            cacheProbe=null;
            forwarder=null;
            mod.log("WARMER_ASYNC_RESEARCH setup_failed=1 stock=1 stage="+stage);
        }
    }
    @Override public synchronized void onEnabled(ModContext mod,boolean enabled){
        if(prewarm!=null)prewarm.stop();
        if(cacheProbe!=null)cacheProbe.invalidate();
        if(!enabled&&ui!=null)ui.disable();
        if(!enabled&&asyncBackend!=null)asyncBackend.disable();
        if(forwarder==null)return;
        if(enabled&&registered){forwarder.start();if(prewarm!=null)prewarm.start();}else forwarder.stop();
    }
    @Override public synchronized void onSettingsChanged(ModContext mod){
        if(mod.enabled() && mod.settingBoolean("experimentalOverlay",false)){
            if(registered&&prewarm!=null)prewarm.start();
        }else{
            if(prewarm!=null)prewarm.stop();
            if(cacheProbe!=null)cacheProbe.invalidate();
            if(asyncBackend!=null)asyncBackend.disable();
            if(ui!=null)ui.disable();
        }
    }

}
