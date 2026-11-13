package kr.ioniq5.warmeripc;
import java.util.*;import java.util.concurrent.*;
public final class SerialIpcBlockedTest {
 static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[]a)throws Exception{
  final SingleDaemonWorker worker=new SingleDaemonWorker();final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),up=new CountDownLatch(1);final List<String> order=Collections.synchronizedList(new ArrayList<String>());
  SerialIpcDispatcher d=new SerialIpcDispatcher(new SerialIpcDispatcher.Worker(){public boolean submit(Runnable r){return worker.submit(r);}},new SerialIpcDispatcher.Gate(){public boolean maySend(){return true;}},new SerialIpcDispatcher.Transport(){public void seat(int i,int t){order.add("seat");}public void wheelDown()throws Exception{order.add("down");entered.countDown();release.await();}public void wheelUp(){order.add("up");up.countDown();}},new SerialIpcDispatcher.Observer(){public void result(String o,boolean v){}});
  ok(d.press(),"schedule");ok(entered.await(1,TimeUnit.SECONDS),"blocked down reached");d.close();ok(!up.await(150,TimeUnit.MILLISECONDS),"never sends concurrent UP ahead of blocked DOWN");ok(!d.seat(0,8),"closed blocks other requests");release.countDown();ok(up.await(2,TimeUnit.SECONDS),"after DOWN returns one UP sent");ok(order.equals(Arrays.asList("down","up")),"strict order and one release");worker.close();System.out.println("SerialIpc blocked DOWN close ordering test passed; process death cannot be simulated");
 }
}
