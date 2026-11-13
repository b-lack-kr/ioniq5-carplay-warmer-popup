package kr.ioniq5.warmerhook;
import android.os.ServiceManager;
import android.os.SystemClock;
import com.hkmc.usm.IUSMService;
import com.hkmc.telematics.service.apps.climate.ClimateManager;
import com.hkmc.telematics.service.apps.climate.j;
import hmgapi.hook.ModContext;
import kr.ioniq5.warmerui.CallbackRegistry;
public final class CandidateBackendTest {
 static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
 static final class Dipo implements android.dipo.IDiPOManager {final android.os.IBinder binder;int status=4,mode=2,statusReads,modeReads;boolean fail,flipStatus,flipMode;Thread forbidden;int mainCalls;Dipo(android.os.IBinder b){binder=b;}public android.os.IBinder asBinder(){return binder;}public int getDiPOConnectStatus(){if(Thread.currentThread()==forbidden)mainCalls++;if(fail)throw new IllegalStateException();statusReads++;return flipStatus&&statusReads>1?5:status;}public int getDiPOConnectedMode(){if(Thread.currentThread()==forbidden)mainCalls++;modeReads++;return flipMode&&modeReads>1?1:mode;}}
 static void installDipo(android.os.IBinder binder){ServiceManager.dipoBinder=binder;android.dipo.IDiPOManager.Stub.remote=new Dipo(binder);}
 static final class Mode implements FailClosedModeState.Access {Object token=new Object();boolean on=true;Thread forbidden;int mainCalls;volatile java.util.concurrent.CountDownLatch entered,resume;
  public boolean onMainThread(){return true;}public boolean ignitionOn(Object o){if(Thread.currentThread()==forbidden)mainCalls++;java.util.concurrent.CountDownLatch e=entered,r=resume;if(e!=null&&r!=null){e.countDown();try{r.await();}catch(InterruptedException x){Thread.currentThread().interrupt();}}return on;}
  public Object service(){return token;}public boolean alive(Object o){return on;}public boolean sameService(Object o){return o==token;}
  public Boolean flag(Object o,String m,String a){return m.equals("isScreenOn")||m.equals("isForegroundActivity");}}
 public static void main(String[] x)throws Exception {
  CandidateControllerTest.Binder binder=new CandidateControllerTest.Binder();CandidateControllerTest.Remote remote=new CandidateControllerTest.Remote(binder);
  ServiceManager.binder=binder;IUSMService.Stub.remote=remote;installDipo(binder);ClimateManager owner=new ClimateManager();j listener=new j(owner);
  CallbackRegistry r=new CallbackRegistry(owner.getClass(),listener.getClass(),new CallbackRegistry.Clock(){public long elapsedMillis(){return SystemClock.now;}});
  AndroidCacheSource source=new AndroidCacheSource(owner.getClass().getClassLoader(),owner.getClass());ModContext mod=new ModContext();Mode mode=new Mode();
  CandidateBackend b=new CandidateBackend(mod,mode,source,r,owner.getClass().getMethod("mD"));
  ok(b.session(owner)==null,"default OFF");mod.experimental=true;ok(b.session(owner)==null,"no cache deny");
  ok(r.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},SystemClock.now,r.checkpoint()),"seed");Object s=b.session(owner);ok(s!=null&&b.overlayAllowed(owner,s),"consistent candidate allowed");
  ok(b.climateContext(owner)!=null,"exact OEM context field resolved");
  Dipo cp=(Dipo)android.dipo.IDiPOManager.Stub.remote;cp.status=3;ok(!b.overlayAllowed(owner,s),"CP not yet activated denied");cp.status=4;cp.mode=0;ok(!b.overlayAllowed(owner,s),"disconnected transport denied");cp.mode=2;cp.fail=true;ok(!b.overlayAllowed(owner,s),"Dipo service exception denied");cp.fail=false;
  mode.on=false;ok(!b.overlayAllowed(owner,s),"ignition change denies");mode.on=true;
  b.committed(owner,s);b.recoverOnce(owner,s);b.recoverOnce(owner,s);ok(mod.logs.size()==2&&mod.logs.get(1).contains("NONE"),"one-shot recovery, original hook not proven");
  mod.experimental=false;ok(!b.overlayAllowed(owner,s),"setting off blocks");
  System.out.println("CandidateBackend default off, Binder/mode gating and one-shot recovery tests passed; no vehicle I/O");
 }
}
