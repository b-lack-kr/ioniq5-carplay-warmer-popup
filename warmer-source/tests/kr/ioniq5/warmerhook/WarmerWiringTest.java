package kr.ioniq5.warmerhook;
import hmgapi.hook.*;
import com.hkmc.telematics.service.apps.climate.*;
import java.lang.reflect.Field;
import kr.ioniq5.warmerui.CallbackRegistry;
public final class WarmerWiringTest {
 static void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static WarmerHook setup(ModContext mod,boolean matched)throws Exception{
  WarmerHook h=new WarmerHook();Field f=WarmerHook.class.getDeclaredField("matched");f.setAccessible(true);f.setBoolean(h,matched);
  LoadedApp a=new LoadedApp();a.packageName=a.processName="com.hkmc.telematics.service.apps.climate";a.context=new Object();a.classLoader=WarmerWiringTest.class.getClassLoader();
  h.appLoaded(mod,a);return h;
 }
 static MethodHook.Param event(Object owner,Object[] args){MethodHook.Param p=new MethodHook.Param();p.thisObject=owner;p.args=args;return p;}
 static boolean logged(ModContext m,String suffix){return m.logs.toString().contains(suffix);}
 public static void main(String[] args)throws Throwable{
  Bridge.reset();ModContext mod=new ModContext();WarmerHook h=setup(mod,true);ok(Bridge.count()==4,"all hooks registered");
  Field pw=WarmerHook.class.getDeclaredField("prewarm");pw.setAccessible(true);
  Field running=CachePrewarm.class.getDeclaredField("running");running.setAccessible(true);
  mod.experimental=false;h.onSettingsChanged(mod);ok(!running.getBoolean(pw.get(h)),"setting OFF stops prewarm");
  mod.experimental=true;h.onSettingsChanged(mod);ok(running.getBoolean(pw.get(h)),"setting ON restarts prewarm after expiry");
  mod.experimental=false;h.onSettingsChanged(mod);
  ClimateManager owner=new ClimateManager();j listener=new j(owner);
  Bridge.get("onFrontSeatHeatStatusReceived").after(event(listener,new Object[]{6,6,8,2}));
  Bridge.get("onRearSeatHeatStatusReceived").after(event(listener,new Object[]{1,1,8,2}));
  Bridge.get("onSWRCStatusReceived").after(event(listener,new Object[]{1,1,0,0}));
  MethodHook.Param open=event(owner,new Object[]{});Bridge.get("mD").before(open);
  ok(!open.skipped&&logged(mod,"stock=1 status_pair=1"),"stock stays on with paired status");
  mod.active=false;h.onEnabled(mod,false);mod.active=true;h.onEnabled(mod,true);
  Bridge.get("mD").before(event(owner,new Object[]{}));ok(logged(mod,"stock=1 status_pair=0"),"disable discards status");
  mod.active=false; h.onEnabled(mod,false); int count=mod.logs.size();
  MethodHook.Param disabled=event(owner,new Object[]{}); Bridge.get("mD").before(disabled);
  ok(!disabled.skipped&&mod.logs.size()==count,"disabled hook passes original through");
  Bridge.reset(); Bridge.hookMethod(ClimateManager.class.getDeclaredMethod("mD"),new MethodHook(){});
  ModContext conflict=new ModContext();setup(conflict,true);
  ok(Bridge.count()==1&&logged(conflict,"setup_failed=1 stock=1"),"existing conflicting hook refuses all four hooks");
  Bridge.reset(); ModContext partial=new ModContext();Bridge.failAt="onSWRCStatusReceived";setup(partial,true);
  MethodHook.Param partialEvent=event(listener,new Object[]{6,6,8,2});Bridge.get("onFrontSeatHeatStatusReceived").after(partialEvent);
  ok(logged(partial,"setup_failed=1 stock=1"),"partial hooks are inert");
  Bridge.reset();ModContext mismatch=new ModContext();setup(mismatch,false);ok(Bridge.count()==0,"hash mismatch installs nothing");
  CallbackRegistry registry=new CallbackRegistry(ClimateManager.class,j.class,new CallbackRegistry.Clock(){public long elapsedMillis(){return 50;}});
  registry.observeRear(listener,1,1,8,2);ok(registry.rearSnapshot(owner)!=null&&!registry.rearInvalid(owner),"rear valid callback");
  registry.observeRear(listener,99,1,8,2);ok(registry.rearInvalid(owner)&&registry.rearSnapshot(owner)==null,"rear invalid callback disables rear display");
  registry.observeRear(listener,1,1,8,2);ok(!registry.rearInvalid(owner)&&registry.rearSnapshot(owner)!=null,"rear valid callback recovers");
  registry.clearAll();
  long point=registry.checkpoint();ok(registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},20,point),"consistent cache bootstrap");
  CallbackRegistry.Pair seeded=registry.snapshot(owner);ok(seeded!=null&&seeded.front.fromConsistentCache&&seeded.wheel.fromConsistentCache,"cache provenance retained");
  long old=registry.checkpoint();registry.observeFront(listener,6,6,7,2);ok(!registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},20,old),"callback races deny stale cache");
  old=registry.checkpoint();registry.invalidateWheel(listener);ok(!registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},20,old),"invalid callback prevents stale cache");
  old=registry.checkpoint();registry.clearAll();ok(!registry.seedConsistentCache(owner,new int[]{6,6,8,2,1,1,1},20,old),"disable prevents stale cache");
  System.out.println("Warmer status hook wiring tests passed; overlay disabled and no vehicle I/O");
 }
}
