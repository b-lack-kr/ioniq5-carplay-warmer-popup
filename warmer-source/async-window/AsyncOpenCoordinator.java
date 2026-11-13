package kr.ioniq5.warmerasync;

/**
 * Research-only state machine for an asynchronously attached overlay.
 * A caller must serialize all methods on the same UI thread. It does not
 * implement HMG hooks, Android windows, climate controls or safety proofs.
 *
 * Only return true from begin() if the original OEM request was suppressed.
 * An attachment/first-draw callback is not itself proof that a panel is
 * displayed above CarPlay; the backend must independently verify that.
 */
public final class AsyncOpenCoordinator {
    public interface ThreadCheck { boolean onUiThread(); }
    public interface Conditions {
        /** Exact owner/session and currently safe to start/continue overlay. */
        boolean overlayAllowed(Object owner, Object session);
        /** Exact owner/session and safe to invoke the normal OEM popup. */
        boolean recoveryAllowed(Object owner, Object session);
    }
    public interface Timer {
        /** Must enqueue asynchronously on the same UI thread, never inline. */
        boolean postDelayed(Runnable task, long delayMs);
        void cancel(Runnable task);
    }
    public interface Panel {
        /** Return false or throw if addView could not be scheduled. The caller
         * owns this object before calling attach, so a partial add is disposed. */
        boolean attach(Listener listener) throws Exception;
        /** Check attached state, real visibility and valid backing controls. */
        boolean usable() throws Exception;
        /** Idempotent; may have to release a pressed steering wheel. */
        void dispose();
    }
    public interface Factory { Panel create(Object owner, Object session) throws Exception; }
    public interface Skip { void skipOriginal() throws Exception; }
    public interface Recovery { void invokeOriginalOnce(Object owner, Object session) throws Exception; }
    public interface Listener {
        void attached();
        void failed();
        void closedByUser();
    }

    private enum Phase { NEW, PREPARING, COMMITTED, VERIFIED, USER_CLOSED, FAILED, UNKNOWN }
    private final Object owner, session;
    private final ThreadCheck thread;
    private final Conditions conditions;
    private final Timer timer;
    private final Factory factory;
    private final Skip skip;
    private final Recovery recovery;
    private final long timeoutMs;
    private Phase phase=Phase.NEW;
    private Panel panel;
    private boolean timerPosted, earlyAttached, earlyFailure, earlyClose, recoveryAttempted;
    private final Runnable timeout=new Runnable(){public void run(){eventTimeout();}};
    private final Listener listener=new Listener(){
        public void attached(){eventAttached();}
        public void failed(){eventFailure();}
        public void closedByUser(){eventClose();}
    };

    public AsyncOpenCoordinator(Object owner,Object session,ThreadCheck thread,Conditions conditions,
            Timer timer,Factory factory,Skip skip,Recovery recovery,long timeoutMs) {
        if(owner==null||session==null||thread==null||conditions==null||timer==null||factory==null||skip==null||recovery==null||timeoutMs<1||timeoutMs>10000)
            throw new IllegalArgumentException("invalid dependency");
        this.owner=owner;this.session=session;this.thread=thread;this.conditions=conditions;
        this.timer=timer;this.factory=factory;this.skip=skip;this.recovery=recovery;this.timeoutMs=timeoutMs;
    }
    private boolean onThread(){try{return thread.onUiThread();}catch(Throwable e){return false;}}
    private boolean allowed(){try{return conditions.overlayAllowed(owner,session);}catch(Throwable e){return false;}}
    private void cancel(){if(timerPosted){timerPosted=false;try{timer.cancel(timeout);}catch(Throwable ignored){}}}
    private void dispose(){Panel p=panel;panel=null;if(p!=null)try{p.dispose();}catch(Throwable ignored){}}

    public boolean begin() {
        if(!onThread()||phase!=Phase.NEW||!allowed())return false;
        phase=Phase.PREPARING;
        try {
            panel=factory.create(owner,session);
            if(panel==null||!panel.attach(listener)||earlyFailure||earlyClose||!allowed())return precommitFailed();
            if(!timer.postDelayed(timeout,timeoutMs))return precommitFailed();
            timerPosted=true;
            if(!allowed())return precommitFailed();
            // If skip throws, its outcome may be ambiguous; never claim that
            // OEM was suppressed and never invoke a duplicate OEM request.
            try {skip.skipOriginal();} catch(Throwable ambiguous) {
                phase=Phase.UNKNOWN;cancel();dispose();return false;
            }
            phase=Phase.COMMITTED;
            if(earlyFailure){failCommitted();return true;}
            if(earlyClose){closeCommitted();return true;}
            if(earlyAttached)verifyCommitted();
            return true;
        } catch(Throwable unavailable){return precommitFailed();}
    }
    private boolean precommitFailed(){phase=Phase.FAILED;cancel();dispose();return false;}
    private void eventAttached(){
        if(!onThread())return;
        if(phase==Phase.PREPARING){earlyAttached=true;return;}
        if(phase==Phase.COMMITTED)verifyCommitted();
    }
    private void verifyCommitted(){
        if(phase!=Phase.COMMITTED)return;
        try{if(panel!=null&&allowed()&&panel.usable()&&allowed()){phase=Phase.VERIFIED;cancel();return;}}
        catch(Throwable ignored){}
        failCommitted();
    }
    private void eventTimeout(){if(onThread()&&phase==Phase.COMMITTED)failCommitted();}
    private void eventFailure(){
        if(!onThread())return;
        if(phase==Phase.PREPARING){earlyFailure=true;return;}
        if(phase==Phase.COMMITTED||phase==Phase.VERIFIED)failCommitted();
    }
    private void eventClose(){
        if(!onThread())return;
        if(phase==Phase.PREPARING){earlyClose=true;return;}
        if(phase==Phase.COMMITTED||phase==Phase.VERIFIED)closeCommitted();
    }
    private void closeCommitted(){phase=Phase.USER_CLOSED;cancel();dispose();}
    private void failCommitted(){
        if(phase!=Phase.COMMITTED&&phase!=Phase.VERIFIED)return;
        phase=Phase.FAILED;cancel();dispose();
        if(recoveryAttempted)return; recoveryAttempted=true;
        try {if(conditions.recoveryAllowed(owner,session))recovery.invokeOriginalOnce(owner,session);}
        catch(Throwable ignored) { /* never retry a possibly delivered OEM call */ }
    }
    /** Call for disable, owner/session replacement or unsafe window changes. */
    public void invalidate(){eventFailure();}
    public boolean isVerified(){return phase==Phase.VERIFIED;}
    public String stateForTest(){return phase.name();}
}
