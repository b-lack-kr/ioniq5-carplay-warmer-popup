package kr.ioniq5.warmerhook;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.view.View;
import com.hkmc.usm.IUSMService;
import com.hkmc.telematics.service.apps.climate.ClimateManager;
import com.hkmc.telematics.service.apps.climate.j;
import kr.ioniq5.warmerui.CallbackRegistry;
import kr.ioniq5.warmerui.ResearchOverlay;

public final class CandidateControllerTest {
 static final class Binder implements IBinder {boolean live=true;Thread forbidden;int mainCalls;public boolean isBinderAlive(){if(Thread.currentThread()==forbidden)mainCalls++;return live;}public boolean pingBinder(){if(Thread.currentThread()==forbidden)mainCalls++;return live;}}
 static final class Remote implements IUSMService { public int rearCalls;public volatile long rearDelayMs; public int getRearSeatHeat_LeftOption(){if(rearDelayMs>0)try{Thread.sleep(rearDelayMs);}catch(InterruptedException e){Thread.currentThread().interrupt();}return 1;} public int getRearSeatHeat_RightOption(){return 1;} public int getRearSeatHeat_LeftState(){return 8;} public int getRearSeatHeat_RightState(){return 2;} public void setRearSeatHeatLeftStep(int v){rearCalls++;} public void setRearSeatHeatRightStep(int v){rearCalls++;}
  final Binder b;int drvCalls,passCalls,wheelCalls,last;boolean fail,dropRelease=true,physicalPressed;Remote(Binder b){this.b=b;}public IBinder asBinder(){return b;}
  public int getFrontSeatHeat_DrvOption(){return 6;}public int getFrontSeatHeat_AstOption(){return 6;}
  public int getFrontSeatHeat_DrvState(){return 8;}public int getFrontSeatHeat_AstState(){return 2;}
  public int getSWRC_SteeringwheelHeatingOpt(){return 1;}public int getSWRC_SteeringwheelHeatingSWInf(){return 1;}
  public int getSWRC_SteeringwheelHeatIndStep2(){return 1;}
  public void setFrontSeatHeatDrvStep(int x){drvCalls++;last=x;if(fail)throw new IllegalStateException();}
  public void setFrontSeatHeatAstStep(int x){passCalls++;last=x;if(fail)throw new IllegalStateException();}
  public void setSWRC_SteeringwheelWarmerSet(int x){wheelCalls++;last=x;if(fail)throw new IllegalStateException();if(x==1)physicalPressed=true;else if(!dropRelease)physicalPressed=false;}
 }
 static final class Proof implements CandidateController.Proof {boolean ok=true;public boolean safe(Object o,Object s){return ok;}public boolean visible(View v){return ok;}}
 static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] ignored)throws Exception {
  Binder binder=new Binder();Remote remote=new Remote(binder);ServiceManager.binder=binder;IUSMService.Stub.remote=remote;
  ClimateManager owner=new ClimateManager();j listener=new j(owner);Object session=new Object();
  AndroidCacheSource source=new AndroidCacheSource(owner.getClass().getClassLoader(),owner.getClass());Object token=source.open();
  CallbackRegistry registry=new CallbackRegistry(owner.getClass(),listener.getClass(),new CallbackRegistry.Clock(){public long elapsedMillis(){return SystemClock.now;}});
  RearSeatCandidate rear=new RearSeatCandidate(owner,token,source);ok(rear.stableCache()!=null,"rear stable cache mapping");ok(rear.sendNext(true,kr.ioniq5.warmerui.SeatCycle.Mode.HEAT,6,false)&&remote.rearCalls==1,"rear fixed method sent; still not wired or hardware ACK");
  Proof proof=new Proof();CandidateController c=new CandidateController(owner,session,token,registry,source,proof);
  ok(c.snapshot()==null&&!c.setSeat(true,7),"no status no commands");
  ok(registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},SystemClock.now,registry.checkpoint()),"seed consistent cache");
  registry.observeWheel(listener,1,1,0,0);ok(c.snapshot()==null,"mismatching wheel rejected");
  ok("snapshot_reject=wheel_indicator_mismatch".equals(android.util.Log.last),"wheel mismatch diagnostic contains no raw state");
  registry.observeWheel(listener,1,1,0,2);ok(c.snapshot()==null,"invalid callback denies snapshot");
  ok("snapshot_reject=pair_wheel_invalid_mask_4".equals(android.util.Log.last),"missing callback diagnostic");
  ok(registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},SystemClock.now,registry.checkpoint()),"restore consistent cache");
  ResearchOverlay.Snapshot s=c.snapshot();ok(s!=null&&c.snapshotValidated(s),"current cache bound to owner");
  ok(!c.setSeat(true,4)&&remote.drvCalls==0,"reject arbitrary target");
  ok(c.setSeat(true,7)&&remote.drvCalls==1&&remote.last==7,"exact OEM next state sent; not actuator ACK");
  registry.observeFront(listener,6,6,7,2);ok(!c.snapshotValidated(s),"new status invalidates prior snapshot");
  registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},SystemClock.now,registry.checkpoint());
  ok(c.wheelPress()&&remote.last==1,"OEM wheel press request");proof.ok=false;
  ok(c.wheelRelease()&&remote.last==0,"release transport returned after safety loss");
  ok(remote.physicalPressed,"downstream dropped release despite Binder return; no physical acknowledgement");
  proof.ok=true;remote.fail=true;ok(!c.setSeat(true,7),"transport error not success");
  binder.live=false;ok(c.snapshot()==null,"dead service denies control");
  System.out.println("CandidateController OEM Binder whitelist and status gating tests passed; no vehicle I/O");
 }
}
