package kr.ioniq5.warmerui;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Host research only. Records the two OEM status callbacks for one exact
 * ClimateManager owner; it does not install hooks or prove freshness. */
public final class CallbackRegistry {
    public interface Clock { long elapsedMillis(); }
    private final Class<?> ownerType, listenerType;
    private final Field ownerField;
    private final Clock clock;
    private WeakReference<Object> owner = new WeakReference<Object>(null);
    private Sample front, wheel, rear; private boolean rearInvalid; private int wheelInvalidMask;
    private long sequence;

    public static final class Sample {
        public final long atElapsed, sequence;
        /** Cache bootstrap is not the same evidence as an observed callback. */
        public final boolean fromConsistentCache;
        private final int[] values;
        private Sample(long time, long sequence, int[] data, boolean cache) { atElapsed=time;this.sequence=sequence;values=data;fromConsistentCache=cache; }
        public int[] values() { return values.clone(); }
    }
    public static final class Pair {
        public final Sample front, wheel;
        private Pair(Sample f, Sample w) { front=f;wheel=w; }
    }

    /** Both classes must come from the captured, hash-pinned climate loader. */
    public CallbackRegistry(Class<?> exactOwner, Class<?> exactListener, Clock clock) throws Exception {
        if(exactOwner==null||exactListener==null||clock==null)throw new IllegalArgumentException("missing dependency");
        Field f=exactListener.getDeclaredField("l");
        if(f.getType()!=exactOwner || Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers()))
            throw new NoSuchFieldException("owner field mismatch");
        f.setAccessible(true);
        this.ownerType=exactOwner;this.listenerType=exactListener;this.ownerField=f;this.clock=clock;
    }
    private Object callbackOwner(Object listener) {
        try {
            if(listener==null || listener.getClass()!=listenerType)return null;
            Object value=ownerField.get(listener);
            return value!=null && value.getClass()==ownerType?value:null;
        } catch(Throwable ignored) { return null; }
    }
    public synchronized boolean listenerForOwner(Object listener,Object exactOwner){return exactOwner!=null&&callbackOwner(listener)==exactOwner;}
    /** Call only after the actual OEM callback returned without exception. */
    public synchronized void observeFront(Object listener, int a, int b, int c, int d) {
        Object next=callbackOwner(listener);if(next==null)return;
        if(a<1||a>6||b<1||b>6||c<2||c>8||d<2||d>8){invalidateIfOwner(next,true);return;}
        if(!adopt(next))return;
        long t=time();if(t<0||(front!=null&&t<front.atElapsed)){front=null;return;}
        long seq=advance();if(seq<0){front=null;wheel=null;return;}
        front=new Sample(t,seq,new int[]{a,b,c,d},false);
    }
    /** wheel callback argument three is retained but not interpreted here. */
    public synchronized void observeWheel(Object listener, int a, int b, int c, int d) {
        Object next=callbackOwner(listener);if(next==null)return;
        int mask=(a<0||a>1?1:0)|(b<0||b>1?2:0)|(d<0||d>1?4:0);
        if(mask!=0){if(owner.get()==next)wheelInvalidMask=mask;invalidateIfOwner(next,false);return;}
        if(!adopt(next))return;
        wheelInvalidMask=0;
        long t=time();if(t<0||(wheel!=null&&t<wheel.atElapsed)){wheel=null;return;}
        long seq=advance();if(seq<0){front=null;wheel=null;return;}
        wheel=new Sample(t,seq,new int[]{a,b,c,d},false);
    }
    /** Rear option/state callback, after the OEM listener succeeds. */
    public synchronized void observeRear(Object listener,int a,int b,int c,int d) {
        Object next=callbackOwner(listener);if(next==null)return;
        if(a<0||a>6||b<0||b>6||c<0||c>8||d<0||d>8){invalidateRear(listener);return;}
        if(!adopt(next))return;long t=time();if(t<0||(rear!=null&&t<rear.atElapsed)){rear=null;return;}
        long seq=advance();if(seq<0){rear=null;rearInvalid=true;return;}rear=new Sample(t,seq,new int[]{a,b,c,d},false);rearInvalid=false;
    }
    public synchronized void invalidateRear(Object listener){Object o=callbackOwner(listener);if(o!=null&&owner.get()==o){advance();rear=null;rearInvalid=true;}}
    public synchronized boolean rearInvalid(Object o){return o!=null&&owner.get()==o&&rearInvalid;}
    public synchronized Sample rearSnapshot(Object o){if(o==null||owner.get()!=o||rear==null)return null;return new Sample(rear.atElapsed,rear.sequence,rear.values.clone(),rear.fromConsistentCache);}
    private boolean adopt(Object next) {
        Object existing=owner.get();
        if(existing==null){owner=new WeakReference<Object>(next);front=null;wheel=null;rear=null;rearInvalid=false;wheelInvalidMask=0;return true;}
        // Never merge callbacks from two live manager instances.
        return existing==next;
    }
    private long time(){try{return clock.elapsedMillis();}catch(Throwable ignored){return -1;}}
    private long advance(){if(sequence==Long.MAX_VALUE)return -1;return ++sequence;}
    private void invalidateIfOwner(Object value, boolean isFront) {
        if(owner.get()==value){advance();if(isFront)front=null;else wheel=null;}
    }
    public synchronized void invalidateFront(Object listener){Object value=callbackOwner(listener);if(value!=null)invalidateIfOwner(value,true);}
    public synchronized void invalidateWheel(Object listener){Object value=callbackOwner(listener);if(value!=null)invalidateIfOwner(value,false);}
    /** Diagnostic categories only, without callback data or identifiers. */
    public synchronized String diagnosticState(Object o) {
        if (o==null || owner.get()!=o) return "owner";
        if (front==null && wheel==null) return "both_missing";
        if (front==null) return "front_missing";
        if (wheel==null) return wheelInvalidMask==0?"wheel_missing":"wheel_invalid_mask_"+wheelInvalidMask;
        return "present";
    }
    /** Caller must separately establish service/session validity and freshness. */
    public synchronized Pair snapshot(Object exactOwner) {
        if(exactOwner==null||owner.get()!=exactOwner||front==null||wheel==null)return null;
        return new Pair(new Sample(front.atElapsed,front.sequence,front.values.clone(),front.fromConsistentCache),new Sample(wheel.atElapsed,wheel.sequence,wheel.values.clone(),wheel.fromConsistentCache));
    }
    /** Checkpoint before asynchronous cache reads. Each callback, invalidation
     * or clear changes the checkpoint, so a late read cannot overwrite it. */
    public synchronized long checkpoint(){return sequence;}
    /** Import an independently double-checked OEM cache pair. This does not
     * prove MICOM connectivity or freshness, and it never enables the UI. */
    public synchronized boolean seedConsistentCache(Object exactOwner,int[] data,long at,long expected){
        if(exactOwner==null||exactOwner.getClass()!=ownerType||data==null||data.length!=7||sequence!=expected)return false;
        if(data[0]<1||data[0]>6||data[1]<1||data[1]>6||data[2]<2||data[2]>8||data[3]<2||data[3]>8
                ||data[4]<0||data[4]>1||data[5]<0||data[5]>1||data[6]<0||data[6]>1)return false;
        long now=time();if(at<0||now<at||sequence>=Long.MAX_VALUE-1||!adopt(exactOwner))return false;
        long a=advance(),b=advance();if(a<0||b<0){front=null;wheel=null;return false;}
        front=new Sample(at,a,new int[]{data[0],data[1],data[2],data[3]},true);
        wheel=new Sample(at,b,new int[]{data[4],data[5],0,data[6]},true);wheelInvalidMask=0;
        return true;
    }
    public synchronized void clear(Object exactOwner) {
        if(exactOwner!=null&&owner.get()==exactOwner){advance();front=null;wheel=null;rear=null;rearInvalid=false;wheelInvalidMask=0;owner=new WeakReference<Object>(null);}
    }
    public synchronized void clearAll(){advance();front=null;wheel=null;rear=null;rearInvalid=false;wheelInvalidMask=0;owner=new WeakReference<Object>(null);}
}
