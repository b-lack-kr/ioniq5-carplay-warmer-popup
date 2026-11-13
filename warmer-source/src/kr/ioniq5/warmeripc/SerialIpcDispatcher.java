package kr.ioniq5.warmeripc;
/** Single-flight command dispatcher. All vehicle I/O runs on the supplied
 * worker, never under a lock or on the caller. A successful submit is queue
 * acceptance, not MICOM/actuator acknowledgement. One session only. */
public final class SerialIpcDispatcher {
 public interface Worker{boolean submit(Runnable task);}
 public interface Gate{boolean maySend();}
 public interface Transport{void seat(int index,int target)throws Exception;void wheelDown()throws Exception;void wheelUp()throws Exception;}
 public interface Observer{void result(String op,boolean transportReturned);}
 private final Worker worker;private final Gate gate;private final Transport transport;private final Observer observer;
 private boolean closed,busy,pressReserved,downAttempted,releaseWanted,upIssued,wheelLocked;
 public SerialIpcDispatcher(Worker w,Gate g,Transport t,Observer o){if(w==null||g==null||t==null||o==null)throw new IllegalArgumentException();worker=w;gate=g;transport=t;observer=o;}
 private void result(String op,boolean ok){try{observer.result(op,ok);}catch(Throwable ignored){}}
 private boolean submit(Runnable r){try{return worker.submit(r);}catch(Throwable e){return false;}}
 public synchronized boolean seat(final int index,final int target){
  if(closed||busy||pressReserved||index<0||index>5||target<2||target>8)return false;busy=true;
  if(submit(new Runnable(){public void run(){boolean ok=false;try{boolean allowed; synchronized(SerialIpcDispatcher.this){allowed=!closed;}if(allowed&&gate.maySend()){transport.seat(index,target);ok=true;}}catch(Throwable ignored){}finally{synchronized(SerialIpcDispatcher.this){busy=false;}result("seat",ok);}}}))return true;
  busy=false;return false;
 }
 public synchronized boolean press(){
  if(closed||busy||pressReserved||wheelLocked)return false;busy=true;pressReserved=true;downAttempted=false;releaseWanted=false;upIssued=false;
  if(submit(new Runnable(){public void run(){boolean ok=false;try{boolean allowed;synchronized(SerialIpcDispatcher.this){allowed=!closed;}if(allowed&&gate.maySend()){
     synchronized(SerialIpcDispatcher.this){allowed=!closed;if(allowed)downAttempted=true;}
     if(allowed){transport.wheelDown();ok=true;}
    }}catch(Throwable ignored){}finally{finishDown(ok);}}}))return true;
  busy=false;pressReserved=false;return false;
 }
 private void finishDown(boolean ok){
  boolean sendUp=false; synchronized(this){busy=false;if(downAttempted&&releaseWanted&&!upIssued){upIssued=true;busy=true;sendUp=true;}else if(!downAttempted)pressReserved=false;}
  result("down",ok);if(sendUp)sendUpNow();
 }
 private void sendUpNow(){boolean ok=false;try{transport.wheelUp();ok=true;}catch(Throwable ignored){}finally{synchronized(this){busy=false;pressReserved=false;if(!ok)wheelLocked=true;}result("up",ok);}}
 /** Reserve the only release before scheduling. No duplicate automatic retry:
  * a failed Binder call can have reached the other process. */
 public synchronized boolean release(){
  if(!pressReserved)return false;releaseWanted=true;
  if(busy)return true;
  if(!downAttempted){pressReserved=false;return true;}
  if(upIssued)return true;upIssued=true;busy=true;
  if(submit(new Runnable(){public void run(){sendUpNow();}}))return true;
  // A rejected queue submission provably sent nothing. Keep the release
  // reserved so a later submission can safely retry; no new DOWN is allowed.
  busy=false;upIssued=false;return false;
 }
 public synchronized boolean unsentReleasePending(){return pressReserved&&releaseWanted&&!upIssued&&!busy;}
 public synchronized boolean busy(){return busy||pressReserved;}
 public synchronized void close(){closed=true;if(pressReserved)release();}
}
