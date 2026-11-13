package kr.ioniq5.warmerhook;

import kr.ioniq5.warmerui.CallbackRegistry;

/** Research-only callback adapter. A successful OEM return must be supplied by
 * the caller. No vehicle hook, registration, or state freshness proof. */
final class CallbackForwarder {
    private final CallbackRegistry registry;
    private volatile boolean active;
    CallbackForwarder(CallbackRegistry registry){if(registry==null)throw new IllegalArgumentException();this.registry=registry;}
    synchronized void start(){active=true;}
    synchronized void stop(){active=false;registry.clearAll();}
    void front(Object listener,Object[] args,boolean success){handle(listener,args,success,true);}
    synchronized void rear(Object listener,Object[] args,boolean success){try{if(!active)return;
        if(!success||args==null||args.length!=4){registry.invalidateRear(listener);return;}
        for(Object x:args)if(!(x instanceof Integer)){registry.invalidateRear(listener);return;}
        registry.observeRear(listener,(Integer)args[0],(Integer)args[1],(Integer)args[2],(Integer)args[3]);
    }catch(Throwable e){registry.invalidateRear(listener);}}
    void wheel(Object listener,Object[] args,boolean success){handle(listener,args,success,false);}
    private synchronized void handle(Object listener,Object[] args,boolean success,boolean front){
        try {
            if(!active)return;
            if(!success||args==null||args.length!=4){invalidate(listener,front);return;}
            for(Object x:args)if(!(x instanceof Integer)){invalidate(listener,front);return;}
            int a=(Integer)args[0],b=(Integer)args[1],c=(Integer)args[2],d=(Integer)args[3];
            if(front)registry.observeFront(listener,a,b,c,d);else registry.observeWheel(listener,a,b,c,d);
        }catch(Throwable e){invalidate(listener,front);}
    }
    private void invalidate(Object listener,boolean front){
        try{if(front)registry.invalidateFront(listener);else registry.invalidateWheel(listener);}catch(Throwable ignored){}
    }
}
