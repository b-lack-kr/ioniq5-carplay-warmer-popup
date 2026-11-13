package hmgapi.hook;
public class MethodHook {
 public static class Param {public Object thisObject;public Object[] args;public Throwable thrown;public boolean skipped;public Throwable getThrowable(){return thrown;}public void setResult(Object x){skipped=true;}}
 protected void beforeHookedMethod(Param p)throws Throwable{} protected void afterHookedMethod(Param p)throws Throwable{}
 public void before(Param p)throws Throwable{beforeHookedMethod(p);} public void after(Param p)throws Throwable{afterHookedMethod(p);}
}
