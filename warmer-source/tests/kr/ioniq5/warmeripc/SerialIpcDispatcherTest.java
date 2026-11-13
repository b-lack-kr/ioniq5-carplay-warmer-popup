package kr.ioniq5.warmeripc;
import java.util.*;
public final class SerialIpcDispatcherTest {
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 static class Rig implements SerialIpcDispatcher.Worker,SerialIpcDispatcher.Gate,SerialIpcDispatcher.Transport,SerialIpcDispatcher.Observer {
  Runnable pending;boolean gate=true,accept=true,closeDuringDown,failDown,failUp;SerialIpcDispatcher active;List<String> calls=new ArrayList<String>();
  public boolean submit(Runnable r){if(!accept||pending!=null)return false;pending=r;return true;}
  public boolean maySend(){calls.add("gate");return gate;}public void seat(int i,int t){calls.add("seat"+i+":"+t);}public void wheelDown(){calls.add("down");if(closeDuringDown)active.close();if(failDown)throw new IllegalStateException();}public void wheelUp(){calls.add("up");if(failUp)throw new IllegalStateException();}
  public void result(String op,boolean ok){calls.add(op+"="+ok);}void run(){Runnable r=pending;pending=null;r.run();}
 }
 public static void main(String[]a){
  Rig r=new Rig();SerialIpcDispatcher d=new SerialIpcDispatcher(r,r,r,r);ok(d.seat(2,6),"seat reserved");ok(!d.seat(3,6),"one worker only");d.close();r.run();ok(!r.calls.contains("seat2:6"),"queued seat cancelled on close");
  Rig b=new Rig();SerialIpcDispatcher x=new SerialIpcDispatcher(b,b,b,b);ok(x.press(),"press reserved");ok(x.release(),"release before worker");b.run();ok(b.calls.indexOf("down")>=0 && b.calls.indexOf("up")>b.calls.indexOf("down"),"quick tap sends down then up exactly once");
  Rig c=new Rig();SerialIpcDispatcher y=new SerialIpcDispatcher(c,c,c,c);ok(y.press(),"press");c.run();ok(c.calls.contains("down"),"down sent");ok(y.release(),"up scheduled");ok(!y.press(),"no concurrent down");c.run();ok(c.calls.indexOf("down")<c.calls.indexOf("up"),"ordered release");
  ok(y.press(),"new DOWN allowed after successful UP transport");c.run();ok(y.release(),"second UP");c.run();
  Rig locked=new Rig();locked.failUp=true;SerialIpcDispatcher failed=new SerialIpcDispatcher(locked,locked,locked,locked);ok(failed.press(),"lock scenario down");locked.run();ok(failed.release(),"up attempt");locked.run();ok(!failed.press(),"ambiguous UP error locks future DOWN");
  Rig retry=new Rig();SerialIpcDispatcher rr=new SerialIpcDispatcher(retry,retry,retry,retry);ok(rr.press(),"retry scenario press");retry.run();retry.accept=false;ok(!rr.release()&&rr.unsentReleasePending(),"unsent release kept reserved");ok(!rr.press(),"new press denied pending unsent release");retry.accept=true;ok(rr.release(),"requeue unsent release");retry.run();ok(Collections.frequency(retry.calls,"up")==1,"queue retry sent one release");
  Rig z=new Rig();SerialIpcDispatcher q=new SerialIpcDispatcher(z,z,z,z);ok(q.press(),"press");z.run();q.close();z.run();ok(z.calls.contains("up"),"close schedules release once");ok(!q.press(),"closed");
  Rig concurrent=new Rig();concurrent.closeDuringDown=true;SerialIpcDispatcher a1=new SerialIpcDispatcher(concurrent,concurrent,concurrent,concurrent);concurrent.active=a1;ok(a1.press(),"start down");concurrent.run();ok(concurrent.calls.indexOf("down")<concurrent.calls.indexOf("up")&&Collections.frequency(concurrent.calls,"up")==1,"close during DOWN orders one UP");
  Rig ambiguous=new Rig();ambiguous.closeDuringDown=true;ambiguous.failDown=true;SerialIpcDispatcher a2=new SerialIpcDispatcher(ambiguous,ambiguous,ambiguous,ambiguous);ambiguous.active=a2;ok(a2.press(),"ambiguous down");ambiguous.run();ok(Collections.frequency(ambiguous.calls,"up")==1,"failed ambiguous DOWN still attempts one release");
  Rig e=new Rig();e.gate=false;SerialIpcDispatcher bad=new SerialIpcDispatcher(e,e,e,e);ok(bad.seat(0,8),"queue accepted only");e.run();ok(!e.calls.contains("seat0:8"),"invalid safety denies actuator");
  System.out.println("SerialIpcDispatcher close, ordering, one-flight and gate tests passed; no vehicle I/O");
 }
}
