import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import kr.ioniq5.warmerui.ResearchOverlay;

/** Android UI simulation; real view timing and z-order remain unverified. */
public final class TestAsyncOverlay {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static final class C implements ResearchOverlay.Controller {
  boolean safe=true,visible=true,missing,controls=true;int failures,presses,releases;
  public boolean safeNow(){return safe;} public boolean overlayVerified(View v){return safe&&visible;}
  public boolean snapshotValidated(ResearchOverlay.Snapshot s){return s!=null;}
  public ResearchOverlay.Snapshot snapshot(){return missing?null:new ResearchOverlay.Snapshot(SystemClock.now,6,6,2,2,true,false,false);}
  public boolean controlsAvailable(){return controls;}public boolean setSeat(boolean d,int v){return true;}public boolean wheelPress(){presses++;return true;}
  public boolean wheelRelease(){releases++;return true;}public void onPanelFailure(){failures++;}
 }
 static final class L implements ResearchOverlay.AsyncListener {int ready,fail,closed;public void ready(){ready++;}public void failed(){fail++;}public void closedByUser(){closed++;}}
 public static void main(String[] args){
  Context context=new Context();C ctrl=new C();L listener=new L();
  ResearchOverlay panel=ResearchOverlay.beginAsync(context,ctrl,listener,1000);
  ok(panel!=null&&context.wm.view!=null&&!context.wm.view.attached,"addView returns before frame");
  ok(context.wm.view.backgroundResource==0x7f0203d0,"OEM popup backdrop assigned");
  ok(context.wm.view.getPaddingLeft()==1&&context.wm.view.getPaddingTop()==2&&context.wm.view.getPaddingRight()==3&&context.wm.view.getPaddingBottom()==4,"background keeps OEM layout padding");
  ok(context.wm.params.gravity==android.view.Gravity.CENTER,"center gravity retained for measurement");
  panel.markSuppressionCommitted();ok(listener.fail==0,"committing cannot fail only because token is pending");
  SystemClock.now+=16;Handler.runPending();ok(listener.ready==0&&listener.fail==0,"wait for future attach");
  context.wm.view.attached=true;SystemClock.now+=16;Handler.runPending();ok(listener.ready==1&&panel.attachedAndUsable(),"ready after attachment and proof");
  
  try {
   java.lang.reflect.Field sf=ResearchOverlay.class.getDeclaredField("seats"), wf=ResearchOverlay.class.getDeclaredField("wheel");sf.setAccessible(true);wf.setAccessible(true);
   View[] buttons=(View[])sf.get(panel);View wheel=(View)wf.get(panel);
   wheel.event(android.view.MotionEvent.ACTION_DOWN);ok(ctrl.presses==1,"wheel down handled");
   ok(wheel.pressed,"wheel uses OEM pressed drawable on down");
   ctrl.controls=false;SystemClock.now+=250;Handler.runPending();
   ok(ctrl.releases==1&&!wheel.enabled,"busy control releases before disable with fresh state");
   ok(!wheel.pressed,"disabled wheel cannot retain pressed visual");
   wheel.event(android.view.MotionEvent.ACTION_UP);ok(ctrl.releases==1,"disabled up ignored without duplicate");
   ctrl.controls=true;SystemClock.now+=250;Handler.runPending();ok(wheel.enabled,"controls reenabled");
   wheel.event(android.view.MotionEvent.ACTION_DOWN);ok(ctrl.presses==2,"wheel second down");
   ctrl.missing=true;SystemClock.now+=250;Handler.runPending();
   ok(ctrl.releases==2,"wheel released before it is disabled");
   wheel.event(android.view.MotionEvent.ACTION_UP);ok(ctrl.releases==2,"disabled view cannot create duplicate release");
   ok(listener.fail==0&&context.wm.view!=null,"transient missing snapshot does not close panel");
   ok(!buttons[0].enabled&&!wheel.enabled,"controls blocked while state missing");
   ctrl.missing=false;SystemClock.now+=50;Handler.runPending();
   ok(listener.fail==0&&buttons[0].enabled&&wheel.enabled,"controls restored after validated snapshot");
   wheel.event(android.view.MotionEvent.ACTION_DOWN);ok(ctrl.presses==3,"next valid press");
   SystemClock.now+=500;Handler.runPending();ok(ctrl.releases==3,"watchdog releases lost touch");
   ok(!wheel.pressed,"watchdog clears pressed visual");
   wheel.event(android.view.MotionEvent.ACTION_UP);ok(ctrl.releases==3,"late up does not double release");
   wheel.event(android.view.MotionEvent.ACTION_DOWN);ok(wheel.pressed,"next touch shows press");
   wheel.event(android.view.MotionEvent.ACTION_CANCEL);ok(!wheel.pressed&&ctrl.releases==4,"cancel clears press and releases once");
   ctrl.missing=true;SystemClock.now+=250;Handler.runPending();SystemClock.now+=801;Handler.runPending();
   ok(listener.fail==1&&context.wm.view==null,"prolonged missing snapshot fails closed");
  } catch(Exception ex){throw new AssertionError(ex);}
  ctrl.missing=false;panel.dispose();ok(context.wm.view==null,"dispose removes window");
  Context timeoutContext=new Context();L timeoutListener=new L();
  ResearchOverlay timeout=ResearchOverlay.beginAsync(timeoutContext,ctrl,timeoutListener,1000);ok(timeout!=null,"timeout staged");
  timeout.markSuppressionCommitted();SystemClock.now+=1001;Handler.runPending();
  ok(timeoutListener.fail==1&&timeoutContext.wm.view==null,"attachment timeout reports failure and removes view");
  System.out.println("AsyncOverlay fake late-attachment and timeout tests passed; no device I/O");
 }
}
