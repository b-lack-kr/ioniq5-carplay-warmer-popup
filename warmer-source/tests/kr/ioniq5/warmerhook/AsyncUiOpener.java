package kr.ioniq5.warmerhook;
/** HMG wiring test stub only; Android implementation is tested separately. */
final class AsyncUiOpener {AsyncUiOpener(Object backend,long timeout){}interface ForegroundProbe{Boolean check(Object owner);} interface ForegroundExit{void exited(Object owner);} void setForegroundProbe(ForegroundProbe p,ForegroundExit e){} interface HookSkip {void skip()throws Exception;}boolean open(Object owner,HookSkip skip){return false;}void disable(){}}
