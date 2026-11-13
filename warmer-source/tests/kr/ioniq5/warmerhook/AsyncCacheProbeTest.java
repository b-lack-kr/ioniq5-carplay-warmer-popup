package kr.ioniq5.warmerhook;

public final class AsyncCacheProbeTest {
 static final class Fixture implements AsyncCacheProbe.Enabled,AsyncCacheProbe.Launch,AsyncCacheProbe.Report,CacheBootstrap.Source {
  Object owner=new Object(),token=new Object();boolean enabled=true,accept=true,alive=true;Runnable task;int reports;boolean matched;
  public boolean enabled(){return enabled;}public boolean submit(Runnable r){if(!accept)return false;task=r;return true;}
  public void cacheCompared(Object o,CacheBootstrap.Sample value,long checkpoint){reports++;matched=value!=null;}
  public Object open(){return alive?token:null;}public boolean unchanged(Object t){return alive&&t==token;}
  public int[] managerValues(Object o){return new int[]{6,6,8,2,1,1,1};}public int[] serviceValues(Object t){return managerValues(owner);}
  public long elapsedMillis(){return 10;}
  AsyncCacheProbe make(){return new AsyncCacheProbe(this,this,this,this);}
 }
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  Fixture f=new Fixture();AsyncCacheProbe p=f.make();ok(p.request(f.owner,0),"scheduled");ok(!p.request(f.owner,0),"single active");
  f.task.run();ok(f.reports==1&&f.matched,"match reported");ok(p.request(f.owner,0),"can reschedule");f.enabled=false;p.invalidate();f.enabled=true;f.task.run();ok(f.reports==1,"disable/re-enable invalidates old report");
  Fixture no=new Fixture();no.accept=false;AsyncCacheProbe q=no.make();ok(!q.request(no.owner,0),"launch failure");no.accept=true;ok(q.request(no.owner,0),"launch recovery");
  Fixture dead=new Fixture();AsyncCacheProbe z=dead.make();ok(z.request(dead.owner,0),"scheduled dead");dead.alive=false;dead.task.run();ok(dead.reports==1&&!dead.matched,"service loss reports mismatch");
  System.out.println("AsyncCacheProbe bounded background and disable tests passed; no vehicle I/O");
 }
}
