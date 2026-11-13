package kr.ioniq5.warmerhook;
import java.lang.reflect.Method;
import java.util.Arrays;
import kr.ioniq5.warmerui.SeatCycle;
/** NOT WIRED TO UI. Candidate rear cache and setter mapping from exact OEM.
 * A stable Binder cache is not a fresh hardware status or an actuator ACK. */
final class RearSeatCandidate {
 private static final String[] MANAGER={"mN","mO","mP","mQ"};
 private static final String[] REMOTE={"getRearSeatHeat_LeftOption","getRearSeatHeat_RightOption","getRearSeatHeat_LeftState","getRearSeatHeat_RightState"};
 private final Object owner,service;private final AndroidCacheSource source;
 RearSeatCandidate(Object o,Object s,AndroidCacheSource c){owner=o;service=s;source=c;}
 private static int[] values(Object o,Class<?> c,String[] names)throws Exception{int[] v=new int[names.length];for(int i=0;i<names.length;i++){Method m=c.getMethod(names[i]);if(m.getReturnType()!=Integer.TYPE)throw new IllegalStateException();v[i]=((Integer)m.invoke(o)).intValue();}return v;}
 int[] stableCache(){try{if(!(service instanceof AndroidCacheSource.Token)||!source.unchanged(service))return null;AndroidCacheSource.Token t=(AndroidCacheSource.Token)service;
  int[] local=values(owner,owner.getClass(),MANAGER),remote=values(t.remote,t.type,REMOTE);
  return source.unchanged(service)&&Arrays.equals(local,remote)?local:null;
 }catch(Throwable ignored){return null;}}
 boolean sendNext(boolean left,SeatCycle.Mode mode,int expectedTarget,boolean ascending){try{int[] a=stableCache();if(a==null)return false;int n=SeatCycle.next(a[left?0:1],mode,a[left?2:3],true,ascending);if(n<0||n!=expectedTarget||!Arrays.equals(a,stableCache()))return false;
  AndroidCacheSource.Token t=(AndroidCacheSource.Token)service;Method m=t.type.getMethod(left?"setRearSeatHeatLeftStep":"setRearSeatHeatRightStep",Integer.TYPE);if(m.getReturnType()!=Void.TYPE)return false;m.invoke(t.remote,n);return source.unchanged(service);
 }catch(Throwable ignored){return false;}}
}
