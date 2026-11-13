package android.dipo;
import android.os.IBinder;import android.os.IInterface;
public interface IDiPOManager extends IInterface {
 int getDiPOConnectStatus();int getDiPOConnectedMode();
 final class Stub {public static IDiPOManager remote;public static IDiPOManager asInterface(IBinder b){return remote;}}
}
