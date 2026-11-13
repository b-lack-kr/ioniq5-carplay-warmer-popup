package kr.ioniq5.warmerhook;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import kr.ioniq5.warmeripc.SingleDaemonWorker;
import kr.ioniq5.warmerui.ResearchOverlay;
/** UI adapter: Binder setup and all climate/mode reads execute on one daemon.
 * Cold or stale state returns null, leaving the original handler intact. */
final class AsyncCandidateBackend implements AsyncUiOpener.Backend {
 private final CandidateBackend base;private final SingleDaemonWorker worker=new SingleDaemonWorker(),recoveryWorker=new SingleDaemonWorker();
 private final Handler main=new Handler(Looper.getMainLooper());private boolean recovering;
 private Entry entry;private Object pending;private long generation,warmEpoch,recoveryEpoch,nextAttempt=-1;
 private static final class Entry{final Object owner,underlying;final Context context;final AsyncCandidateController control;
  Entry(Object o,Object u,Context c,AsyncCandidateController a){owner=o;underlying=u;context=c;control=a;}}
 AsyncCandidateBackend(CandidateBackend b){if(b==null)throw new IllegalArgumentException();base=b;}
 synchronized void prepare(final Object owner){
  if(owner==null||!base.enabled())return;
  if(entry!=null&&entry.owner==owner&&entry.control.alive()){entry.control.prime();return;}
  if(pending!=null||worker.busy())return;
  // A failed/unavailable session must not probe services four times a second.
  long now=SystemClock.elapsedRealtime();if(nextAttempt>=0&&now<nextAttempt)return;nextAttempt=now+1000;
  final long g=++generation;pending=owner;
  if(!worker.submit(new Runnable(){public void run(){Entry e=null;try{
   Object u=base.session(owner);Context c=base.climateContext(owner);ResearchOverlay.Controller d=u==null?null:base.controller(owner,u);
   if(u!=null&&c!=null&&d instanceof CandidateController)e=new Entry(owner,u,c,new AsyncCandidateController(owner,u,(CandidateController)d,worker,new Runnable(){public void run(){warmAfterClose(owner);}}));
  }catch(Throwable ignored){}synchronized(AsyncCandidateBackend.this){pending=null;if(g==generation&&base.enabled()){if(entry!=null)entry.control.close();entry=e;}else if(e!=null)e.control.close();}}}))pending=null;
 }
 private synchronized void warmAfterClose(final Object owner){
  final long token=++warmEpoch;
  main.postDelayed(new Runnable(){int remaining=200;public void run(){synchronized(AsyncCandidateBackend.this){
   if(token!=warmEpoch||remaining--<=0||!base.enabled())return;
   if(!recovering)prepare(owner);
   if(entry!=null&&entry.owner==owner&&entry.control.alive()&&entry.control.ready())return;
   main.postDelayed(this,50);
  }}},50);
 }
 synchronized void newRequest(Object owner){recoveryEpoch++;}
 synchronized void changed(Object listener){if(entry!=null&&base.listenerForOwner(listener,entry.owner)){entry.control.invalidate();entry.control.prime();}}
 synchronized void disable(){generation++;warmEpoch++;recoveryEpoch++;nextAttempt=-1;pending=null;if(entry!=null)entry.control.close();entry=null;}
 synchronized boolean primedFor(Object owner){return entry!=null&&entry.owner==owner&&entry.control.alive()&&entry.control.ready();}
 public synchronized Object session(Object owner){prepare(owner);boolean ok=entry!=null&&entry.owner==owner&&entry.control.alive()&&entry.control.ready();if(!ok)base.reportGate(pending!=null?"pending":entry==null?"no_entry":entry.owner!=owner?"owner_mismatch":!entry.control.alive()?"closed":"not_ready");return ok?entry:null;}
 public synchronized Context climateContext(Object owner){return entry!=null&&entry.owner==owner?entry.context:null;}
 public synchronized ResearchOverlay.Controller controller(Object owner,Object session){return session==entry&&entry.owner==owner?entry.control:null;}
 public synchronized boolean overlayAllowed(Object owner,Object session){return base.enabled()&&session==entry&&entry.owner==owner&&entry.control.ready();}
 public synchronized boolean recoveryAllowed(Object owner,Object session){return base.enabled()&&session==entry&&entry!=null&&entry.owner==owner;}
 public synchronized void committed(Object owner,Object session){if(session==entry&&entry.owner==owner)base.committed(owner,entry.underlying);}
 public void recoverOnce(final Object owner,final Object session){final Object u;final long g,start,requestEpoch;
  synchronized(this){if(!recoveryAllowed(owner,session)||recovering)return;u=entry.underlying;g=generation;requestEpoch=recoveryEpoch;start=SystemClock.elapsedRealtime();recovering=true;}
  if(!recoveryWorker.submit(new Runnable(){public void run(){boolean safe=false;try{safe=base.recoverySafeWorker(owner,u);}catch(Throwable ignored){}
   final long checked=SystemClock.elapsedRealtime();final boolean verified=safe&&checked>=start&&checked-start<=800;
   if(!main.postDelayed(new Runnable(){public void run(){
    synchronized(AsyncCandidateBackend.this){recovering=false;if(!verified||generation!=g||recoveryEpoch!=requestEpoch||!recoveryAllowed(owner,session))return;}
    base.recoverVia(owner,u,new StockRecovery.Safety(){public boolean mayShowStockNow(Object o,Object ignored){long now=SystemClock.elapsedRealtime();synchronized(AsyncCandidateBackend.this){return now>=checked&&now-checked<=800&&generation==g&&recoveryEpoch==requestEpoch&&recoveryAllowed(o,session);}}});
   }},0)){synchronized(AsyncCandidateBackend.this){recovering=false;}}
  }})){synchronized(this){recovering=false;}}
 }
}
