package kr.ioniq5.warmerhook;
import android.os.IBinder;
import android.os.IInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
/** Exact captured framework's CarPlay service. Reads are worker-only.
 * The service's published state can lag a crash; observed state is not a
 * guarantee of real-time session health or Climate UID access. */
final class AndroidDipoSession {
 private final ClassLoader loader;
 static final class Token {final IBinder binder;final Object remote;final Class<?> iface;Token(IBinder b,Object r,Class<?> i){binder=b;remote=r;iface=i;}}
 AndroidDipoSession(ClassLoader l){if(l==null)throw new IllegalArgumentException();loader=l;}
 private IBinder lookup()throws Exception{Method m=Class.forName("android.os.ServiceManager").getMethod("checkService",String.class);if(!Modifier.isStatic(m.getModifiers())||!IBinder.class.isAssignableFrom(m.getReturnType()))return null;return (IBinder)m.invoke(null,"dipo");}
 Object open()throws Exception{
  IBinder b=lookup();if(b==null||!b.isBinderAlive()||!b.pingBinder())return null;
  Class<?> i=Class.forName("android.dipo.IDiPOManager",false,loader),stub=Class.forName("android.dipo.IDiPOManager$Stub",false,loader);
  Method m=stub.getMethod("asInterface",IBinder.class);if(!Modifier.isStatic(m.getModifiers())||!i.isAssignableFrom(m.getReturnType()))return null;
  Object remote=m.invoke(null,b);if(remote==null||!i.isInstance(remote)||!(remote instanceof IInterface)||!b.equals(((IInterface)remote).asBinder()))return null;
  return new Token(b,remote,i);
 }
 private int get(Token t,String name)throws Exception{Method m=t.iface.getMethod(name);if(m.getReturnType()!=Integer.TYPE||Modifier.isStatic(m.getModifiers()))throw new IllegalStateException();return ((Integer)m.invoke(t.remote)).intValue();}
 private boolean same(Token t)throws Exception{IBinder b=lookup();return b!=null&&b.equals(t.binder)&&b.isBinderAlive()&&b.pingBinder();}
 boolean active(Object object){try{if(!(object instanceof Token))return false;Token t=(Token)object;if(!same(t))return false;
  int status=get(t,"getDiPOConnectStatus"),mode=get(t,"getDiPOConnectedMode");
  return status==4&&(mode==1||mode==2)&&get(t,"getDiPOConnectStatus")==status&&get(t,"getDiPOConnectedMode")==mode&&same(t);
 }catch(Throwable denied){return false;}}
}
