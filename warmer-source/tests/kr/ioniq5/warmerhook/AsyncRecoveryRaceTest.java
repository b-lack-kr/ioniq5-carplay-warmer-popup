package kr.ioniq5.warmerhook;
import android.os.ServiceManager;import android.os.SystemClock;import android.os.Handler;import com.hkmc.usm.IUSMService;
import com.hkmc.telematics.service.apps.climate.ClimateManager;import com.hkmc.telematics.service.apps.climate.j;
import hmgapi.hook.ModContext;import kr.ioniq5.warmerui.CallbackRegistry;import java.util.concurrent.*;
public final class AsyncRecoveryRaceTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  CandidateControllerTest.Binder binder=new CandidateControllerTest.Binder();CandidateControllerTest.Remote remote=new CandidateControllerTest.Remote(binder);ServiceManager.binder=binder;IUSMService.Stub.remote=remote;CandidateBackendTest.installDipo(binder);
  final ClimateManager owner=new ClimateManager();j listener=new j(owner);CallbackRegistry registry=new CallbackRegistry(owner.getClass(),listener.getClass(),new CallbackRegistry.Clock(){public long elapsedMillis(){return SystemClock.now;}});
  registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},SystemClock.now,registry.checkpoint());
  AndroidCacheSource source=new AndroidCacheSource(owner.getClass().getClassLoader(),owner.getClass());ModContext mod=new ModContext();mod.experimental=true;CandidateBackendTest.Mode mode=new CandidateBackendTest.Mode();
  AsyncCandidateBackend b=new AsyncCandidateBackend(new CandidateBackend(mod,mode,source,registry,owner.getClass().getMethod("mD")));Object session=null;
  for(int i=0;i<200&&session==null;i++){b.prepare(owner);session=b.session(owner);Thread.sleep(5);}ok(session!=null,"ready");
  AsyncUiOpener opener=new AsyncUiOpener(b,1000);ok(opener.open(owner,new AsyncUiOpener.HookSkip(){public void skip(){}}),"stock suppressed");
  mode.entered=new CountDownLatch(1);mode.resume=new CountDownLatch(1);b.recoverOnce(owner,session);ok(mode.entered.await(2,TimeUnit.SECONDS),"recovery read starts on worker");
  b.newRequest(owner);mode.resume.countDown();for(int i=0;i<50;i++){Thread.sleep(5);Handler.runPending();}ok(owner.mDCalls==0,"new request cancels delayed old recovery");
  b.disable();opener.disable();System.out.println("AsyncRecoveryRace user re-entry cancels delayed OEM retry; no vehicle I/O");
 }
}
