from pathlib import Path
import ast
import subprocess
import tempfile

here=Path(__file__).resolve().parent
root=here.parents[3]
original=here.parent/'research-ui/test-overlay/run.py'
module=ast.parse(original.read_text())
stubs=None
for node in module.body:
    if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in node.targets):
        stubs=ast.literal_eval(node.value)
if not stubs:raise SystemExit('missing original test stubs')
stubs['android/util/Log.java']='package android.util; public class Log {public static String last; public static int i(String t,String m){last=m;return 0;}}'
stubs['android/view/View.java']=stubs['android/view/View.java'].replace('public View(Context c){}', 'public View(Context c){} public boolean isShown(){return attached;}')
stubs['android/view/View.java']=stubs['android/view/View.java'].replace('public int id,visibility;', 'public int id,visibility,backgroundResource; public void setBackgroundResource(int res){backgroundResource=res;padLeft=90;padTop=91;padRight=92;padBottom=93;}')
stubs['android/view/View.java']=stubs['android/view/View.java'].replace('public View(Context c){}', 'public int padLeft=1,padTop=2,padRight=3,padBottom=4; public int getPaddingLeft(){return padLeft;} public int getPaddingTop(){return padTop;} public int getPaddingRight(){return padRight;} public int getPaddingBottom(){return padBottom;} public void setPadding(int l,int t,int r,int b){padLeft=l;padTop=t;padRight=r;padBottom=b;} public View(Context c){}')
stubs['android/view/WindowManager.java']=stubs['android/view/WindowManager.java'].replace('view=v;v.attached=true;', 'view=v;v.attached=false;')
stubs['android/view/WindowManager.java']=stubs['android/view/WindowManager.java'].replace('public boolean failAdd; public View view;', 'public boolean failAdd; public View view; public LayoutParams params;').replace('view=v;v.attached=false;', 'view=v;params=p;v.attached=false;').replace('public int gravity;', 'public int gravity,width,height;').replace('LayoutParams(int a,int b,int c,int d,int e){}', 'LayoutParams(int a,int b,int c,int d,int e){width=a;height=b;}')
stubs['android/os/Handler.java']='''package android.os; import java.util.*; public class Handler { static class Item {Runnable r;long due;Item(Runnable r,long d){this.r=r;due=d;}} private static final List<Item> q=new ArrayList<Item>(); public Handler(Looper l){} public boolean postDelayed(Runnable r,long ms){synchronized(q){q.add(new Item(r,SystemClock.now+ms));}return true;} public void removeCallbacks(Runnable r){synchronized(q){for(Iterator<Item> i=q.iterator();i.hasNext();)if(i.next().r==r)i.remove();}} public static void runPending(){List<Item> work=new ArrayList<Item>();synchronized(q){for(Iterator<Item> i=q.iterator();i.hasNext();){Item n=i.next();if(n.due<=SystemClock.now){work.add(n);i.remove();}}}for(Item x:work)x.r.run();} }'''
stubs['android/view/View.java']=stubs['android/view/View.java'].replace('VISIBLE=0,INVISIBLE=4','VISIBLE=0,INVISIBLE=4,GONE=8').replace('if(touch!=null)touch.onTouch(this,new MotionEvent(a));','if(enabled&&touch!=null)touch.onTouch(this,new MotionEvent(a));')
stubs['android/view/View.java']=stubs['android/view/View.java'].replace('public boolean enabled=true,attached;', 'public boolean enabled=true,attached,pressed; public void setPressed(boolean v){pressed=v;} public int getWidth(){return 100;} public int getHeight(){return 100;}')
stubs['android/view/MotionEvent.java']=stubs['android/view/MotionEvent.java'].replace('public class MotionEvent {', 'public class MotionEvent { public static final int ACTION_MOVE=2; public float getX(){return 10;} public float getY(){return 10;}')
stubs['android/widget/RelativeLayout.java']=stubs['android/widget/RelativeLayout.java'].replace('ALIGN_PARENT_LEFT=9','ALIGN_PARENT_TOP=10,ALIGN_PARENT_LEFT=9').replace('leftMargin,rightMargin,topMargin','leftMargin,rightMargin,topMargin,bottomMargin')
stubs['android/widget/TextView.java']=stubs['android/widget/TextView.java'].replace('public void setText(String s){}','public void setText(String s){} public void setTextSize(float v){} public void setGravity(int v){}')
stubs['com/hkmc/telematics/service/apps/climate/widget/FuncButton.java']=stubs['com/hkmc/telematics/service/apps/climate/widget/FuncButton.java'].replace('public boolean checked;', 'public boolean checked; public void ay(){}')
stubs['android/content/res/Resources.java']=stubs['android/content/res/Resources.java'].replace('public class Resources {','public class Resources { public int getColor(int id){return id;}')
stubs['android/widget/TextView.java']=stubs['android/widget/TextView.java'].replace('public void setText(String s){}','public void setTextColor(int c){} public void setText(String s){}')
stubs['android/widget/Button.java']=stubs['android/widget/Button.java'].replace('public void setText(String s){}','public void setTextColor(int c){} public void setText(String s){}')
jdk=root/'.tools/java-build/jdk-17.0.21+9/Contents/Home/bin'
with tempfile.TemporaryDirectory(prefix='warmer-async-ui-') as td:
    out=Path(td);files=[]
    for name,data in stubs.items():
        p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data);files.append(p)
    for path in ['tests/hmgapi/hook/ModContext.java','tests/hmgapi/hook/MethodHook.java', 'tests/android/dipo/IDiPOManager.java','tests/android/os/IBinder.java','tests/android/os/IInterface.java','tests/android/os/ServiceManager.java',
                 'tests/com/hkmc/usm/IUSMService.java','tests/com/hkmc/telematics/service/apps/climate/ClimateManager.java',
                 'tests/com/hkmc/telematics/service/apps/climate/j.java']:
        files.append(here/path)
    src=here/'src/kr/ioniq5/warmerui'
    files += [src/'SeatCycle.java',src/'ResearchOverlay.java',
              here/'src/kr/ioniq5/warmerasync/AsyncOpenCoordinator.java',
              here/'src/kr/ioniq5/warmerhook/AsyncUiPanel.java',here/'src/kr/ioniq5/warmerhook/AsyncUiOpener.java',
              here/'src/kr/ioniq5/warmerhook/CacheBootstrap.java',here/'src/kr/ioniq5/warmerhook/AndroidCacheSource.java',
              here/'src/kr/ioniq5/warmeripc/BoundedSnapshotCache.java',here/'src/kr/ioniq5/warmeripc/SingleDaemonWorker.java',here/'src/kr/ioniq5/warmeripc/SerialIpcDispatcher.java',here/'src/kr/ioniq5/warmerhook/AsyncCandidateController.java',here/'src/kr/ioniq5/warmerhook/AsyncCandidateBackend.java',here/'src/kr/ioniq5/warmerhook/CandidateController.java',here/'src/kr/ioniq5/warmerhook/RearSeatCandidate.java', here/'src/kr/ioniq5/warmerhook/CandidateBackend.java',here/'src/kr/ioniq5/warmerhook/AndroidDipoSession.java', here/'src/kr/ioniq5/warmerhook/FailClosedModeState.java',here/'src/kr/ioniq5/warmerhook/StockRecovery.java',src/'CallbackRegistry.java',
              here/'tests/TestAsyncOverlay.java',here/'tests/kr/ioniq5/warmerhook/AsyncUiOpenerTest.java',
              here/'tests/kr/ioniq5/warmerhook/CandidateControllerTest.java',here/'tests/kr/ioniq5/warmerhook/CandidateBackendTest.java',here/'tests/kr/ioniq5/warmerhook/AsyncCandidateBackendTest.java',here/'tests/kr/ioniq5/warmerhook/AsyncRecoveryRaceTest.java',here/'tests/kr/ioniq5/warmerhook/AndroidDipoSessionTest.java', here/'tests/kr/ioniq5/warmerhook/GateReasonTest.java']
    subprocess.run([str(jdk/'javac'),'-d',str(out),*map(str,files)],check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'TestAsyncOverlay'],check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'kr.ioniq5.warmerhook.AsyncUiOpenerTest'],check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'kr.ioniq5.warmerhook.CandidateControllerTest'],check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'kr.ioniq5.warmerhook.CandidateBackendTest'],check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'kr.ioniq5.warmerhook.AsyncCandidateBackendTest'],check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'kr.ioniq5.warmerhook.AsyncRecoveryRaceTest'],check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'kr.ioniq5.warmerhook.AndroidDipoSessionTest'],check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'kr.ioniq5.warmerhook.GateReasonTest'],check=True)
