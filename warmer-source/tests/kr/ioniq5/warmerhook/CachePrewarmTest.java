package kr.ioniq5.warmerhook;
import com.hkmc.telematics.service.apps.climate.ClimateManager;import com.hkmc.telematics.service.apps.climate.j;import kr.ioniq5.warmerui.CallbackRegistry;
public final class CachePrewarmTest {
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 static final class S implements CachePrewarm.Scheduler{Runnable task;public boolean postDelayed(Runnable r,long d){task=r;return true;}public void remove(Runnable r){if(task==r)task=null;}void tick(){Runnable r=task;task=null;r.run();}}
 public static void main(String[] args)throws Exception{
  final ClimateManager owner=new ClimateManager();ClimateManager.setTestInstance(null);final CallbackRegistry registry=new CallbackRegistry(ClimateManager.class,j.class,new CallbackRegistry.Clock(){public long elapsedMillis(){return 10;}});
  final AsyncCacheProbeTest.Fixture f=new AsyncCacheProbeTest.Fixture();
  AsyncCacheProbe probe=new AsyncCacheProbe(f,f,f,new AsyncCacheProbe.Report(){public void cacheCompared(Object o,CacheBootstrap.Sample value,long checkpoint){if(value!=null)registry.seedConsistentCache(o,value.values(),value.at,checkpoint);}});
  final int[] warms={0}; S s=new S();CachePrewarm p=new CachePrewarm(ClimateManager.class,registry,probe,s,new CachePrewarm.Warm(){public void prepare(Object o){warms[0]++;}});p.start();s.tick();ok(f.task==null,"absent singleton no read");
  ClimateManager.setTestInstance(owner);s.tick();ok(f.task!=null,"same singleton asynchronously read");f.task.run();ok(registry.snapshot(owner)!=null,"consistent cache seeded");
  f.task=null;s.tick();ok(f.task==null,"ready cache avoids repeated probe");
  // 250ms polling continues past 60s; original loop stopped permanently at 120 ticks.
  int before=warms[0];for(int i=0;i<300;i++){ok(s.task!=null,"prewarm runs past former 60 second limit");s.tick();}ok(warms[0]-before==300,"continue warming the real owner past 60 seconds");
  p.stop();ok(s.task==null,"stop cancels polling");
  registry.clearAll();p.start();s.tick();ok(f.task!=null,"new probe started");probe.invalidate();p.stop();f.task.run();ok(registry.snapshot(owner)==null,"disable race suppresses late result");
  System.out.println("CachePrewarm singleton, cold start and disable race tests passed; first click not guaranteed");
 }
}
