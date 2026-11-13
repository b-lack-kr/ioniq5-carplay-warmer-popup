package kr.ioniq5.warmerhook;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Research-only direct IModeManager accessor. Never installed or invoked on a car.
 * Unlike the OEM convenience wrapper, binder/IPC exceptions are not converted to
 * false/true defaults. Real permissions and class loading remain unverified. */
final class AndroidModeAccess implements FailClosedModeState.Access {
    private static final String SERVICE = "com.hkmc.micom.mode";
    private static final String IFACE = "com.hkmc.mode.IModeManager";
    private static final String STUB = "com.hkmc.mode.IModeManager$Stub";
    private static final String MANAGER = "com.hkmc.telematics.service.apps.climate.ClimateManager";
    private final ClassLoader loader;
    AndroidModeAccess(ClassLoader loader) { this.loader = loader; }
    static final class Token {
        final IBinder binder; final Object remote; final Class<?> type;
        Token(IBinder b, Object r, Class<?> t) { binder = b; remote = r; type = t; }
    }
    private IBinder binder() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method get = sm.getMethod("getService", String.class);
        if (!Modifier.isStatic(get.getModifiers()) || !IBinder.class.isAssignableFrom(get.getReturnType())) return null;
        return (IBinder)get.invoke(null, SERVICE);
    }
    public boolean onMainThread() { return Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper(); }
    public boolean ignitionOn(Object owner) throws Exception {
        if (owner == null || !MANAGER.equals(owner.getClass().getName())) return false;
        Method ignition = owner.getClass().getMethod("getIgnStatus");
        if (ignition.getReturnType() != Boolean.TYPE || Modifier.isStatic(ignition.getModifiers())) return false;
        return Boolean.TRUE.equals(ignition.invoke(owner));
    }
    public Object service() throws Exception {
        IBinder b = binder();
        if (b == null || !b.isBinderAlive() || !b.pingBinder()) return null;
        Class<?> type = Class.forName(IFACE, false, loader);
        Class<?> stub = Class.forName(STUB, false, loader);
        Method method = stub.getMethod("asInterface", IBinder.class);
        if (!Modifier.isStatic(method.getModifiers()) || !type.isAssignableFrom(method.getReturnType())) return null;
        Object remote = method.invoke(null, b);
        if (remote == null || !type.isInstance(remote) || !(remote instanceof IInterface)) return null;
        IBinder remoteBinder = ((IInterface)remote).asBinder();
        if (remoteBinder == null || !b.equals(remoteBinder)) return null;
        return new Token(b, remote, type);
    }
    public boolean alive(Object service) {
        if (!(service instanceof Token)) return false;
        IBinder b = ((Token)service).binder;
        return b != null && b.isBinderAlive() && b.pingBinder();
    }
    public boolean sameService(Object service) throws Exception {
        if (!(service instanceof Token)) return false;
        IBinder original = ((Token)service).binder;
        IBinder current = binder();
        return current != null && original.equals(current) && current.isBinderAlive() && current.pingBinder();
    }
    public Boolean flag(Object service, String name, String arg) throws Exception {
        if (!(service instanceof Token)) return null;
        Token token = (Token)service;
        // Fixed whitelist; do not expose an arbitrary binder interface invocation.
        boolean foreground = "isForegroundActivity".equals(name);
        if (!(foreground || "isScreenOn".equals(name) || "isRearViewOn".equals(name)
                || "isCallingStatus".equals(name) || "isPopUpActivityShowing".equals(name))) return null;
        if (foreground != (arg != null)) return null;
        Method m = foreground ? token.type.getMethod(name, String.class) : token.type.getMethod(name);
        if (m.getReturnType() != Boolean.TYPE || Modifier.isStatic(m.getModifiers())) return null;
        Object value = foreground ? m.invoke(token.remote, arg) : m.invoke(token.remote);
        return value instanceof Boolean ? (Boolean)value : null;
    }
}
