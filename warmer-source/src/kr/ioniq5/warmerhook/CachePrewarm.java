package kr.ioniq5.warmerhook;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import kr.ioniq5.warmerui.CallbackRegistry;
/** One asynchronous prewarm loop while the mod is enabled. Binder work is
 * single-flight, deadline/TTL checked and never blocks a WARMER click. */
final class CachePrewarm {
 interface Warm{void prepare(Object owner);}
 interface Scheduler{boolean postDelayed(Runnable r,long delay);void remove(Runnable r);}
 private final Class<?> type;private final Warm warm;private final CallbackRegistry registry;private final AsyncCacheProbe probe;private final Scheduler scheduler;
 private boolean running;private long generation;
 private final Runnable poll=new Runnable(){public void run(){tick();}};
 CachePrewarm(Class<?> t,CallbackRegistry r,AsyncCacheProbe p,Scheduler s,Warm w){type=t;registry=r;probe=p;scheduler=s;warm=w;}
 synchronized void start(){stop();running=true;generation++;schedule(0);}
 synchronized void stop(){running=false;generation++;try{scheduler.remove(poll);}catch(Throwable ignored){}}
 private synchronized void schedule(long delay){try{if(running&&!scheduler.postDelayed(poll,delay))running=false;}catch(Throwable e){running=false;}}
 private void tick(){
  synchronized(this){if(!running)return;}
  try{Field f=type.getDeclaredField("bl");if(!Modifier.isStatic(f.getModifiers())||f.getType()!=type)throw new IllegalStateException();f.setAccessible(true);Object owner=f.get(null);
   if(owner!=null&&owner.getClass()==type){if(registry.snapshot(owner)==null)probe.request(owner,registry.checkpoint());if(warm!=null)warm.prepare(owner);}
  }catch(Throwable ignored){}
  synchronized(this){if(running)schedule(250);}
 }
}
