package kr.ioniq5.warmerhook;
public final class GateReasonTest {
 static class A implements FailClosedModeState.Access {
  int fail; final Object service=new Object();
  public boolean onMainThread(){return true;}
  public boolean ignitionOn(Object x){return fail!=2;}
  public Object service(){return fail==3?null:service;}
  public boolean alive(Object s){return true;}
  public boolean sameService(Object s){return fail!=9;}
  public Boolean flag(Object s,String n,String a){
   if(fail==10)throw new RuntimeException();
   if(n.equals("isScreenOn"))return fail!=4;
   if(n.equals("isForegroundActivity"))return fail!=5;
   if(n.equals("isRearViewOn"))return fail==6;
   if(n.equals("isCallingStatus"))return fail==7;
   if(n.equals("isPopUpActivityShowing"))return fail==8;
   throw new AssertionError();
  }
 }
 public static void main(String[] args){A a=new A();Object owner=new Object();
  for(int n=0;n<=10;n++){if(n==1)continue;a.fail=n;int got=FailClosedModeState.diagnostic(a,owner);if(got!=n||FailClosedModeState.safeWorker(a,owner)!=(n==0))throw new AssertionError(n+" "+got);}
  if(FailClosedModeState.diagnostic(null,owner)!=1)throw new AssertionError();
  System.out.println("Gate reasons preserve allow/deny behavior: PASS");
 }
}
