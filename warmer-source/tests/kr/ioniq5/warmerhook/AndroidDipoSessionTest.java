package kr.ioniq5.warmerhook;
import android.os.ServiceManager;
public final class AndroidDipoSessionTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[]a)throws Exception{
  CandidateControllerTest.Binder b=new CandidateControllerTest.Binder();CandidateBackendTest.installDipo(b);CandidateBackendTest.Dipo d=(CandidateBackendTest.Dipo)android.dipo.IDiPOManager.Stub.remote;
  AndroidDipoSession api=new AndroidDipoSession(AndroidDipoSessionTest.class.getClassLoader());Object token=api.open();ok(token!=null&&api.active(token),"wireless active");
  d.mode=1;ok(api.active(token),"wired active");d.mode=2;
  for(int status:new int[]{-1,0,1,2,3,5}){d.status=status;ok(!api.active(token),"inactive status denied");}d.status=4;
  for(int mode:new int[]{-1,0,3}){d.mode=mode;ok(!api.active(token),"transport denied");}d.mode=2;
  d.fail=true;ok(!api.active(token),"binder exception denied");d.fail=false;
  d.statusReads=0;d.modeReads=0;d.flipStatus=true;ok(!api.active(token),"session transition denied");d.flipStatus=false;
  d.statusReads=0;d.modeReads=0;d.flipMode=true;ok(!api.active(token),"transport transition denied");d.flipMode=false;
  ServiceManager.dipoBinder=new CandidateControllerTest.Binder();ok(!api.active(token),"replacement denied");ServiceManager.dipoBinder=b;
  b.live=false;ok(!api.active(token),"dead service denied");System.out.println("AndroidDipoSession OEM status/mode and identity gate tests passed; no vehicle I/O");
 }
}
