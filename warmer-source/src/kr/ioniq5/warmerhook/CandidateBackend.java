package kr.ioniq5.warmerhook;

import android.content.Context;
import android.view.View;
import hmgapi.hook.ModContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import kr.ioniq5.warmerui.CallbackRegistry;
import kr.ioniq5.warmerui.ResearchOverlay;

/** Experimental CP candidate. Dipo foreground is an approximation, not a
 * verified CarPlay session; visible Android window is not proof of GPU z-order. */
final class CandidateBackend implements AsyncUiOpener.Backend,CandidateController.Proof {
 private final ModContext mod; private final FailClosedModeState.Access mode;
 private final AndroidCacheSource source;private final CallbackRegistry registry;private final Method stock;private final AndroidDipoSession dipo;
 private volatile String gateReason="not_checked";
 void reportGate(String async){try{mod.log("WARMER_GATE async="+async+" last_worker_reason="+gateReason);}catch(Throwable ignored){}}
 private static final class Session {
  final Object owner,service,modeService,dipoService;final StockRecovery recovery;
  Session(Object owner,Object service,Object modeService,Object dipoService){this.owner=owner;this.service=service;this.modeService=modeService;this.dipoService=dipoService;recovery=new StockRecovery(owner,this,this);}
 }
 CandidateBackend(ModContext m,FailClosedModeState.Access a,AndroidCacheSource s,CallbackRegistry r,Method original){mod=m;mode=a;source=s;registry=r;stock=original;dipo=new AndroidDipoSession(original.getDeclaringClass().getClassLoader());}
 // Read-only mode probe, called only by the dedicated foreground worker.
 Boolean foreground(Object owner){
  try{if(!enabled()||owner==null)return null;Object token=mode.service();
   if(token==null||!mode.alive(token)||!mode.sameService(token))return null;
   Boolean shown=mode.flag(token,"isForegroundActivity",FailClosedModeState.DIPO);
   return mode.alive(token)&&mode.sameService(token)?shown:null;
  }catch(Throwable ignored){return null;}
 }
 boolean listenerForOwner(Object listener,Object owner){return registry.listenerForOwner(listener,owner);}
 boolean enabled(){try{return mod.enabled()&&mod.settingBoolean("experimentalOverlay",false);}catch(Throwable e){return false;}}
 public Object session(Object owner){
  try{if(!enabled()){gateReason="disabled";return null;}
   if(registry.snapshot(owner)==null){gateReason="snapshot_missing";return null;}
   int modeGate=FailClosedModeState.diagnostic(mode,owner);if(modeGate!=0){gateReason="mode_"+modeGate;return null;}
   Object service=source.open(),modeService=mode.service(),dipoService=dipo.open();
   if(service==null){gateReason="climate_service_missing";return null;}
   if(modeService==null){gateReason="mode_service_missing";return null;}
   if(dipoService==null){gateReason="dipo_service_missing";return null;}
   if(!dipo.active(dipoService)){gateReason="dipo_not_active";return null;}
   if(!source.unchanged(service)||!mode.alive(modeService)||!mode.sameService(modeService)){gateReason="service_changed";return null;}
   Session s=new Session(owner,service,modeService,dipoService);
   CandidateController c=new CandidateController(owner,s,service,registry,source,this);
   ResearchOverlay.Snapshot snap=c.snapshot();if(snap==null||!c.snapshotValidated(snap)){gateReason="snapshot_invalid";return null;}
   gateReason="ready";return s;
  }catch(Throwable e){gateReason="exception";return null;}
 }
 public Context climateContext(Object owner){try{Field f=owner.getClass().getDeclaredField("mContext");if(f.getType()!=Context.class)return null;f.setAccessible(true);return (Context)f.get(owner);}catch(Throwable e){return null;}}
 public ResearchOverlay.Controller controller(Object owner,Object session){if(!(session instanceof Session)||((Session)session).owner!=owner)return null;return new CandidateController(owner,session,((Session)session).service,registry,source,this);}
 public boolean safe(Object owner,Object session){try{if(!(session instanceof Session))return false;Session s=(Session)session;return enabled()&&s.owner==owner&&dipo.active(s.dipoService)&&source.unchanged(s.service)&&mode.alive(s.modeService)&&mode.sameService(s.modeService)&&FailClosedModeState.safeWorker(mode,owner);}catch(Throwable e){return false;}}
 boolean recoverySafeWorker(Object owner,Object session){try{if(!(session instanceof Session)||!enabled())return false;Session s=(Session)session;return s.owner==owner&&mode.alive(s.modeService)&&mode.sameService(s.modeService)&&FailClosedModeState.safeWorker(mode,owner);}catch(Throwable e){return false;}}
 public boolean visible(View root){try{return root!=null&&root.getWindowToken()!=null&&root.isShown();}catch(Throwable e){return false;}}
 public boolean overlayAllowed(Object o,Object s){return safe(o,s);}
 public boolean recoveryAllowed(Object o,Object s){return safe(o,s);}
 public void committed(Object owner,Object session){if(session instanceof Session){Session s=(Session)session;if(!s.recovery.suppressionCommitted(owner,s,s))mod.log("WARMER_EXPERIMENT recovery_commit_failed=1");}}
 void recoverVia(final Object owner,final Object session,final StockRecovery.Safety gate){if(!(session instanceof Session))return;final Session s=(Session)session;
  StockRecovery.Outcome result=s.recovery.unexpectedLoss(owner,s,s,gate,new StockRecovery.Invoker(){public void invoke(Object o)throws Throwable{stock.invoke(o);}});
  mod.log("WARMER_EXPERIMENT stock_recovery="+result.name());
 }
 public void recoverOnce(final Object owner,final Object session){recoverVia(owner,session,new StockRecovery.Safety(){public boolean mayShowStockNow(Object o,Object ignored){return safe(o,session);}});}
}
