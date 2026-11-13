package kr.ioniq5.warmerhook;

import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import kr.ioniq5.warmerui.CallbackRegistry;
import kr.ioniq5.warmerui.OemClimateController;
import kr.ioniq5.warmerui.RegistryController;
import kr.ioniq5.warmerui.ResearchOverlay;

/** Research-only factory. WarmerHook deliberately does not instantiate it. */
final class ResearchCallbackFactory implements ResearchUiBridge.Factory {
    interface ProofFactory {
        boolean verifiedAndReady();
        OemClimateController.Proof proof(Object owner);
    }
    private final Class<?> ownerClass;
    private final Field contextField;
    private final CallbackRegistry callbacks;
    private final ProofFactory proofs;
    ResearchCallbackFactory(Class<?> exactOwner,CallbackRegistry callbacks,ProofFactory proofs)throws Exception {
        if(exactOwner==null||callbacks==null||proofs==null)throw new IllegalArgumentException();
        Field f=exactOwner.getDeclaredField("mContext");
        if(f.getType()!=Context.class||Modifier.isStatic(f.getModifiers()))throw new NoSuchFieldException("mContext");
        f.setAccessible(true);
        ownerClass=exactOwner;contextField=f;this.callbacks=callbacks;this.proofs=proofs;
    }
    private boolean ownerOk(Object owner){return owner!=null&&owner.getClass()==ownerClass;}
    public boolean verifiedAndReady(){try{return proofs.verifiedAndReady();}catch(Throwable e){return false;}}
    public Context climateContext(Object owner){
        try{return ownerOk(owner)?(Context)contextField.get(owner):null;}catch(Throwable e){return null;}
    }
    public ResearchOverlay.Controller controller(Object owner){
        try {
            if(!ownerOk(owner)||!verifiedAndReady())return null;
            OemClimateController.Proof p=proofs.proof(owner);if(p==null)return null;
            return new RegistryController(owner,callbacks,new OemClimateController(owner,ownerClass,p));
        }catch(Throwable e){return null;}
    }
}
