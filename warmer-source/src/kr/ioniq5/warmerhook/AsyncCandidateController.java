package kr.ioniq5.warmerhook;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import kr.ioniq5.warmeripc.BoundedSnapshotCache;
import kr.ioniq5.warmeripc.SingleDaemonWorker;
import kr.ioniq5.warmeripc.SerialIpcDispatcher;
import kr.ioniq5.warmerui.ResearchOverlay;
/** Async UI-facing adapter. CandidateController is used only on the worker.
 * 1000ms TTL and 800ms timeout are experimental fail-closed budgets, not
 * measured guarantees. A returned command result means scheduling only. */
final class AsyncCandidateController implements ResearchOverlay.Controller {
 private final Object owner,session;private final CandidateController delegate;
 private final SingleDaemonWorker worker;private final Runnable closedCallback;private final BoundedSnapshotCache cache;private final SerialIpcDispatcher commands;
 private final Handler releaseHandler=new Handler(Looper.getMainLooper());
 private int releaseRetryRemaining;
 private final Runnable releaseRetry=new Runnable(){public void run(){
  if(!commands.unsentReleasePending())return;
  if(commands.release())return;
  if(commands.unsentReleasePending()&&releaseRetryRemaining-->0)releaseHandler.postDelayed(this,100);
  else CandidateController.diagnostic("wheel_release=queue_unavailable");
 }};
 private boolean scheduleUnsentReleaseRetry(){
  if(!commands.unsentReleasePending())return false;
  releaseHandler.removeCallbacks(releaseRetry);releaseRetryRemaining=20;
  if(releaseHandler.postDelayed(releaseRetry,100))return true;
  CandidateController.diagnostic("wheel_release=retry_schedule_failed");return false;
 }
 private volatile ResearchOverlay.Snapshot last,lastPaint;private volatile boolean active=true,panelClosed,dirty;private volatile long paintAt=-1;private volatile long revision,actionRevision;private long lastRefresh=-1;
 AsyncCandidateController(final Object o,final Object s,final CandidateController d,final SingleDaemonWorker w,final Runnable onClosed){owner=o;session=s;delegate=d;worker=w;closedCallback=onClosed;
  cache=new BoundedSnapshotCache(new BoundedSnapshotCache.Clock(){public long elapsedMillis(){return SystemClock.elapsedRealtime();}},w,
   new BoundedSnapshotCache.Probe(){public BoundedSnapshotCache.Sample read(Object a,Object b){ResearchOverlay.Snapshot v=delegate.snapshot();if(v==null||!delegate.snapshotValidated(v))return null;return new BoundedSnapshotCache.Sample(true,pack(v));}},12,1000,800);
  cache.start(o,s);
  commands=new SerialIpcDispatcher(new SerialIpcDispatcher.Worker(){public boolean submit(Runnable r){return worker.submit(r);}},
   new SerialIpcDispatcher.Gate(){public boolean maySend(){return active&&!panelClosed&&revision==actionRevision&&cache.view(owner,session)!=null&&delegate.safeNow();}},
   new SerialIpcDispatcher.Transport(){public void seat(int i,int target)throws Exception{boolean ok=i<2?delegate.setSeat(i==0,target):delegate.setRearSeat(i==2||i==4,i<4,target);if(!ok)throw new IllegalStateException("seat transport unavailable");}
    public void wheelDown()throws Exception{if(!delegate.wheelPress())throw new IllegalStateException("down unavailable");}
    public void wheelUp()throws Exception{if(!delegate.wheelRelease())throw new IllegalStateException("up unavailable");}},
   new SerialIpcDispatcher.Observer(){public void result(String op,boolean ok){if("wheel_down".equals(op)||"wheel_up".equals(op)||"down".equals(op)||"up".equals(op))CandidateController.diagnostic("wheel_transport="+op+"_"+(ok?"returned":"failed")); /* Not a hardware ACK. */}});
 }
 private static int[] pack(ResearchOverlay.Snapshot s){int[] r=s.rear;return new int[]{s.driverOption,s.passengerOption,s.driverState,s.passengerState,s.wheelSupported?1:0,s.wheelOn?1:0,s.ascending?1:0,r==null?0:1,r==null?0:r[0],r==null?0:r[1],r==null?0:r[2],r==null?0:r[3]};}
 private static ResearchOverlay.Snapshot unpack(int[] a){if(a==null||a.length!=12)return null;return new ResearchOverlay.Snapshot(SystemClock.elapsedRealtime(),a[0],a[1],a[2],a[3],a[4]==1,a[5]==1,a[6]==1,a[7]==0?null:new int[]{a[8],a[9],a[10],a[11]});}
 void prime(){if(!active||commands.busy()||worker.busy())return;long now=SystemClock.elapsedRealtime();if(lastRefresh>=0&&now>=lastRefresh&&now-lastRefresh<600)return;if(cache.requestRefresh(owner,session))lastRefresh=now;}
 boolean alive(){return active&&!panelClosed;}
 boolean ready(){return active&&cache.view(owner,session)!=null;}
 private boolean paintGrace(){long now=SystemClock.elapsedRealtime();return active&&!panelClosed&&dirty&&lastPaint!=null&&paintAt>=0&&now>=paintAt&&now-paintAt<=800;}
 public boolean safeNow(){if(ready())return true;prime();boolean grace=paintGrace();if(!grace)CandidateController.diagnostic("async_safe_reject="+cache.diagnosticState(owner,session)+(commands.busy()?"_command_busy":worker.busy()?"_worker_busy":""));return grace;}
 public boolean overlayVerified(View root){return root!=null&&root.getWindowToken()!=null&&root.isShown()&&ready();}
 public ResearchOverlay.Snapshot snapshot(){BoundedSnapshotCache.Sample v=cache.view(owner,session);
  if(v==null){prime();if(!paintGrace()){CandidateController.diagnostic("async_reject="+cache.diagnosticState(owner,session)+(commands.busy()?"_command_busy":worker.busy()?"_worker_busy":""));return null;}last=lastPaint;return last;}
  ResearchOverlay.Snapshot s=unpack(v.state());last=s;lastPaint=s;paintAt=SystemClock.elapsedRealtime();dirty=false;return s;
 }
 public boolean snapshotValidated(ResearchOverlay.Snapshot s){if(s==null||s!=last)return false;BoundedSnapshotCache.Sample v=cache.view(owner,session);return v!=null?java.util.Arrays.equals(pack(s),v.state()):s==lastPaint&&paintGrace();}
 private boolean authorize(){if(!alive()||cache.takeForAction(owner,session)==null)return false;actionRevision=revision;return true;}
 public boolean refusalDefinitelyNotSent(){return true;}
 public boolean controlsAvailable(){return alive()&&!commands.busy()&&cache.canTake(owner,session);}
 public boolean setSeat(boolean driver,int target){return authorize()&&commands.seat(driver?0:1,target);}
 public boolean setRearSeat(boolean left,boolean heat,int target){if((heat&&target>=3&&target<=5)||(!heat&&target>=6))return false;return authorize()&&commands.seat(heat?(left?2:3):(left?4:5),target);}
 public boolean wheelPress(){return authorize()&&commands.press();}
 public boolean wheelRelease(){boolean accepted=commands.release();return accepted||scheduleUnsentReleaseRetry();}
 public void onPanelFailure(){}
 synchronized void invalidate(){revision++;dirty=true;cache.invalidate(owner,session);lastRefresh=-1;}
 public void onClosed(){panelClosed=true;commands.close();scheduleUnsentReleaseRetry();if(closedCallback!=null)try{closedCallback.run();}catch(Throwable ignored){} /* keep cache for guarded one-shot OEM recovery */}
 void close(){active=false;cache.stop();commands.close();scheduleUnsentReleaseRetry();}
}
