package kr.ioniq5.warmerhook;

import android.content.Context;
import kr.ioniq5.warmerasync.AsyncOpenCoordinator;
import kr.ioniq5.warmerui.ResearchOverlay;

/** Android UI adapter for the asynchronous coordinator. The production hook
 * does not construct this class or suppress stock until real proofs exist. */
final class AsyncUiPanel implements AsyncOpenCoordinator.Panel {
    private final Context context;private final ResearchOverlay.Controller controller;private final long timeout;
    private ResearchOverlay panel;
    AsyncUiPanel(Context c,ResearchOverlay.Controller controller,long timeout){this.context=c;this.controller=controller;this.timeout=timeout;}
    public boolean attach(final AsyncOpenCoordinator.Listener listener){
        if(panel!=null||listener==null)return false;
        panel=ResearchOverlay.beginAsync(context,controller,new ResearchOverlay.AsyncListener(){
            public void ready(){listener.attached();}
            public void failed(){listener.failed();}
            public void closedByUser(){listener.closedByUser();}
        },timeout);
        return panel!=null;
    }
    public boolean usable(){return panel!=null&&panel.attachedAndUsable();}
    void markSuppressionCommitted(){if(panel==null)throw new IllegalStateException("no panel");panel.markSuppressionCommitted();}
    public void dispose(){if(panel!=null){panel.dispose();panel=null;}}
}
