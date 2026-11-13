package kr.ioniq5.warmerui;

import android.view.View;

/** Research-only adapter; does not register hooks or provide safety proofs. */
public final class RegistryController implements ResearchOverlay.Controller {
    private final Object owner;
    private final CallbackRegistry callbacks;
    private final OemClimateController target;
    private long frontSequence=-1, wheelSequence=-1;

    public RegistryController(Object owner, CallbackRegistry callbacks, OemClimateController target) {
        if(owner==null||callbacks==null||target==null)throw new IllegalArgumentException();
        this.owner=owner;this.callbacks=callbacks;this.target=target;
    }
    /** Callback timestamps must not be refreshed merely by reading a snapshot. */
    private synchronized boolean sync() {
        try {
            CallbackRegistry.Pair p=callbacks.snapshot(owner);
            if(p==null){frontSequence=wheelSequence=-1;target.invalidate();return false;}
            if(p.front.sequence!=frontSequence) {
                int[] x=p.front.values();target.observeFrontAt(owner,x[0],x[1],x[2],x[3],p.front.atElapsed);
                frontSequence=p.front.sequence;
            }
            if(p.wheel.sequence!=wheelSequence) {
                int[] x=p.wheel.values();target.observeWheelAt(owner,x[0],x[1],x[2],x[3],p.wheel.atElapsed);
                wheelSequence=p.wheel.sequence;
            }
            return true;
        } catch(Throwable e){frontSequence=wheelSequence=-1;target.invalidate();return false;}
    }
    public boolean safeNow(){return target.safeNow();}
    public synchronized ResearchOverlay.Snapshot snapshot(){return sync()?target.snapshot():null;}
    public synchronized boolean snapshotValidated(ResearchOverlay.Snapshot s){
        try {
            CallbackRegistry.Pair p=callbacks.snapshot(owner);
            return p!=null && p.front.sequence==frontSequence && p.wheel.sequence==wheelSequence
                    && target.snapshotValidated(s);
        }catch(Throwable e){return false;}
    }
    public boolean overlayVerified(View root){return target.overlayVerified(root);}
    public synchronized boolean setSeat(boolean driver,int state){return sync()&&target.setSeat(driver,state);}
    public synchronized boolean wheelPress(){return sync()&&target.wheelPress();}
    /** A release is allowed even after callback, display or session loss. */
    public boolean wheelRelease(){return target.wheelRelease();}
    public void onPanelFailure(){target.onPanelFailure();}
}
