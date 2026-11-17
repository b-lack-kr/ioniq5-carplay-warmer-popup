package kr.ioniq5.warmerui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Experimental OEM-resource overlay. Not wired into an HMG module or installed.
 * The caller must supply independently verified live session, camera, call,
 * climate capability and sensor freshness checks via Controller. This class
 * does not verify firmware signatures, observe the OEM service, or obtain such
 * proofs and cannot safely run against a real car without those components.
 */
public final class ResearchOverlay {
    private static final int OEM_LAYOUT=0x7f0300b7, OEM_REAR_LAYOUT=0x7f0300b6, OEM_MAIN_BG=0x7f060134;
    // Original content is normally hosted inside an AlertDialog. When attached
    // directly as a system overlay it has no dialog chrome or background.
    // Climate's co_bg_popup is a day/night-qualified nine-patch; resolve it
    // from this pinned Climate context rather than shipping its asset.
    private static final int OEM_POPUP_BG=0x7f0203d0;
    private static final int OEM_WHEEL_BUTTON=0x7f0601a3, OEM_BUTTON_LAYOUT=0x7f03000d;
    private static final int HEAT_LED=0x7f0201b5, VENT_LED=0x7f0201c2;
    private static final long REFRESH_MS=200;
    public interface Controller {
        /** Only true for the current authenticated CarPlay session and a safe display. */
        boolean safeNow();
        /** Must return null unless the current state is independently proven authoritative. */
        Snapshot snapshot();
        /** Validate the exact snapshot again immediately before it is used. */
        boolean snapshotValidated(Snapshot snapshot);
        /** Independently prove that the attached panel is visible above the live CarPlay surface. */
        boolean overlayVerified(View root);
        boolean setSeat(boolean driver, int state);
        default boolean setRearSeat(boolean left, boolean heat,int state){return false;}
        default boolean refusalDefinitelyNotSent(){return false;}
        default boolean controlsAvailable(){return true;}
        boolean wheelPress();
        /** True when the release request was accepted by the transport. This
         * is not a physical switch/actuator acknowledgement. */
        boolean wheelRelease();
        /** Notify the hook to consider safe, one-shot OEM recovery; never run it blindly. */
        void onPanelFailure();
        default void onClosed(){}
    }
    /** Main-thread callbacks; a future hook must bind them to one owner/session. */
    public interface AsyncListener { void ready(); void failed(); void closedByUser(); }
    public static final class Snapshot {
        public final long atElapsed; public final int driverOption, passengerOption, driverState, passengerState;
        public final boolean wheelSupported, wheelOn, ascending; public final int[] rear;
        public Snapshot(long at, int dOpt, int pOpt, int dState, int pState,
                        boolean wheelSupported, boolean wheelOn, boolean ascending) {
            this(at,dOpt,pOpt,dState,pState,wheelSupported,wheelOn,ascending,null);
        }
        public Snapshot(long at,int dOpt,int pOpt,int dState,int pState,boolean supported,boolean on,boolean asc,int[] rear){
            atElapsed=at;driverOption=dOpt;passengerOption=pOpt;driverState=dState;passengerState=pState;wheelSupported=supported;wheelOn=on;ascending=asc;
            this.rear=rear==null?null:rear.clone();
        }
    }
    private final Controller controller; private final WindowManager wm; private final Handler main;
    private final View root, wheel; private final View[] seats=new View[4], rearSeats=new View[4]; private final boolean hasRear; private final Method paint, paintWheel;
    private static final long WHEEL_RELEASE_WATCHDOG_MS=500;
    private final Runnable wheelReleaseWatchdog=new Runnable(){public void run(){
        if(wheelDown&&!releaseAttempted){
            try{android.util.Log.i("WARMER_DIAG","wheel_watchdog=release_requested");}catch(Throwable ignored){}
            releaseWheel();
        }
    }};
    private boolean live, wheelDown, releaseAttempted, committed, failureReported; private int pendingDriver=-1, pendingPassenger=-1; private final int[] rearPending={-1,-1}; private final long[] rearSent={0,0};
    private AsyncListener asyncListener; private Runnable attachPoll; private long attachDeadline;
    private long driverSent, passengerSent, snapshotMissingSince=-1; private boolean paintedOnce; private final Runnable refresh = new Runnable() {
        public void run() { if (!live) return; try { update(); if (live) main.postDelayed(this, (hasPendingSeat() || snapshotMissingSince>=0) ? 50 : REFRESH_MS); }
        catch(Throwable failure) { fail("refresh_exception"); } }
    };
    private ResearchOverlay(Context context, Controller controller, boolean hasRear, Snapshot initial) throws Exception {
        this.hasRear=hasRear;
        this.controller=controller; this.main=new Handler(Looper.getMainLooper());
        Object service=context.getSystemService(Context.WINDOW_SERVICE);
        if (!(service instanceof WindowManager)) throw new IllegalStateException("window service");
        wm=(WindowManager)service;
        View content=LayoutInflater.from(context).inflate(hasRear?OEM_REAR_LAYOUT:OEM_LAYOUT, null);
        View frontLabel=content.findViewById(0x7f060241);
        Typeface stockFace=frontLabel instanceof TextView ? ((TextView)frontLabel).getTypeface() : null;
        // The stock dialog provides a title and full-width confirmation control
        // outside the OEM seat layout. Keep the OEM layout itself untouched.
        RelativeLayout chrome=new RelativeLayout(context);
        int cLeft=chrome.getPaddingLeft(), cTop=chrome.getPaddingTop(), cRight=chrome.getPaddingRight(), cBottom=chrome.getPaddingBottom();
        chrome.setBackgroundResource(OEM_POPUP_BG);
        chrome.setPadding(cLeft,cTop,cRight,cBottom);
        int header=dp(context,60), footer=dp(context,51);
        RelativeLayout.LayoutParams contentLp=new RelativeLayout.LayoutParams(-1,-1);
        contentLp.topMargin=header; contentLp.bottomMargin=footer;
        chrome.addView(content,contentLp);
        TextView title=new TextView(context);
        title.setTypeface(stockFace,Typeface.BOLD);
        title.getPaint().setFakeBoldText(true);
        // The vehicle's Korean fallback font does not visibly thicken enough
        // with Typeface.BOLD alone; add a small stroke to match the OEM title.
        title.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
        title.getPaint().setStrokeWidth(context.getResources().getDisplayMetrics().density * 0.65f);
        title.setText("열선/통풍"); title.setTextSize(26); title.setGravity(Gravity.CENTER);
        title.setTextColor(context.getResources().getColor(0x7f07001f));
        RelativeLayout.LayoutParams titleLp=new RelativeLayout.LayoutParams(-1,header);
        titleLp.addRule(RelativeLayout.ALIGN_PARENT_TOP); chrome.addView(title,titleLp);
        root=chrome;
        View mainBg=content.findViewById(OEM_MAIN_BG);
        if (!(mainBg instanceof RelativeLayout)) throw new IllegalStateException("OEM layout");
        wheel=root.findViewById(OEM_WHEEL_BUTTON);
        if (wheel==null) throw new IllegalStateException("OEM wheel button");
        ClassLoader cl=context.getClassLoader();
        Class<?> cfgClass=Class.forName("com.hkmc.telematics.service.apps.climate.widget.h", false, cl);
        Class<?> buttonClass=Class.forName("com.hkmc.telematics.service.apps.climate.widget.FuncButton", false, cl);
        if(!buttonClass.isInstance(wheel)) throw new IllegalStateException("OEM wheel class");
        paintWheel=buttonClass.getMethod("setChecked",boolean.class);
        Constructor<?> cfgCtor=cfgClass.getConstructor(int.class);
        Constructor<?> btnCtor=buttonClass.getConstructor(Context.class,cfgClass);
        Method layout=cfgClass.getMethod("av",int.class), icon=cfgClass.getMethod("ax",int.class), led=cfgClass.getMethod("az",int.class);
        paint=buttonClass.getMethod("a",boolean.class,int.class,boolean.class);
        int[] ids={0x7f06001c,0x7f06001d,0x7f06001e,0x7f06001f};
        int[] icons={0x7f0202fb,0x7f0202fa,0x7f0202fd,0x7f0202fc};
        RelativeLayout container=(RelativeLayout)mainBg;
        for (int i=0;i<4;i++) {
            Object cfg=cfgCtor.newInstance(ids[i]); layout.invoke(cfg,OEM_BUTTON_LAYOUT);
            icon.invoke(cfg,icons[i]); led.invoke(cfg,(i%2==0)?HEAT_LED:VENT_LED);
            Object obj=btnCtor.newInstance(context,cfg);
            if (!(obj instanceof View)) throw new IllegalStateException("OEM button");
            final int which=i; View view=(View)obj; seats[i]=view;
            RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(-2,-2);
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            lp.leftMargin=hasRear?(i<2?21:327):(i<2?100:406);
            int option=i<2?initial.driverOption:initial.passengerOption;
            boolean both=option==5||option==6;lp.topMargin=both?(i%2==0?84:297):183;
            container.addView(view,lp);
            view.setOnClickListener(new View.OnClickListener(){public void onClick(View ignored){seat(which);}});
        }
        if(hasRear) {
            int[] rearIds={0x7f060020,0x7f060021,0x7f060022,0x7f060023};
            for(int i=0;i<4;i++) {
                Object cfg=cfgCtor.newInstance(rearIds[i]);layout.invoke(cfg,OEM_BUTTON_LAYOUT);
                icon.invoke(cfg,icons[i]);led.invoke(cfg,(i%2==0)?HEAT_LED:VENT_LED);
                View view=(View)btnCtor.newInstance(context,cfg);rearSeats[i]=view;
                RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(-2,-2);
                lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);lp.leftMargin=i<2?546:771;
                int option=initial.rear[i/2];
                // Two-step option: call the stock FuncButton visibility helper.
                if(option==1 || option==5)buttonClass.getMethod("ay").invoke(view);
                boolean both=option==5||option==6;lp.topMargin=both?(i%2==0?84:297):183;
                container.addView(view,lp);final int index=i;
                view.setOnClickListener(new View.OnClickListener(){public void onClick(View v){rearSeat(index);}});
            }
        }
        wheel.setOnTouchListener(new View.OnTouchListener(){ public boolean onTouch(View v,MotionEvent e) {
            if (e==null) return true;
            switch(e.getActionMasked()) {
                // Consuming the touch bypasses View.onTouchEvent's normal
                // pressed drawable state. Reuse the OEM day/night selector;
                // this is touch feedback, never an optimistic heater state.
                case MotionEvent.ACTION_DOWN: pressWheel(); v.setPressed(live && wheelDown && !releaseAttempted); return true;
                case MotionEvent.ACTION_MOVE:
                    v.setPressed(live && wheelDown && !releaseAttempted && e.getX()>=0 && e.getY()>=0
                        && e.getX()<v.getWidth() && e.getY()<v.getHeight());
                    return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: releaseWheel(); return true;
                default: return true;
            }
        }});
        Button close=new Button(context); close.setText("확인");
        close.setTypeface(stockFace,Typeface.BOLD);
        close.getPaint().setFakeBoldText(true);
        close.setTextSize(24);
        close.setTextColor(context.getResources().getColor(0x7f07001f));
        close.setBackgroundResource(0x7f0203da); // OEM day/night bottom-button selector
        RelativeLayout.LayoutParams closeLp=new RelativeLayout.LayoutParams(-1,footer);
        closeLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        closeLp.leftMargin=dp(context,8);closeLp.rightMargin=dp(context,8);closeLp.bottomMargin=dp(context,8);
        chrome.addView(close,closeLp);close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            AsyncListener l=asyncListener;if(l!=null)try{l.closedByUser();}catch(Throwable ignored){}dispose();
        }});
        if (root.findViewById(0x7f060241) instanceof TextView)
            ((TextView)root.findViewById(0x7f060241)).setText("열선 및 통풍");
    }
    private static int dp(Context c,int v){return (int)(v*c.getResources().getDisplayMetrics().density+0.5f);}
    private boolean valid(Snapshot s) {
        long now=SystemClock.elapsedRealtime();
        return s!=null && s.atElapsed>=0 && now>=s.atElapsed && controller.snapshotValidated(s)
            && s.driverOption>=1 && s.driverOption<=6 && s.passengerOption>=1 && s.passengerOption<=6
            && s.driverState>=2 && s.driverState<=8 && s.passengerState>=2 && s.passengerState<=8;
    }
    private Snapshot safeSnapshot() {
        try {if (!live || !controller.safeNow()) return null; Snapshot s=controller.snapshot();return valid(s)?s:null;}
        catch(Throwable unavailable){return null;}
    }
    // During an outstanding command, paint confirmed status sooner; never
    // display a requested value until the OEM state confirms it.
    private boolean hasPendingSeat(){return pendingDriver>=0 || pendingPassenger>=0 || rearPending[0]>=0 || rearPending[1]>=0;}
    private void requestPendingRefresh(){if(live && hasPendingSeat()){main.removeCallbacks(refresh);main.postDelayed(refresh,50);}}
    private void update() throws Exception {
        Snapshot s=safeSnapshot();
        long now=SystemClock.elapsedRealtime();
        if(s==null){
            // Keep only already-painted pixels briefly while a state callback
            // invalidates the cache. Never accept control input while blind.
            if(paintedOnce && now>=0 && (snapshotMissingSince<0 || now>=snapshotMissingSince)) {
                if(snapshotMissingSince<0) snapshotMissingSince=now;
                if(now-snapshotMissingSince<=800) {
                    for(View v:seats) if(v!=null)v.setEnabled(false);
                    for(View v:rearSeats) if(v!=null)v.setEnabled(false);
                    setWheelEnabled(false);
                    return;
                }
            }
            fail("refresh_snapshot");return;
        }
        snapshotMissingSince=-1;paintedOnce=true;
        if(pendingDriver>=0 && s.driverState==pendingDriver) pendingDriver=-1;
        if(pendingPassenger>=0 && s.passengerState==pendingPassenger) pendingPassenger=-1;
        // No acknowledgement is ambiguous: do not automatically allow another
        // command against potentially stale state. Close; recovery is external.
        if((pendingDriver>=0&&now-driverSent>2000)||(pendingPassenger>=0&&now-passengerSent>2000)){
            fail();return;
        }
        boolean controls=controller.controlsAvailable();
        for(int i=0;i<4;i++) {
            boolean driver=i<2, heat=(i%2)==0; int opt=driver?s.driverOption:s.passengerOption;
            int current=driver?s.driverState:s.passengerState;
            int next=SeatCycle.next(opt,heat?SeatCycle.Mode.HEAT:SeatCycle.Mode.VENT,current,true,s.ascending);
            seats[i].setVisibility(next<0?View.INVISIBLE:View.VISIBLE);
            seats[i].setEnabled(controls && next>=0 && (driver?pendingDriver:pendingPassenger)<0);
            if(next>=0) paint.invoke(seats[i],heat,current, heat?(opt==1||opt==5):(opt==3||opt==5));
        }
        if(hasRear) {
            if(s.rear==null||s.rear.length!=4){fail();return;}
            for(int side=0;side<2;side++)if(rearPending[side]>=0&&s.rear[2+side]==rearPending[side])rearPending[side]=-1;
            for(int side=0;side<2;side++)if(rearPending[side]>=0&&now-rearSent[side]>2000){fail();return;}
            for(int i=0;i<4;i++) {
                int side=i/2,opt=s.rear[side],current=s.rear[2+side];boolean heat=i%2==0;
                int next=SeatCycle.next(opt,heat?SeatCycle.Mode.HEAT:SeatCycle.Mode.VENT,current,true,s.ascending);
                rearSeats[i].setVisibility(next<0?View.INVISIBLE:View.VISIBLE);
                rearSeats[i].setEnabled(controls&&next>=0&&rearPending[side]<0);
                if(next>=0)paint.invoke(rearSeats[i],heat,current,heat?(opt==1||opt==5):(opt==3||opt==5));
            }
        }
        setWheelEnabled(controls&&s.wheelSupported);wheel.setVisibility(s.wheelSupported?View.VISIBLE:View.INVISIBLE);
        if(s.wheelSupported) paintWheel.invoke(wheel,s.wheelOn);
    }
    private void seat(int i) {
        Snapshot s=safeSnapshot();if(s==null){fail();return;}
        boolean driver=i<2,heat=(i%2)==0;
        if((driver?pendingDriver:pendingPassenger)>=0)return;
        int target=SeatCycle.next(driver?s.driverOption:s.passengerOption,heat?SeatCycle.Mode.HEAT:SeatCycle.Mode.VENT,
                driver?s.driverState:s.passengerState,true,s.ascending);
        if(target<0)return;
        try {
            if(!controller.safeNow()) {fail();return;}
            // A command may reach the vehicle before an error is returned. Mark
            // it pending first so a failed/ambiguous response cannot be resent
            // by another tap until state confirmation or timeout.
            if(driver){pendingDriver=target;driverSent=SystemClock.elapsedRealtime();}
            else {pendingPassenger=target;passengerSent=SystemClock.elapsedRealtime();}
            if(!controller.setSeat(driver,target)&&controller.refusalDefinitelyNotSent()){if(driver)pendingDriver=-1;else pendingPassenger=-1;}
            update();requestPendingRefresh();
        } catch(Throwable e){fail();}
    }
    private void rearSeat(int index){
        Snapshot s=safeSnapshot();if(s==null||s.rear==null||s.rear.length!=4){fail();return;}
        int side=index/2;boolean heat=index%2==0;if(rearPending[side]>=0)return;
        int target=SeatCycle.next(s.rear[side],heat?SeatCycle.Mode.HEAT:SeatCycle.Mode.VENT,s.rear[side+2],true,s.ascending);if(target<0)return;
        try{if(!controller.safeNow()){fail();return;}rearPending[side]=target;rearSent[side]=SystemClock.elapsedRealtime();if(!controller.setRearSeat(side==0,heat,target)&&controller.refusalDefinitelyNotSent())rearPending[side]=-1;update();requestPendingRefresh();}catch(Throwable e){fail();}
    }
    private void setWheelEnabled(boolean enabled){
        if(!enabled)wheel.setPressed(false);
        // Disabled Android views may stop delivering ACTION_UP. Preserve the
        // OEM down/up pair before disabling any active touch target.
        if(!enabled&&wheelDown&&!releaseAttempted){
            try{android.util.Log.i("WARMER_DIAG","wheel_release=before_disable");}catch(Throwable ignored){}
            releaseWheel();
        }
        wheel.setEnabled(enabled);
    }
    private void pressWheel() {
        Snapshot s=safeSnapshot();if(s==null){fail("wheel_snapshot");return;}if(!s.wheelSupported||wheelDown)return;
        try {
            if (!controller.safeNow()) return;
            // Arm release *before* the call: the controller might send DOWN
            // and then throw or report failure before returning.
            wheelDown=true;releaseAttempted=false;
            if (!controller.wheelPress()) {
                releaseWheel();
                if(controller.refusalDefinitelyNotSent())wheelDown=false;
            } else if(!main.postDelayed(wheelReleaseWatchdog,WHEEL_RELEASE_WATCHDOG_MS)) {
                releaseWheel();
            }
        } catch(Throwable e){releaseWheel();fail("wheel_exception");}
    }
    private void releaseWheel(){
        wheel.setPressed(false);
        if(!wheelDown||releaseAttempted)return;
        main.removeCallbacks(wheelReleaseWatchdog);
        // The OEM setter has no actuator ACK. Attempt only once; a failed IPC
        // can still have been delivered and repeating may have unknown effects.
        releaseAttempted=true;
        boolean releaseTransportCompleted=false;
        try{releaseTransportCompleted=controller.wheelRelease();}catch(Throwable ignored){}
        if(releaseTransportCompleted)wheelDown=false;
    }
    public boolean wheelReleaseTransportPending(){return wheelDown;}
    public boolean attachedAndUsable(){
        try{return live && root.getWindowToken()!=null && safeSnapshot()!=null && controller.overlayVerified(root);}
        catch(Throwable noProof){return false;}
    }
    /** Hook calls this only after it has successfully suppressed OEM mD(). */
    public void markSuppressionCommitted(){committed=true;if(asyncListener==null&&!attachedAndUsable())fail();}
    private void fail(){fail("other");}
    private void fail(String reason){
        try { android.util.Log.i("WARMER_DIAG", "panel_failure="+reason); } catch(Throwable ignored) {}
        dispose();
        if(!failureReported){failureReported=true;
            if(asyncListener!=null)try{asyncListener.failed();}catch(Throwable ignored){}
            else if(committed)try{controller.onPanelFailure();}catch(Throwable ignored){}
        }
    }
    public void dispose(){if(!live)return;live=false;main.removeCallbacks(refresh);main.removeCallbacks(wheelReleaseWatchdog);if(attachPoll!=null)main.removeCallbacks(attachPoll);releaseWheel();try{controller.onClosed();}catch(Throwable ignored){}try{wm.removeViewImmediate(root);}catch(Throwable ignored){}}
    /** Schedule the actual Android attachment and check only on a later main
     * loop iteration. A window token alone is not a proof of video z-order. */
    public static ResearchOverlay beginAsync(Context c,Controller controller,AsyncListener listener,long timeoutMs){
        if(c==null||controller==null||listener==null||timeoutMs<1||timeoutMs>10000||Looper.myLooper()!=Looper.getMainLooper())return null;
        ResearchOverlay panel=null;
        try {
            if(!controller.safeNow())return null;
            Snapshot initial=controller.snapshot();if(initial==null||!controller.snapshotValidated(initial))return null;
            boolean useRear=initial.rear!=null&&initial.rear.length==4&&(initial.rear[0]>=1&&initial.rear[0]<=6||initial.rear[1]>=1&&initial.rear[1]<=6);
            panel=new ResearchOverlay(c,controller,useRear,initial);final ResearchOverlay p=panel;p.asyncListener=listener;
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(dp(c,p.hasRear?648:452),dp(c,414),WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
            lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.y=dp(c,33);p.live=true;p.wm.addView(p.root,lp);p.update();if(!p.live)return null;
            long now=SystemClock.elapsedRealtime();if(now<0||now>Long.MAX_VALUE-timeoutMs){p.dispose();return null;}
            p.attachDeadline=now+timeoutMs;
            p.attachPoll=new Runnable(){public void run(){
                if(!p.live)return;
                try {
                    if(!p.controller.safeNow()){p.fail();return;}
                    if(p.attachedAndUsable()){
                        p.attachPoll=null;p.main.postDelayed(p.refresh,REFRESH_MS);
                        p.asyncListener.ready();return;
                    }
                    long now=SystemClock.elapsedRealtime();
                    if(now<0||now>=p.attachDeadline||!p.main.postDelayed(this,16)){p.fail();}
                }catch(Throwable error){p.fail();}
            }};
            if(!p.main.postDelayed(p.attachPoll,16)){p.dispose();return null;}
            return p;
        }catch(Throwable error){if(panel!=null)panel.dispose();return null;}
    }
    /** Returns null on any failure; it never changes another window's state. */
    public static ResearchOverlay attach(Context c,Controller controller) {
        if(c==null||controller==null||Looper.myLooper()!=Looper.getMainLooper())return null;
        ResearchOverlay panel=null;
        try {
            if(!controller.safeNow())return null;
            Snapshot initial=controller.snapshot();if(initial==null||!controller.snapshotValidated(initial))return null;
            panel=new ResearchOverlay(c,controller,false,initial);
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(c,452),dp(c,414),
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            p.gravity=Gravity.CENTER; panel.live=true; panel.wm.addView(panel.root,p); panel.update();
            if(!panel.attachedAndUsable()){panel.dispose();return null;}
            panel.main.postDelayed(panel.refresh,REFRESH_MS);return panel;
        } catch(Throwable failure) { if(panel!=null)panel.dispose();return null; }
    }
}
