package kr.ioniq5.warmerhook;

public final class CacheBootstrapTest {
 static final class Fake implements CacheBootstrap.Source {
  final Object token=new Object(),owner=new Object();int[] manager={6,6,8,2,1,1,1},server=manager.clone();
  boolean live=true,changed,fail;int calls;long time=10;
  public Object open(){return live?token:null;}
  public boolean unchanged(Object t){return live&&t==token;}
  public int[] managerValues(Object o){if(o!=owner||fail)throw new IllegalStateException();return manager.clone();}
  public int[] serviceValues(Object t){calls++;int[] r=server.clone();if(changed&&calls==2)r[2]=7;return r;}
  public long elapsedMillis(){return time;}
 }
 static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] ignored){
  Fake f=new Fake();CacheBootstrap.Sample s=CacheBootstrap.read(f.owner,f);ok(s!=null&&s.values()[2]==8,"stable equal cache accepted");
  int[] a=s.values();a[2]=5;ok(s.values()[2]==8,"defensive snapshot");
  Fake n=new Fake();n.server[0]=-1;ok(CacheBootstrap.read(n.owner,n)==null,"invalid getter rejected");
  Fake m=new Fake();m.server[2]=7;ok(CacheBootstrap.read(m.owner,m)==null,"mismatch rejected");
  Fake race=new Fake();race.changed=true;ok(CacheBootstrap.read(race.owner,race)==null,"between-read state change rejected");
  Fake dead=new Fake();dead.live=false;ok(CacheBootstrap.read(dead.owner,dead)==null,"binder unavailable rejected");
  Fake err=new Fake();err.fail=true;ok(CacheBootstrap.read(err.owner,err)==null,"getter exception rejected");
  Fake other=new Fake();ok(CacheBootstrap.read(new Object(),other)==null,"wrong owner rejected");
  System.out.println("CacheBootstrap consistent OEM-cache tests passed; not sensor freshness proof");
 }
}
