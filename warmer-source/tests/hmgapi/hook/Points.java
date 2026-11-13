package hmgapi.hook;
public final class Points {
 public static java.lang.reflect.Method method(ClassLoader cl,String name){try{
  if(name.equals("climate.warmer.open"))return Class.forName("com.hkmc.telematics.service.apps.climate.ClimateManager").getDeclaredMethod("mD");
  Class<?> c=Class.forName("com.hkmc.telematics.service.apps.climate.j");
  String m=name.equals("climate.listener.front")?"onFrontSeatHeatStatusReceived":name.equals("climate.listener.rear")?"onRearSeatHeatStatusReceived":"onSWRCStatusReceived";
  return c.getDeclaredMethod(m,int.class,int.class,int.class,int.class);
 }catch(Exception e){throw new IllegalStateException(e);}}
}
