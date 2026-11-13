package kr.ioniq5.warmerhook;

import kr.ioniq5.warmerui.CallbackRegistry;

/** Host-only composition for integration tests. The real WarmerHook never
 * constructs this class, and it does not register OEM callback hooks. */
final class ResearchPipeline {
    private final CallbackForwarder callbacks;
    private final CheckedBackend backend;
    private ResearchPipeline(CallbackForwarder callbacks,CheckedBackend backend){this.callbacks=callbacks;this.backend=backend;}
    static ResearchPipeline create(Class<?> ownerClass,Class<?> listenerClass,CallbackRegistry.Clock clock,
            ResearchCallbackFactory.ProofFactory proofs,FailClosedModeState.Access mode)throws Exception {
        CallbackRegistry registry=new CallbackRegistry(ownerClass,listenerClass,clock);
        CallbackForwarder callbacks=new CallbackForwarder(registry);
        ResearchCallbackFactory factory=new ResearchCallbackFactory(ownerClass,registry,proofs);
        return new ResearchPipeline(callbacks,new CheckedBackend(mode,new ResearchUiBridge(factory)));
    }
    void startForTest(){callbacks.start();}
    void stop(){callbacks.stop();}
    void afterFront(Object listener,Object[] args,boolean success){callbacks.front(listener,args,success);}
    void afterWheel(Object listener,Object[] args,boolean success){callbacks.wheel(listener,args,success);}
    boolean openForTest(Object owner,boolean enabled,OverlayGate.SkipOriginal skip){return OverlayGate.run(backend,owner,enabled,skip);}
}
