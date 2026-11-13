package kr.ioniq5.warmerhook;

import android.os.IBinder;
import android.os.IInterface;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Exact 260902 read-only manager / IUSMService cache comparator.
 * A non-null snapshot is not a proof that MICOM status is fresh. */
final class AndroidCacheSource implements CacheBootstrap.Source {
    static final String SERVICE="com.hkmc.micom.usm";
    static final String IFACE="com.hkmc.usm.IUSMService", STUB=IFACE+"$Stub";
    static final String OWNER="com.hkmc.telematics.service.apps.climate.ClimateManager";
    static final String[] MANAGER={"mH","mI","mJ","mK","mT","mU","mV"};
    static final String[] REMOTE={"getFrontSeatHeat_DrvOption","getFrontSeatHeat_AstOption",
        "getFrontSeatHeat_DrvState","getFrontSeatHeat_AstState","getSWRC_SteeringwheelHeatingOpt",
        "getSWRC_SteeringwheelHeatingSWInf","getSWRC_SteeringwheelHeatIndStep2"};
    static final class Token {final IBinder binder;final Object remote;final Class<?> type;
        Token(IBinder b,Object r,Class<?> t){binder=b;remote=r;type=t;}}
    private final ClassLoader loader;private final Class<?> ownerType;
    AndroidCacheSource(ClassLoader loader,Class<?> ownerType){
        if(loader==null||ownerType==null||!OWNER.equals(ownerType.getName()))throw new IllegalArgumentException("owner");
        this.loader=loader;this.ownerType=ownerType;
    }
    private IBinder lookup()throws Exception{
        Method m=Class.forName("android.os.ServiceManager").getMethod("checkService",String.class);
        if(!Modifier.isStatic(m.getModifiers())||!IBinder.class.isAssignableFrom(m.getReturnType()))return null;
        return (IBinder)m.invoke(null,SERVICE);
    }
    public Object open()throws Exception{
        IBinder b=lookup();if(b==null||!b.isBinderAlive()||!b.pingBinder())return null;
        Class<?> type=Class.forName(IFACE,false,loader),stub=Class.forName(STUB,false,loader);
        Method method=stub.getMethod("asInterface",IBinder.class);
        if(!Modifier.isStatic(method.getModifiers())||!type.isAssignableFrom(method.getReturnType()))return null;
        Object remote=method.invoke(null,b);
        if(remote==null||!type.isInstance(remote)||!(remote instanceof IInterface))return null;
        IBinder actual=((IInterface)remote).asBinder();
        return actual!=null&&b.equals(actual)?new Token(b,remote,type):null;
    }
    public boolean unchanged(Object token)throws Exception{
        if(!(token instanceof Token))return false;
        IBinder old=((Token)token).binder,current=lookup();
        return old!=null&&old.equals(current)&&old.isBinderAlive()&&old.pingBinder();
    }
    private static int[] read(Object instance,Class<?> type,String[] names)throws Exception{
        int[] result=new int[names.length];
        for(int i=0;i<names.length;i++){
            Method m=type.getMethod(names[i]);
            if(m.getReturnType()!=Integer.TYPE||Modifier.isStatic(m.getModifiers()))throw new NoSuchMethodException(names[i]);
            Object value=m.invoke(instance);if(!(value instanceof Integer))throw new IllegalStateException("not int");
            result[i]=(Integer)value;
        }
        return result;
    }
    public int[] managerValues(Object owner)throws Exception{
        if(owner==null||owner.getClass()!=ownerType)throw new IllegalArgumentException("owner");
        return read(owner,ownerType,MANAGER);
    }
    public int[] serviceValues(Object token)throws Exception{
        if(!(token instanceof Token)||!unchanged(token))throw new IllegalStateException("service");
        Token t=(Token)token;return read(t.remote,t.type,REMOTE);
    }
    public long elapsedMillis(){return SystemClock.elapsedRealtime();}
}
