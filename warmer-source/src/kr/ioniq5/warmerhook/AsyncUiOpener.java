package kr.ioniq5.warmerhook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import kr.ioniq5.warmeripc.SingleDaemonWorker;
import kr.ioniq5.warmerasync.AsyncOpenCoordinator;
import kr.ioniq5.warmerui.ResearchOverlay;

/** Concrete Android adapter for one asynchronous WARMER request. The caller
 * must supply a REAL verified backend; the research hook has none and never
 * instantiates this class. It is not an installable replacement. */
final class AsyncUiOpener {
    interface Backend {
        /** Null unless the current authenticated CarPlay session is known. */
        Object session(Object owner);
        Context climateContext(Object owner);
        ResearchOverlay.Controller controller(Object owner,Object session);
        boolean overlayAllowed(Object owner,Object session);
        boolean recoveryAllowed(Object owner,Object session);
        /** Must invoke stock via a verified one-shot reentry guard. */
        void recoverOnce(Object owner,Object session)throws Exception;
        void committed(Object owner,Object session);
    }
    interface HookSkip {void skip()throws Exception;}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Backend backend;private final long timeout;
    private volatile AsyncOpenCoordinator active;
    private Object activeOwner;
    interface ForegroundProbe { Boolean check(Object owner); }
    interface ForegroundExit { void exited(Object owner); }
    private ForegroundProbe foregroundProbe;
    private ForegroundExit foregroundExit;
    private final SingleDaemonWorker foregroundWorker=new SingleDaemonWorker();
    private long foregroundEpoch;
    private Runnable foregroundPoll;
    void setForegroundProbe(ForegroundProbe probe,ForegroundExit exit){foregroundProbe=probe;foregroundExit=exit;}
    private void stopForegroundWatch(){
        foregroundEpoch++;
        if(foregroundPoll!=null)handler.removeCallbacks(foregroundPoll);
        foregroundPoll=null;
    }
    private void watchForeground(final AsyncOpenCoordinator target,final Object owner){
        stopForegroundWatch();
        if(foregroundProbe==null)return;
        final long epoch=foregroundEpoch;
        final ForegroundProbe probe=foregroundProbe;
        foregroundPoll=new Runnable(){
            private boolean armed;
            private String lastState="";
            private void report(String state){if(!state.equals(lastState)){lastState=state;CandidateController.diagnostic("foreground="+state);}}
            public void run(){
                if(epoch!=foregroundEpoch||active!=target||target.isTerminal())return;
                if(!foregroundWorker.busy())foregroundWorker.submit(new Runnable(){public void run(){
                    final long started=SystemClock.elapsedRealtime();Boolean result=null;
                    try{result=probe.check(owner);}catch(Throwable ignored){}
                    final Boolean visible=result;final long finished=SystemClock.elapsedRealtime();
                    handler.postDelayed(new Runnable(){public void run(){
                        if(epoch!=foregroundEpoch||active!=target)return;
                        long now=SystemClock.elapsedRealtime();
                        if(finished<started||finished-started>250||now<finished||now-finished>250){report("stale");return;}
                        if(Boolean.TRUE.equals(visible)){if(!target.isTerminal()){armed=true;report("carplay");}return;}
                        if(visible==null){report("unknown");return;}
                        // Observe Dipo while this overlay is attached before
                        // interpreting false as departure. A platform that
                        // reports the overlay itself as foreground is not
                        // allowed to close every new popup unconditionally.
                        if(!armed){report("unarmed_other");return;}
                        report("left_carplay");
                        // Invalidate any queued backend recovery before the
                        // coordinator cancels its attachment timer/disposes.
                        try{if(foregroundExit!=null)foregroundExit.exited(owner);}catch(Throwable ignored){}
                        target.dismissWithoutRecovery();active=null;activeOwner=null;stopForegroundWatch();
                    }},0);
                }});
                handler.postDelayed(this,100);
            }
        };
        handler.postDelayed(foregroundPoll,100);
    }
    AsyncUiOpener(Backend backend,long timeout){if(backend==null||timeout<1||timeout>10000)throw new IllegalArgumentException();this.backend=backend;this.timeout=timeout;}
    boolean open(final Object owner,final HookSkip hookSkip){
        if(owner==null||hookSkip==null||Looper.myLooper()!=Looper.getMainLooper())return false;
        if(active!=null&&!active.isTerminal()) {
            // A second physical WARMER press intentionally dismisses the
            // current overlay. Do not consult the now-stale seat cache or
            // start another session: either can fall through to stock/home.
            if(activeOwner==owner) {
                try { hookSkip.skip(); } catch(Throwable ambiguous) { return false; }
                stopForegroundWatch();active.dismissWithoutRecovery();
                active=null;activeOwner=null;
                return true;
            }
            stopForegroundWatch();active.dismissWithoutRecovery();
        }
        active=null;activeOwner=null;
        try {
            final Object session=backend.session(owner);if(session==null)return false;
            final Context context=backend.climateContext(owner);final ResearchOverlay.Controller controller=backend.controller(owner,session);
            if(context==null||controller==null||!backend.overlayAllowed(owner,session))return false;
            final AsyncUiPanel panel=new AsyncUiPanel(context,controller,timeout);
            AsyncOpenCoordinator c=new AsyncOpenCoordinator(owner,session,
                new AsyncOpenCoordinator.ThreadCheck(){public boolean onUiThread(){return Looper.myLooper()==Looper.getMainLooper();}},
                new AsyncOpenCoordinator.Conditions(){
                    public boolean overlayAllowed(Object o,Object s){return backend.overlayAllowed(o,s);}
                    public boolean recoveryAllowed(Object o,Object s){return backend.recoveryAllowed(o,s);}
                },
                new AsyncOpenCoordinator.Timer(){
                    public boolean postDelayed(Runnable r,long ms){return handler.postDelayed(r,ms);}
                    public void cancel(Runnable r){handler.removeCallbacks(r);}
                },
                new AsyncOpenCoordinator.Factory(){public AsyncOpenCoordinator.Panel create(Object o,Object s){return panel;}},
                new AsyncOpenCoordinator.Skip(){public void skipOriginal()throws Exception{hookSkip.skip();backend.committed(owner,session);panel.markSuppressionCommitted();}},
                new AsyncOpenCoordinator.Recovery(){public void invokeOriginalOnce(Object o,Object s)throws Exception{backend.recoverOnce(o,s);}},timeout);
            active=c;activeOwner=owner;boolean intercepted=c.begin();if(!intercepted){active=null;activeOwner=null;}else watchForeground(c,owner);return intercepted;
        }catch(Throwable unavailable){active=null;activeOwner=null;return false;}
    }
    void disable(){
        final AsyncOpenCoordinator target=active;
        if(target==null)return;
        Runnable close=new Runnable(){public void run(){
            if(active==target)stopForegroundWatch();
            target.dismissWithoutRecovery();
            if(active==target){active=null;activeOwner=null;}
        }};
        if(Looper.myLooper()==Looper.getMainLooper())close.run();
        else handler.postDelayed(close,0);
    }
    void invalidate(){if(active!=null){active.invalidate();if(active.isTerminal()){stopForegroundWatch();active=null;activeOwner=null;}}}
}
