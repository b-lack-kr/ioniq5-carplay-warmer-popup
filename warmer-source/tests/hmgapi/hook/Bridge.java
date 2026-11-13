package hmgapi.hook;
import java.lang.reflect.Member;
import java.util.*;
public final class Bridge {
 static final Map<Member,MethodHook> hooked=new HashMap<Member,MethodHook>();
 public static String failAt;
 public static void reset(){hooked.clear();failAt=null;}
 public static void hookMethod(Member method,MethodHook hook){if(method.getName().equals(failAt))throw new IllegalStateException("test");hooked.put(method,hook);}
 public static MethodHook get(String name){for(Map.Entry<Member,MethodHook> e:hooked.entrySet())if(e.getKey().getName().equals(name))return e.getValue();return null;}
 public static int count(){return hooked.size();}
}
