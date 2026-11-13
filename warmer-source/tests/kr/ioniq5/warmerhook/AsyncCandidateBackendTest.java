package kr.ioniq5.warmerhook;
import android.os.ServiceManager;import android.os.SystemClock;import android.os.Handler;import android.content.Context;import android.view.View;import com.hkmc.usm.IUSMService;
import com.hkmc.telematics.service.apps.climate.ClimateManager;import com.hkmc.telematics.service.apps.climate.j;
import hmgapi.hook.ModContext;import kr.ioniq5.warmerui.CallbackRegistry;import kr.ioniq5.warmerui.ResearchOverlay;
public final class AsyncCandidateBackendTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  CandidateControllerTest.Binder binder=new CandidateControllerTest.Binder();CandidateControllerTest.Remote remote=new CandidateControllerTest.Remote(binder);ServiceManager.binder=binder;IUSMService.Stub.remote=remote;CandidateBackendTest.installDipo(binder);
  ClimateManager owner=new ClimateManager();j listener=new j(owner);final CallbackRegistry registry=new CallbackRegistry(owner.getClass(),listener.getClass(),new CallbackRegistry.Clock(){public long elapsedMillis(){return SystemClock.now;}});
  registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},SystemClock.now,registry.checkpoint());
  AndroidCacheSource source=new AndroidCacheSource(owner.getClass().getClassLoader(),owner.getClass());ModContext mod=new ModContext();mod.experimental=true;
  CandidateBackendTest.Mode mode=new CandidateBackendTest.Mode();mode.forbidden=Thread.currentThread();binder.forbidden=Thread.currentThread();((CandidateBackendTest.Dipo)android.dipo.IDiPOManager.Stub.remote).forbidden=Thread.currentThread();
  CandidateBackend raw=new CandidateBackend(mod,mode,source,registry,owner.getClass().getMethod("mD"));
  AsyncCandidateBackend async=new AsyncCandidateBackend(raw);Object session=null;
  for(int i=0;i<200&&session==null;i++){async.prepare(owner);session=async.session(owner);Thread.sleep(5);}
  ok(session!=null,"background initialize and sample");ResearchOverlay.Controller c=async.controller(owner,session);ok(c!=null&&c.safeNow(),"cache ready");
  // Simulate a 750ms refresh cadence for a full minute using the same owner.
  // It verifies fresh samples continue without relaxing the one-second TTL.
  for(int tick=0;tick<81;tick++){
   SystemClock.now+=750; async.prepare(owner);
   for(int w=0;w<200&&async.session(owner)==null;w++)Thread.sleep(2);
   ok(async.session(owner)!=null,"fresh session after long running prewarm tick="+tick);
  }
  session=async.session(owner);c=async.controller(owner,session);
  for(int w=0;w<200&&!c.controlsAvailable();w++)Thread.sleep(2);
  ok(c.controlsAvailable(),"idle valid snapshot ready for action");
  ResearchOverlay.Snapshot snap=c.snapshot();ok(snap!=null&&c.snapshotValidated(snap)&&snap.rear!=null,"front rear snapshot only from worker cache");
  ok(c.controlsAvailable(),"reading a valid snapshot must not itself start a competing refresh");
  final int[] skipped={0};AsyncUiOpener opener=new AsyncUiOpener(async,1000);
  ok(opener.open(owner,new AsyncUiOpener.HookSkip(){public void skip(){skipped[0]++;}})&&skipped[0]==1,"real async backend opens UI");
  Context context=async.climateContext(owner);ok(context.wm.params.width==648,"rear layout selected");context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();
  View rear=context.wm.view.findViewById(0x7f060020);ok(rear!=null&&rear.enabled,"left rear button");
  remote.rearDelayMs=150;SystemClock.now+=600;async.prepare(owner);Handler.runPending();ok(!rear.enabled,"buttons disabled during delayed worker refresh");
  Thread.sleep(500);SystemClock.now+=200;Handler.runPending();ok(rear.enabled,"button re-enabled on subsequent render after delayed refresh");rear.tap();
  for(int i=0;i<200&&remote.rearCalls==0;i++)Thread.sleep(5);ok(remote.rearCalls==1,"rear request dispatched on worker while delay remains");remote.rearDelayMs=0;
  ok(binder.mainCalls==0&&mode.mainCalls==0&&((CandidateBackendTest.Dipo)android.dipo.IDiPOManager.Stub.remote).mainCalls==0,"UI paths make no mode or binder liveness calls");
  Object previous=session;opener.disable();
  for(int i=0;i<200&&!async.primedFor(owner);i++){SystemClock.now+=50;Handler.runPending();Thread.sleep(5);}
  ok(async.primedFor(owner),"close independently prewarms fresh session");session=async.session(owner);ok(session!=null&&session!=previous,"new entry ready without extra button presses");
  c=async.controller(owner,session);final int[] reopens={0};AsyncUiOpener again=new AsyncUiOpener(async,1000);
  ok(again.open(owner,new AsyncUiOpener.HookSkip(){public void skip(){reopens[0]++;}})&&reopens[0]==1,"first reopen click uses newly warmed session");
  context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();
  ClimateManager other=new ClimateManager();async.changed(new j(other));ok(c.safeNow(),"foreign listener cannot invalidate active owner");
  async.changed(listener);ok(!c.setSeat(true,7),"callback invalidation denies actions immediately");ok(c.safeNow(),"brief paint grace while refresh runs");
  for(int i=0;i<200&&async.session(owner)==null;i++){async.prepare(owner);Thread.sleep(5);}ok(async.session(owner)!=null,"background refresh after invalidation");
  remote.rearDelayMs=1000;SystemClock.now+=1001;ok(!c.safeNow(),"expired state denies UI/action");ok(!c.setSeat(true,7),"stale state cannot send");Handler.runPending();
  for(int i=0;i<200&&owner.mDCalls==0;i++){Thread.sleep(5);SystemClock.now+=10;Handler.runPending();}
  ok(owner.mDCalls==1,"stale USM worker still permits separate safe mode probe and one OEM attempt");
  Thread.sleep(20);Handler.runPending();ok(owner.mDCalls==1,"no duplicate OEM attempts");async.disable();ok(!c.safeNow(),"disable revokes state");
  System.out.println("AsyncCandidateBackend worker initialization, callbacks, TTL and session tests passed; no vehicle I/O");
 }
}
