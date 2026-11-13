package kr.ioniq5.warmerhook;
import android.os.IBinder;
import android.os.ServiceManager;
import com.hkmc.usm.IUSMService;
import com.hkmc.telematics.service.apps.climate.ClimateManager;

public final class AndroidCacheSourceTest {
 static final class Binder implements IBinder {boolean alive=true;public boolean isBinderAlive(){return alive;}public boolean pingBinder(){return alive;}}
 static final class Remote implements IUSMService { public int rearCalls; public int getRearSeatHeat_LeftOption(){return 1;} public int getRearSeatHeat_RightOption(){return 1;} public int getRearSeatHeat_LeftState(){return 8;} public int getRearSeatHeat_RightState(){return 2;} public void setRearSeatHeatLeftStep(int v){rearCalls++;} public void setRearSeatHeatRightStep(int v){rearCalls++;}
  final IBinder binder;boolean fail;Remote(IBinder b){binder=b;}public IBinder asBinder(){return binder;}
  public int getFrontSeatHeat_DrvOption(){return 6;} public int getFrontSeatHeat_AstOption(){return 6;}
  public int getFrontSeatHeat_DrvState(){if(fail)throw new IllegalStateException();return 8;}public int getFrontSeatHeat_AstState(){return 2;}
  public int getSWRC_SteeringwheelHeatingOpt(){return 1;}public int getSWRC_SteeringwheelHeatingSWInf(){return 1;}public int getSWRC_SteeringwheelHeatIndStep2(){return 1;}
  public void setFrontSeatHeatDrvStep(int value){}public void setFrontSeatHeatAstStep(int value){}public void setSWRC_SteeringwheelWarmerSet(int value){}
 }
 static void ok(boolean v,String reason){if(!v)throw new AssertionError(reason);}
 public static void main(String[] args){
  Binder b=new Binder();Remote remote=new Remote(b);ServiceManager.binder=b;IUSMService.Stub.remote=remote;
  ClimateManager owner=new ClimateManager();AndroidCacheSource source=new AndroidCacheSource(owner.getClass().getClassLoader(),owner.getClass());
  CacheBootstrap.Sample sample=CacheBootstrap.read(owner,source);ok(sample!=null&&sample.values()[2]==8,"real signatures and stable cache");
  remote.fail=true;ok(CacheBootstrap.read(owner,source)==null,"remote errors do not become values");remote.fail=false;
  IUSMService.Stub.remote=new Remote(new Binder());ok(CacheBootstrap.read(owner,source)==null,"binder identity mismatch");
  IUSMService.Stub.remote=remote;b.alive=false;ok(CacheBootstrap.read(owner,source)==null,"dead binder");
  System.out.println("AndroidCacheSource binder / identity / exception tests passed; no device I/O");
 }
}
