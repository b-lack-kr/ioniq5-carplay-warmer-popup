package kr.ioniq5.warmerhook;

import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import kr.ioniq5.warmerui.CallbackRegistry;
import kr.ioniq5.warmerui.ResearchOverlay;
import kr.ioniq5.warmerui.SeatCycle;

/** Explicitly experimental, stock-cache-equivalent control adapter. A void
 * Binder return is transport completion, never an actuator acknowledgement.
 * Displayed state is updated only by OEM status callbacks/cache bootstrap. */
final class CandidateController implements ResearchOverlay.Controller {
    interface Proof {
        boolean safe(Object owner,Object session);
        /** Window attachment/visible bounds only; cannot prove GPU z-order. */
        boolean visible(View root);
    }
    // Category-only diagnostics: no VIN, identifiers, location, or raw state.
    private static String diagnosticLast=""; private static long diagnosticAt=-1;
    static synchronized void diagnostic(String reason) {
        try {
            long now=SystemClock.elapsedRealtime();
            if (!reason.equals(diagnosticLast) || diagnosticAt<0 || now<diagnosticAt || now-diagnosticAt>=2000) {
                android.util.Log.i("WARMER_DIAG",reason); diagnosticLast=reason; diagnosticAt=now;
            }
        } catch(Throwable ignored) { }
    }
    private final Object owner,session,service;
    private final CallbackRegistry registry;private final AndroidCacheSource source;private final Proof proof;
    private long frontSeq=-1,wheelSeq=-1;private ResearchOverlay.Snapshot last;private boolean wheelDown;
    CandidateController(Object owner,Object session,Object service,CallbackRegistry registry,AndroidCacheSource source,Proof proof){
        if(owner==null||session==null||service==null||registry==null||source==null||proof==null)throw new IllegalArgumentException();
        this.owner=owner;this.session=session;this.service=service;this.registry=registry;this.source=source;this.proof=proof;
    }
    public boolean safeNow(){try{return proof.safe(owner,session)&&source.unchanged(service);}catch(Throwable e){return false;}}
    public boolean overlayVerified(View root){try{return root!=null&&safeNow()&&proof.visible(root);}catch(Throwable e){return false;}}
    public void onPanelFailure(){} // asynchronous coordinator owns one-shot fallback
    public synchronized ResearchOverlay.Snapshot snapshot(){
        last=null;if(!safeNow()){diagnostic("snapshot_reject=safety_or_service");return null;}
        try{
            CallbackRegistry.Pair p=registry.snapshot(owner);if(p==null){diagnostic("snapshot_reject=pair_"+registry.diagnosticState(owner));return null;}
            int[] f=p.front.values(),w=p.wheel.values(),v=source.managerValues(owner);
            if(v.length!=7||f.length!=4||w.length!=4){diagnostic("snapshot_reject=length");return null;}
            if(v[0]!=f[0]||v[1]!=f[1]||v[2]!=f[2]||v[3]!=f[3]){diagnostic("snapshot_reject=front_mismatch");return null;}
            if(v[4]!=w[0]){diagnostic("snapshot_reject=wheel_option_mismatch");return null;}
            if(v[5]!=w[1]){diagnostic("snapshot_reject=wheel_switch_mismatch");return null;}
            if(v[6]!=w[3]){diagnostic("snapshot_reject=wheel_indicator_mismatch");return null;}
            Method ni=owner.getClass().getMethod("ni");if(ni.getReturnType()!=Boolean.TYPE){diagnostic("snapshot_reject=direction_type");return null;}
            boolean ascending=!Boolean.TRUE.equals(ni.invoke(owner));
            frontSeq=p.front.sequence;wheelSeq=p.wheel.sequence;
            int[] rear=new RearSeatCandidate(owner,service,source).stableCache();
            if(registry.rearInvalid(owner))rear=null;
            CallbackRegistry.Sample rearEvent=registry.rearSnapshot(owner);
            if(rearEvent!=null&&rear!=null&&!java.util.Arrays.equals(rearEvent.values(),rear))rear=null;
            if(rear!=null&&(rear.length!=4||rear[0]<0||rear[0]>6||rear[1]<0||rear[1]>6||rear[2]<0||rear[2]>8||rear[3]<0||rear[3]>8||(rear[0]>0&&rear[2]<2)||(rear[1]>0&&rear[3]<2)))rear=null;
            last=new ResearchOverlay.Snapshot(Math.min(p.front.atElapsed,p.wheel.atElapsed),f[0],f[1],f[2],f[3],w[0]==1&&w[1]==1,w[3]==1,ascending,rear);
            return last;
        }catch(Throwable e){diagnostic("snapshot_reject=exception");return null;}
    }
    public synchronized boolean snapshotValidated(ResearchOverlay.Snapshot s){
        if(s==null||s!=last||!safeNow()){diagnostic("validation_reject=identity_or_safety");return false;}
        try{
            CallbackRegistry.Pair p=registry.snapshot(owner);
            if(p==null){diagnostic("validation_reject=pair_"+registry.diagnosticState(owner));return false;}
            if(p.front.sequence!=frontSeq){diagnostic("validation_reject=front_changed");return false;}
            if(p.wheel.sequence!=wheelSeq){diagnostic("validation_reject=wheel_changed");return false;}
            if(s.rear!=null&&!java.util.Arrays.equals(s.rear,new RearSeatCandidate(owner,service,source).stableCache())){diagnostic("validation_reject=rear_changed");return false;}
            return true;
        } catch(Throwable e){diagnostic("validation_reject=exception");return false;}
    }
    /** Fixed whitelist, same OEM IUSMService methods. A successful return
     * only says the binder call returned without exception. */
    private boolean request(String name,int value){
        if(!(service instanceof AndroidCacheSource.Token))return false;
        if(!("setFrontSeatHeatDrvStep".equals(name)||"setFrontSeatHeatAstStep".equals(name)||"setSWRC_SteeringwheelWarmerSet".equals(name)))return false;
        try{
            if(!source.unchanged(service))return false;
            AndroidCacheSource.Token token=(AndroidCacheSource.Token)service;
            Method m=token.type.getMethod(name,Integer.TYPE);
            if(m.getReturnType()!=Void.TYPE||Modifier.isStatic(m.getModifiers()))return false;
            m.invoke(token.remote,value);
            return source.unchanged(service);
        }catch(Throwable error){return false;}
    }
    public synchronized boolean setSeat(boolean driver,int target){
        ResearchOverlay.Snapshot s=snapshot();if(s==null)return false;
        int opt=driver?s.driverOption:s.passengerOption,state=driver?s.driverState:s.passengerState;
        int heat=SeatCycle.next(opt,SeatCycle.Mode.HEAT,state,true,s.ascending);
        int vent=SeatCycle.next(opt,SeatCycle.Mode.VENT,state,true,s.ascending);
        if((target!=heat&&target!=vent)||target<2||target>8||!snapshotValidated(s))return false;
        return request(driver?"setFrontSeatHeatDrvStep":"setFrontSeatHeatAstStep",target);
    }
    public synchronized boolean setRearSeat(boolean left,boolean heat,int target){
        ResearchOverlay.Snapshot s=snapshot();if(s==null||s.rear==null||!snapshotValidated(s))return false;
        return new RearSeatCandidate(owner,service,source).sendNext(left,heat?SeatCycle.Mode.HEAT:SeatCycle.Mode.VENT,target,s.ascending);
    }
    public synchronized boolean wheelPress(){
        ResearchOverlay.Snapshot s=snapshot();if(s==null||!s.wheelSupported||wheelDown||!snapshotValidated(s))return false;
        wheelDown=true;if(request("setSWRC_SteeringwheelWarmerSet",1))return true;
        // The caller will attempt one release; a failed transaction is ambiguous.
        return false;
    }
    public synchronized boolean wheelRelease(){
        if(!wheelDown)return true;
        if(!request("setSWRC_SteeringwheelWarmerSet",0))return false;
        wheelDown=false;return true; // transport completion only, not hardware ACK
    }
}
