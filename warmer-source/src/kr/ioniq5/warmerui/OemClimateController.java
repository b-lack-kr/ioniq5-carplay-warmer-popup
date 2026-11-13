package kr.ioniq5.warmerui;

import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.lang.reflect.Method;

/** Research-only adapter for the pinned 260902 ClimateManager. Not installed.
 * Evidence of raw callbacks and a separately verified safety policy are required;
 * this class does not provide either or register a vehicle listener by itself. */
public final class OemClimateController implements ResearchOverlay.Controller {
    public interface Proof {
        /** Must validate binary identity, exact manager owner and current connection. */
        boolean compatibleAndConnected(Object manager);
        /** Must verify same CarPlay session/foreground, IGN, screen, camera, call and popup state. */
        boolean safeDisplay(Object manager);
        /** Must prove the panel is visually above the current CarPlay surface. */
        boolean overlayVisible(Object manager, View root);
        /** Must prove both state sets are current despite change-only callbacks. */
        boolean stateAuthoritative(Object manager,long frontAt,long wheelAt,long now);
        /** Must independently prove the wheel press line is released. A void OEM
         * setter returning normally is NOT proof: it swallows Binder failures. */
        boolean wheelReleaseConfirmed(Object manager);
        /** Request guarded OEM recovery, deferred when camera/call/screen is unsafe. */
        void panelFailed(Object manager);
    }
    private final Object manager; private final Proof proof;
    private final Method[] reads = new Method[8]; private final Method driver,passenger,wheel;
    private int[] front,steering; private long frontTime=-1,steeringTime=-1; private boolean down;
    private ResearchOverlay.Snapshot lastSnapshot;
    /**
     * exactClass is passed by the pinned hook resolver; class-name identity alone
     * is not a firmware check. No connection or vehicle read occurs here.
     */
    public OemClimateController(Object owner, Class<?> exactClass, Proof proof) throws Exception {
        if(owner==null||exactClass==null||owner.getClass()!=exactClass||proof==null)
            throw new IllegalArgumentException("unverified owner");
        this.manager=owner;this.proof=proof;
        String[] names={"mH","mI","mJ","mK","mT","mU","mV","ni"};
        for(int i=0;i<names.length;i++) {
            reads[i]=exactClass.getMethod(names[i]);
            if(reads[i].getReturnType()!=(i==7?Boolean.TYPE:Integer.TYPE))throw new NoSuchMethodException(names[i]);
        }
        driver=setter(exactClass,"bJ");passenger=setter(exactClass,"bK");wheel=setter(exactClass,"bL");
    }
    private static Method setter(Class<?> c,String name)throws Exception {
        Method m=c.getMethod(name,Integer.TYPE);if(m.getReturnType()!=Void.TYPE)throw new NoSuchMethodException(name);return m;
    }
    private boolean compatible(){try{return proof.compatibleAndConnected(manager);}catch(Throwable e){return false;}}
    public boolean safeNow(){try{return Looper.myLooper()==Looper.getMainLooper()&&compatible()&&proof.safeDisplay(manager);}catch(Throwable e){return false;}}
    public boolean overlayVerified(View root){try{return root!=null&&safeNow()&&proof.overlayVisible(manager,root);}catch(Throwable e){return false;}}
    public void onPanelFailure(){try{proof.panelFailed(manager);}catch(Throwable ignored){}}
    /** Call only after the pinned OEM onFrontSeatHeatStatusReceived succeeds.
     * The hook must prove its j.this.l references this exact manager. */
    public synchronized void observeFront(Object exactOwner,int dOpt,int pOpt,int dState,int pState) {
        observeFrontAt(exactOwner,dOpt,pOpt,dState,pState,SystemClock.elapsedRealtime());
    }
    /** Preserve the original callback time when importing a registry snapshot. */
    public synchronized void observeFrontAt(Object exactOwner,int dOpt,int pOpt,int dState,int pState,long at) {
        lastSnapshot=null;
        if(exactOwner!=manager||!compatible()||at<0||at>SystemClock.elapsedRealtime()||dOpt<1||dOpt>6||pOpt<1||pOpt>6||dState<2||dState>8||pState<2||pState>8) {
            front=null;frontTime=-1;return;
        }
        front=new int[]{dOpt,pOpt,dState,pState};frontTime=at;
    }
    /** Call only after pinned OEM onSWRCStatusReceived succeeds and same-owner proof. */
    public synchronized void observeWheel(Object exactOwner,int opt,int sw,int ignoredStep,int indicator) {
        observeWheelAt(exactOwner,opt,sw,ignoredStep,indicator,SystemClock.elapsedRealtime());
    }
    /** Preserve the original callback time when importing a registry snapshot. */
    public synchronized void observeWheelAt(Object exactOwner,int opt,int sw,int ignoredStep,int indicator,long at) {
        lastSnapshot=null;
        if(exactOwner!=manager||!compatible()||at<0||at>SystemClock.elapsedRealtime()||opt<0||opt>1||sw<0||sw>1||indicator<0||indicator>1) {
            steering=null;steeringTime=-1;return;
        }
        steering=new int[]{opt,sw,indicator};steeringTime=at;
    }
    private int read(int i)throws Exception {return ((Integer)reads[i].invoke(manager)).intValue();}
    private boolean authoritative(long now){
        try{return frontTime>=0&&steeringTime>=0&&now>=frontTime&&now>=steeringTime
                &&proof.stateAuthoritative(manager,frontTime,steeringTime,now);}
        catch(Throwable e){return false;}
    }
    public synchronized ResearchOverlay.Snapshot snapshot() {
        lastSnapshot=null;if(!safeNow())return null;long now=SystemClock.elapsedRealtime();
        if(front==null||steering==null||!authoritative(now))return null;
        try {
            for(int i=0;i<4;i++)if(read(i)!=front[i])return null;
            if(read(4)!=steering[0]||read(5)!=steering[1]||read(6)!=steering[2])return null;
            boolean ascending=!((Boolean)reads[7].invoke(manager)).booleanValue();
            lastSnapshot=new ResearchOverlay.Snapshot(Math.min(frontTime,steeringTime),front[0],front[1],front[2],front[3],
                    steering[0]==1&&steering[1]==1,steering[2]==1,ascending);
            return lastSnapshot;
        }catch(Throwable e){return null;}
    }
    public synchronized boolean snapshotValidated(ResearchOverlay.Snapshot s){
        return s!=null&&s==lastSnapshot&&safeNow()&&authoritative(SystemClock.elapsedRealtime());
    }
    public synchronized boolean setSeat(boolean isDriver,int target) {
        ResearchOverlay.Snapshot s=snapshot();if(s==null)return false;
        int option=isDriver?s.driverOption:s.passengerOption, state=isDriver?s.driverState:s.passengerState;
        int heat=SeatCycle.next(option,SeatCycle.Mode.HEAT,state,true,s.ascending);
        int vent=SeatCycle.next(option,SeatCycle.Mode.VENT,state,true,s.ascending);
        if(target<0 || (target!=heat&&target!=vent) || !snapshotValidated(s))return false;
        try{(isDriver?driver:passenger).invoke(manager,target);return true;}catch(Throwable e){return false;}
    }
    public synchronized boolean wheelPress(){
        ResearchOverlay.Snapshot s=snapshot();if(s==null||!s.wheelSupported||down||!snapshotValidated(s))return false;
        down=true;try{wheel.invoke(manager,1);return true;}catch(Throwable e){wheelRelease();return false;}
    }
    /** Release must remain possible when session or display safety is lost. */
    public synchronized boolean wheelRelease(){
        if(!down)return true;
        try{
            wheel.invoke(manager,0);
            if(proof.wheelReleaseConfirmed(manager)){down=false;return true;}
            return false;
        }catch(Throwable ignored){return false;}
    }
    public synchronized void invalidate(){front=null;steering=null;lastSnapshot=null;frontTime=-1;steeringTime=-1;wheelRelease();}
}
