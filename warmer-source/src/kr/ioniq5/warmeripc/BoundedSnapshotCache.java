package kr.ioniq5.warmeripc;

/**
 * Nonblocking, single-flight research cache for an OEM Binder observation.
 * No Android or vehicle I/O is performed here. The supplied probe runs only on
 * a worker, and must itself validate both service identities, session/owner,
 * all required safety flags, and a coherent state before returning a sample.
 * A sample is evidence only as of its probe start, not of the current frame.
 */
public final class BoundedSnapshotCache {
    public interface Clock { long elapsedMillis(); }
    public interface Submitter { boolean submit(Runnable task); }
    public interface Probe { Sample read(Object owner, Object session) throws Exception; }

    /** Candidate payload is an immutable copy; no Binder object is exposed. */
    public static final class Sample {
        private final int[] state;
        public final boolean safe;
        public Sample(boolean safe, int[] state) {
            this.safe=safe; this.state=state==null?null:state.clone();
        }
        public int[] state(){return state==null?null:state.clone();}
    }

    private final Clock clock; private final Submitter submitter; private final Probe probe;
    private final long maxAgeMs,deadlineMs; private final int stateLength;
    private Object owner,session; private long generation,serial,activeTicket,acceptedAt=-1,startedAt=-1;
    private boolean enabled,closed,inFlight,actionConsumed; private Sample accepted; private String diagnosticLast="empty";

    /** Age/deadline are policy inputs, not asserted facts about a vehicle. */
    public BoundedSnapshotCache(Clock c,Submitter s,Probe p,int length,long maxAge,long deadline) {
        if(c==null||s==null||p==null||length<1||length>32||maxAge<1||deadline<1||maxAge>10000||deadline>10000)
            throw new IllegalArgumentException("bad configuration");
        clock=c;submitter=s;probe=p;stateLength=length;maxAgeMs=maxAge;deadlineMs=deadline;
    }
    /** Starting a new session invalidates old work; it never spawns an extra worker. */
    public synchronized boolean start(Object nextOwner,Object nextSession) {
        if(closed||nextOwner==null||nextSession==null||generation==Long.MAX_VALUE)return false;
        generation++;owner=nextOwner;session=nextSession;enabled=true;discard();return true;
    }
    /** On known foreground/power/camera/call/lifecycle events, revoke immediately. */
    public synchronized void invalidate(Object expectedOwner,Object expectedSession) {
        if(!current(expectedOwner,expectedSession))return;
        if(generation==Long.MAX_VALUE){enabled=false;discard();return;}
        generation++;discard();diagnosticLast="invalidated";
    }
    /** Called on UI thread; runs no OEM getter, ping, wait, reflection or Binder IPC. */
    public boolean requestRefresh(final Object expectedOwner,final Object expectedSession) {
        final long token, ticket, start;
        synchronized(this) {
            if(!current(expectedOwner,expectedSession)||inFlight||serial==Long.MAX_VALUE)return false;
            try{start=clock.elapsedMillis();}catch(Throwable t){discard();return false;}
            if(start<0){discard();return false;}
            token=generation;ticket=++serial;activeTicket=ticket;startedAt=start;inFlight=true;
        }
        boolean submitted=false;
        try{submitted=submitter.submit(new Runnable(){public void run(){
            Sample value=null;
            try{value=probe.read(expectedOwner,expectedSession);}catch(Throwable ignored){}
            complete(expectedOwner,expectedSession,token,ticket,start,value);
        }});}catch(Throwable ignored){}
        if(!submitted){complete(expectedOwner,expectedSession,token,ticket,start,null);synchronized(this){diagnosticLast="submit_rejected";}}
        return submitted;
    }
    private synchronized void complete(Object expectedOwner,Object expectedSession,long token,long ticket,long start,Sample result) {
        // Only the one currently reserved worker can clear single-flight state.
        if(!inFlight||activeTicket!=ticket)return;
        inFlight=false;activeTicket=0;startedAt=-1;
        if(!current(expectedOwner,expectedSession)||token!=generation){diagnosticLast="generation_changed";return;}
        long now;try{now=clock.elapsedMillis();}catch(Throwable t){discard();return;}
        int[] data=result==null?null:result.state();
        if(now<start||now-start>deadlineMs||result==null||!result.safe||data==null||data.length!=stateLength){
            if(now<start)diagnosticLast="clock";
            else if(now-start>deadlineMs)diagnosticLast="probe_timeout";
            else if(result==null)diagnosticLast="probe_null";
            else if(!result.safe)diagnosticLast="probe_unsafe";
            else diagnosticLast="probe_shape";
            discard();return;
        }
        diagnosticLast="accepted";accepted=new Sample(true,data);acceptedAt=start;actionConsumed=false;
    }
    private boolean current(Object o,Object s){return enabled&&!closed&&owner==o&&session==s&&o!=null&&s!=null;}
    private void discard(){accepted=null;acceptedAt=-1;actionConsumed=false;}
    private void clockAnomaly(){if(generation==Long.MAX_VALUE)enabled=false;else generation++;discard();}
    private boolean fresh(Object o,Object s,long now){
        // An old window/session must not invalidate a different active session.
        if(!current(o,s))return false;
        if(inFlight&&now<startedAt){clockAnomaly();return false;}
        if(accepted!=null&&now<acceptedAt){clockAnomaly();return false;}
        if(accepted==null||acceptedAt<0||now-acceptedAt>maxAgeMs){discard();return false;}
        // A stalled in-flight probe is a denial, even if old data has some TTL left.
        if(inFlight&&now-startedAt>deadlineMs){discard();return false;}
        return true;
    }
    /** No state values or identifiers: used solely to distinguish read/worker failures. */
    public synchronized String diagnosticState(Object o,Object s){
        if(!current(o,s))return "inactive";
        if(inFlight)return "in_flight_after_"+diagnosticLast;
        if(accepted==null)return "no_sample_"+diagnosticLast;
        long now;try{now=clock.elapsedMillis();}catch(Throwable t){return "clock";}
        if(now<acceptedAt)return "clock";
        if(now-acceptedAt>maxAgeMs)return "expired";
        return "available";
    }
    /** Nonblocking read for painting only. A successful read does not authorise an action. */
    public synchronized Sample view(Object o,Object s){
        long now;try{now=clock.elapsedMillis();}catch(Throwable t){discard();return null;}
        return fresh(o,s,now)?new Sample(accepted.safe,accepted.state()):null;
    }
    /** Display hint only; the action must still call takeForAction. */
    public synchronized boolean canTake(Object o,Object s){long now;try{now=clock.elapsedMillis();}catch(Throwable t){discard();return false;}return !inFlight&&!actionConsumed&&fresh(o,s,now);}
    /** One-time decision; no command is queued here. In-flight refresh denies it. */
    public synchronized Sample takeForAction(Object o,Object s){
        long now;try{now=clock.elapsedMillis();}catch(Throwable t){discard();return null;}
        if(inFlight||actionConsumed||!fresh(o,s,now))return null;
        actionConsumed=true;return new Sample(accepted.safe,accepted.state());
    }
    public synchronized void stop(){enabled=false;owner=null;session=null;if(generation!=Long.MAX_VALUE)generation++;discard();}
    public synchronized void close(){stop();closed=true;}
}
