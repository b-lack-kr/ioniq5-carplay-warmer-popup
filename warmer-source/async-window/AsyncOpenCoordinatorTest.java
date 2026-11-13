package kr.ioniq5.warmerasync;

/** Deterministic event-order tests. No Android or vehicle calls. */
public final class AsyncOpenCoordinatorTest {
    static final class Rig implements AsyncOpenCoordinator.ThreadCheck,AsyncOpenCoordinator.Conditions,
            AsyncOpenCoordinator.Timer,AsyncOpenCoordinator.Factory,AsyncOpenCoordinator.Panel,
            AsyncOpenCoordinator.Skip,AsyncOpenCoordinator.Recovery {
        final Object owner=new Object(),session=new Object();
        boolean thread=true,allow=true,recover=true,attach=true,usable=true,post=true;
        boolean throwAttach,throwSkip,earlyAttach,earlyFail,earlyClose;
        int attachCount,disposeCount,skipCount,recoveryCount;Runnable timeout;
        AsyncOpenCoordinator.Listener listener;
        public boolean onUiThread(){return thread;}
        public boolean overlayAllowed(Object o,Object s){return o==owner&&s==session&&allow;}
        public boolean recoveryAllowed(Object o,Object s){return o==owner&&s==session&&recover;}
        public boolean postDelayed(Runnable r,long ms){if(!post)return false;timeout=r;return true;}
        public void cancel(Runnable r){if(timeout==r)timeout=null;}
        public AsyncOpenCoordinator.Panel create(Object o,Object s){return this;}
        public boolean attach(AsyncOpenCoordinator.Listener l){attachCount++;listener=l;
            if(earlyAttach)l.attached();if(earlyFail)l.failed();if(earlyClose)l.closedByUser();
            if(throwAttach)throw new IllegalStateException();return attach;}
        public boolean usable(){return usable;}
        public void dispose(){disposeCount++;}
        public void skipOriginal(){skipCount++;if(throwSkip)throw new IllegalStateException();}
        public void invokeOriginalOnce(Object o,Object s){if(o!=owner||s!=session)throw new AssertionError();recoveryCount++;}
        AsyncOpenCoordinator open(){return new AsyncOpenCoordinator(owner,session,this,this,this,this,this,this,500);}
    }
    static void ok(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    public static void main(String[] args){
        Rig a=new Rig();AsyncOpenCoordinator x=a.open();
        ok(x.begin()&&a.skipCount==1&&!x.isVerified(),"addView alone must not imply attached");
        a.listener.attached();ok(x.isVerified()&&a.timeout==null&&a.recoveryCount==0,"first frame verifies later");
        a.listener.closedByUser();a.listener.failed();ok(a.disposeCount==1&&a.recoveryCount==0,"normal close never reopens");

        Rig b=new Rig();AsyncOpenCoordinator y=b.open();ok(y.begin(),"timeout setup");Runnable old=b.timeout;old.run();old.run();
        ok(b.recoveryCount==1&&b.disposeCount==1,"timeout restores exactly once");b.listener.attached();ok(!y.isVerified(),"late attach ignored");

        Rig c=new Rig();c.throwAttach=true;AsyncOpenCoordinator z=c.open();ok(!z.begin()&&c.skipCount==0&&c.disposeCount==1,"partial add returns stock");
        Rig d=new Rig();d.post=false;ok(!d.open().begin()&&d.skipCount==0&&d.disposeCount==1,"timer failure returns stock");
        Rig e=new Rig();e.earlyAttach=true;AsyncOpenCoordinator early=e.open();ok(early.begin()&&early.isVerified(),"inline ready deferred until commit");
        Rig f=new Rig();f.earlyFail=true;ok(!f.open().begin()&&f.skipCount==0&&f.disposeCount==1,"early failure stays stock");
        Rig g=new Rig();g.earlyClose=true;ok(!g.open().begin()&&g.skipCount==0,"early close stays stock");
        Rig h=new Rig();h.throwSkip=true;AsyncOpenCoordinator ambiguous=h.open();ok(!ambiguous.begin()&&ambiguous.stateForTest().equals("UNKNOWN")&&h.recoveryCount==0,"ambiguous skip never double invokes");
        Rig i=new Rig();AsyncOpenCoordinator unsafe=i.open();ok(unsafe.begin(),"unsafe setup");i.allow=false;i.recover=false;i.listener.attached();
        ok(i.recoveryCount==0&&i.disposeCount==1,"camera/call unsafe cannot force stock");i.recover=true;i.listener.failed();ok(i.recoveryCount==0,"no delayed surprise");
        Rig j=new Rig();AsyncOpenCoordinator loss=j.open();ok(loss.begin(),"loss setup");j.listener.attached();loss.invalidate();
        ok(j.recoveryCount==1&&j.disposeCount==1,"session invalidation recovers at most once if independently allowed");
        Rig k=new Rig();k.thread=false;ok(!k.open().begin()&&k.attachCount==0&&k.skipCount==0,"wrong thread never intercepts");
        Rig l=new Rig();AsyncOpenCoordinator unusable=l.open();ok(unusable.begin(),"unusable setup");l.usable=false;l.listener.attached();ok(l.recoveryCount==1,"failed visibility restores stock");
        System.out.println("AsyncOpenCoordinator event-order tests passed; no Android or vehicle I/O");
    }
}
