package kr.ioniq5.warmeripc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class BoundedSnapshotCacheTest {
    static final class Clock implements BoundedSnapshotCache.Clock {long now=100;public long elapsedMillis(){return now;}}
    static final class Queue implements BoundedSnapshotCache.Submitter {
        Runnable next;boolean reject;
        public boolean submit(Runnable r){if(reject||next!=null)return false;next=r;return true;}
        void run(){Runnable x=next;next=null;if(x==null)throw new AssertionError("missing worker");x.run();}
    }
    static final class Probe implements BoundedSnapshotCache.Probe {
        int calls;boolean safe=true,throwsNow;int[] state={6,6,8,2,1,1,0};
        public BoundedSnapshotCache.Sample read(Object o,Object s)throws Exception{
            calls++;if(throwsNow)throw new Exception("failure");return new BoundedSnapshotCache.Sample(safe,state);
        }
    }
    static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void eq(int a,int b,String m){ok(a==b,m+" got="+a);}
    public static void main(String[] args)throws Exception{
        Clock clock=new Clock();Queue queue=new Queue();Probe p=new Probe();Object o=new Object(),s=new Object();
        BoundedSnapshotCache c=new BoundedSnapshotCache(clock,queue,p,7,500,250);
        ok(c.start(o,s),"start");ok(c.view(o,s)==null,"empty initially");
        ok(c.requestRefresh(o,s),"async accepted");eq(p.calls,0,"no probe on caller");
        ok(!c.requestRefresh(o,s),"single flight");ok(c.takeForAction(o,s)==null,"action blocked during probe");
        queue.run();eq(p.calls,1,"probe once");
        BoundedSnapshotCache.Sample view=c.view(o,s);ok(view!=null,"view");
        int[] copy=view.state();copy[0]=99;p.state[0]=88;
        eq(c.view(o,s).state()[0],6,"immutable across caller/probe mutation");
        ok(c.takeForAction(o,s)!=null,"first action accepted");ok(c.takeForAction(o,s)==null,"action one shot");
        p.state[0]=6;clock.now=120;ok(c.requestRefresh(o,s),"second refresh");
        ok(c.view(o,s)!=null,"last sample can paint only during refresh");
        ok(c.takeForAction(o,s)==null,"action cannot use pending stale result");queue.run();
        ok(c.takeForAction(o,s)!=null,"new action ticket");
        clock.now=700;ok(c.view(o,s)==null,"age expires from request start");
        clock.now=1000;ok(c.requestRefresh(o,s),"start delayed");clock.now=1251;queue.run();
        ok(c.view(o,s)==null,"late result never accepted");ok("no_sample_probe_timeout".equals(c.diagnosticState(o,s)),"timeout diagnostic");
        clock.now=1400;ok(c.requestRefresh(o,s),"start then unsafe");p.safe=false;queue.run();ok(c.view(o,s)==null,"unsafe denied");p.safe=true;
        clock.now=1500;ok(c.requestRefresh(o,s),"start then exception");p.throwsNow=true;queue.run();p.throwsNow=false;ok(c.view(o,s)==null,"exception denied");
        clock.now=1600;queue.reject=true;ok(!c.requestRefresh(o,s),"submission rejection");queue.reject=false;ok("no_sample_submit_rejected".equals(c.diagnosticState(o,s)),"submission diagnostic");
        clock.now=1700;ok(c.requestRefresh(o,s),"start old session");Object s2=new Object();ok(c.start(o,s2),"new session");
        ok(!c.requestRefresh(o,s2),"old worker cannot be replaced while blocked");queue.run();
        ok(c.view(o,s2)==null,"old completion not carried over");ok(c.requestRefresh(o,s2),"refresh new session");queue.run();
        ok(c.view(o,s2)!=null,"new accepted");ok(c.view(o,s)==null,"old session denied");
        ok(c.view(o,s2)!=null,"old session cannot invalidate new session");
        c.invalidate(o,s2);ok(c.view(o,s2)==null,"signal invalidates");
        clock.now=2000;ok(c.requestRefresh(o,s2),"refresh before clock anomaly");clock.now=1999;
        ok(c.view(o,s2)==null,"clock reversal denied");clock.now=2001;queue.run();ok(c.view(o,s2)==null,"clock reversal cannot resurrect");
        clock.now=3000;ok(c.requestRefresh(o,s2),"refresh then disable");c.stop();queue.run();ok(c.view(o,s2)==null,"disable blocks late result");
        ok(c.start(o,s2),"restart after stop");ok(c.requestRefresh(o,s2),"accepted after restart");queue.run();c.close();
        ok(!c.start(o,s2),"close terminal");ok(c.view(o,s2)==null,"close clears");
        blockedWorkerDoesNotBlockUi();
        System.out.println("BoundedSnapshotCache: async, expiry, single-flight, lifecycle and action tests passed");
    }
    static void blockedWorkerDoesNotBlockUi()throws Exception {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Clock c=new Clock();Object owner=new Object(),session=new Object();
        SingleDaemonWorker w=new SingleDaemonWorker();
        BoundedSnapshotCache.Probe p=new BoundedSnapshotCache.Probe(){public BoundedSnapshotCache.Sample read(Object o,Object s)throws Exception{
            entered.countDown();release.await();return new BoundedSnapshotCache.Sample(true,new int[]{1});
        }};
        BoundedSnapshotCache cache=new BoundedSnapshotCache(c,w,p,1,500,250);
        try {
            ok(cache.start(owner,session),"real worker session");ok(cache.requestRefresh(owner,session),"real worker queued");
            ok(entered.await(2,TimeUnit.SECONDS),"worker entered");
            long begin=System.nanoTime();ok(cache.view(owner,session)==null,"blocked reader unavailable");
            ok(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin)<500,"UI view must not wait for worker");
            c.now=400;ok(cache.view(owner,session)==null,"blocked worker times out");
            Object second=new Object();ok(cache.start(owner,second),"new session while blocked");
            ok(!cache.requestRefresh(owner,second),"no extra worker spawned");release.countDown();
            for(int i=0;i<100&&!cache.requestRefresh(owner,second);i++)Thread.sleep(10);
            // Any accepted old result cannot become the new session's sample.
            cache.stop();ok(cache.view(owner,second)==null,"stop remains closed");
        } finally {release.countDown();w.close();}
    }
}
