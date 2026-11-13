package android.os;
public final class ServiceManager {public static IBinder binder,dipoBinder;public static IBinder checkService(String name){return "com.hkmc.micom.usm".equals(name)?binder:"dipo".equals(name)?dipoBinder:null;}}
