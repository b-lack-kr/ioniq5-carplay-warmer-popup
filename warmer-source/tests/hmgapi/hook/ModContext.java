package hmgapi.hook;
public final class ModContext {
 public final java.util.Map<String,MethodHook> hooks=new java.util.LinkedHashMap<String,MethodHook>();
 public final java.util.List<String> logs=new java.util.ArrayList<String>();public boolean active=true;public String failAt;
 public boolean enabled(){return active;} public boolean experimental=false; public boolean settingBoolean(String k,boolean d){return experimental;}
 public void hook(ClassLoader cl,String name,MethodHook callback){if(name.equals(failAt))throw new IllegalStateException("test");hooks.put(name,callback);}
 public void log(String value){logs.add(value);}
}
