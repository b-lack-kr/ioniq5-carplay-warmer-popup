package kr.ioniq5.warmerhook;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import kr.ioniq5.warmerui.ResearchOverlay;

public final class AsyncUiOpenerTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static final class Rig implements AsyncUiOpener.Backend,ResearchOverlay.Controller,AsyncUiOpener.HookSkip {
  final Object owner=new Object(),token=new Object();final Context context=new Context();boolean safe=true,recover=true,visible=true,session=true;int skip,restore,rearCalls; int[] rear; boolean lastLeft; int lastTarget;
  public Object session(Object o){return o==owner&&session?token:null;}public Context climateContext(Object o){return context;}
  public ResearchOverlay.Controller controller(Object o,Object s){return this;}
  public boolean overlayAllowed(Object o,Object s){return o==owner&&s==token&&session&&safe;}
  public boolean recoveryAllowed(Object o,Object s){return o==owner&&s==token&&session&&recover;}
  public void committed(Object o,Object s){} public void recoverOnce(Object o,Object s){restore++;}public void skip(){skip++;}
  public boolean safeNow(){return safe;}public boolean overlayVerified(View v){return visible;}
  public boolean snapshotValidated(ResearchOverlay.Snapshot s){return s!=null;}
  public ResearchOverlay.Snapshot snapshot(){return new ResearchOverlay.Snapshot(SystemClock.now,6,6,2,2,true,false,false,rear);}
  public boolean setSeat(boolean driver,int state){return true;} public boolean setRearSeat(boolean left,boolean heat,int state){rearCalls++;lastLeft=left;lastTarget=state;return true;}public boolean wheelPress(){return true;}
  public boolean wheelRelease(){return true;}public void onPanelFailure(){}
 }
 static final class Probe implements AsyncUiOpener.ForegroundProbe,AsyncUiOpener.ForegroundExit {
  volatile Boolean shown=Boolean.TRUE;volatile int reads;int exits;volatile java.util.concurrent.CountDownLatch entered,resume;
  public Boolean check(Object owner){Boolean result=shown;java.util.concurrent.CountDownLatch e=entered,r=resume;if(e!=null&&r!=null){e.countDown();try{r.await();}catch(InterruptedException x){Thread.currentThread().interrupt();}}reads++;return result;}
  public void exited(Object owner){exits++;}
 }
 static void drain(){for(int i=0;i<20;i++){try{Thread.sleep(3);}catch(InterruptedException e){throw new AssertionError(e);}Handler.runPending();}}
 static void tick(){SystemClock.now+=100;Handler.runPending();drain();}
 public static void main(String[] args){
  Rig a=new Rig();AsyncUiOpener opener=new AsyncUiOpener(a,1000);
  ok(opener.open(a.owner,a)&&a.skip==1&&a.context.wm.view!=null&&!a.context.wm.view.attached,"must stage before async attachment");
  ok(a.context.wm.params.width==452&&a.context.wm.params.height==414,"OEM front-only dp dimensions at density 1");
  a.context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();ok(a.restore==0,"verified window has no recovery");
  opener.disable();ok(a.context.wm.view==null&&a.restore==0,"disable closes without reopening");
  Rig toggle=new Rig();AsyncUiOpener toggleOpener=new AsyncUiOpener(toggle,1000);
  ok(toggleOpener.open(toggle.owner,toggle),"open for physical key toggle");
  toggle.context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();
  toggle.session=false; // existing cached session may have expired; dismissal must not reopen OEM
  ok(toggleOpener.open(toggle.owner,toggle)&&toggle.skip==2,"second key suppresses original even when cache expires");
  ok(toggle.context.wm.view==null&&toggle.restore==0,"second key closes overlay without stock/home");
  SystemClock.now+=2000;Handler.runPending();ok(toggle.restore==0,"closed overlay cannot recover later");
  Rig pending=new Rig();AsyncUiOpener pendingOpener=new AsyncUiOpener(pending,1000);
  ok(pendingOpener.open(pending.owner,pending),"first key stages window");
  ok(pendingOpener.open(pending.owner,pending)&&pending.skip==2,"second key closes pending window");
  SystemClock.now+=1100;Handler.runPending();ok(pending.restore==0&&pending.context.wm.view==null,"pending close cancels recovery");
  Rig different=new Rig();AsyncUiOpener differentOpener=new AsyncUiOpener(different,1000);
  ok(differentOpener.open(different.owner,different),"first owner opens");
  ok(!differentOpener.open(new Object(),different)&&different.skip==1,"other owner is never swallowed");
  Rig thrown=new Rig();AsyncUiOpener thrownOpener=new AsyncUiOpener(thrown,1000);
  ok(thrownOpener.open(thrown.owner,thrown),"skip-failure setup");
  ok(!thrownOpener.open(thrown.owner,new AsyncUiOpener.HookSkip(){public void skip() throws Exception{throw new Exception("skip unavailable");}}),"skip failure does not claim interception");
  ok(thrown.context.wm.view!=null&&thrown.restore==0,"ambiguous skip does not independently reopen stock");
  thrownOpener.disable();
  Rig b=new Rig();b.context.wm.failAdd=true;ok(!new AsyncUiOpener(b,1000).open(b.owner,b)&&b.skip==0,"add error leaves stock");
  Rig c=new Rig();AsyncUiOpener timed=new AsyncUiOpener(c,1000);ok(timed.open(c.owner,c),"timeout staged");
  SystemClock.now+=1001;Handler.runPending();ok(c.restore==1&&c.context.wm.view==null,"timeout triggers single guarded OEM recovery");
  timed.invalidate();ok(c.restore==1,"duplicate failure ignored");
  Rig d=new Rig();AsyncUiOpener unsafe=new AsyncUiOpener(d,1000);ok(unsafe.open(d.owner,d),"unsafe staged");d.safe=false;d.recover=false;
  SystemClock.now+=16;Handler.runPending();ok(d.restore==0&&d.context.wm.view==null,"unsafe state never forces OEM popup");
  Rig r=new Rig();r.rear=new int[]{1,1,8,2};AsyncUiOpener rearOpener=new AsyncUiOpener(r,1000);
  ok(rearOpener.open(r.owner,r)&&r.context.wm.params.width==648&&r.context.wm.params.height==414,"rear OEM dimensions");
  View left=r.context.wm.view.findViewById(0x7f060020),right=r.context.wm.view.findViewById(0x7f060022),unsupportedVent=r.context.wm.view.findViewById(0x7f060021);
  ok(left!=null&&right!=null&&!unsupportedVent.enabled,"rear mapping and option hides unsupported vent");left.tap();ok(r.rearCalls==1&&r.lastLeft&&r.lastTarget==6,"left rear target");left.tap();ok(r.rearCalls==1,"pending left suppresses duplicate");right.tap();ok(r.rearCalls==2&&!r.lastLeft&&r.lastTarget==8,"right rear target");
  rearOpener.disable();
  Rig f=new Rig();Probe probe=new Probe();AsyncUiOpener watched=new AsyncUiOpener(f,1000);watched.setForegroundProbe(probe,probe);
  ok(watched.open(f.owner,f),"foreground open");f.context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();tick();
  ok(f.context.wm.view!=null&&probe.reads>0,"CarPlay with popup remains foreground");
  probe.shown=null;tick();ok(f.context.wm.view!=null&&probe.exits==0,"unknown mode never guesses departure");
  probe.shown=false;tick();ok(f.context.wm.view==null&&f.restore==0&&probe.exits==1,"confirmed exit promptly closes without OEM recovery");
  SystemClock.now+=2000;Handler.runPending();ok(f.restore==0&&probe.exits==1,"departure cancels delayed coordinator recovery");
  Rig unarmed=new Rig();Probe up=new Probe();up.shown=false;AsyncUiOpener uo=new AsyncUiOpener(unarmed,1000);uo.setForegroundProbe(up,up);
  ok(uo.open(unarmed.owner,unarmed),"unarmed open");unarmed.context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();tick();
  ok(unarmed.context.wm.view!=null&&up.exits==0,"overlay-as-foreground cannot close unconditionally");uo.disable();
  Rig stale=new Rig();Probe sp=new Probe();AsyncUiOpener so=new AsyncUiOpener(stale,1000);so.setForegroundProbe(sp,sp);
  ok(so.open(stale.owner,stale),"stale open");stale.context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();tick();
  sp.shown=false;sp.entered=new java.util.concurrent.CountDownLatch(1);sp.resume=new java.util.concurrent.CountDownLatch(1);
  SystemClock.now+=100;Handler.runPending();
  try{ok(sp.entered.await(1,java.util.concurrent.TimeUnit.SECONDS),"probe running away from UI");}catch(InterruptedException e){throw new AssertionError(e);}
  // A stuck Binder probe must not block UI closure or replacement.
  so.disable();ok(stale.context.wm.view==null,"blocked worker cannot prevent close");
  ok(so.open(stale.owner,stale),"replacement opens while old probe blocked");stale.context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();
  sp.shown=true;sp.resume.countDown();drain();
  ok(stale.context.wm.view!=null&&sp.exits==0,"old false response cannot close replacement");
  sp.entered=null;sp.resume=null;tick();so.disable();
  System.out.println("AsyncUiOpener mock HMG boundary / delayed attach / rear button tests passed; no vehicle I/O");
 }
}
