package kr.ioniq5.warmeripc;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/** One daemon worker and at most one queued handoff; no replacement thread while Binder is stuck. */
public final class SingleDaemonWorker implements BoundedSnapshotCache.Submitter, AutoCloseable {
    private final ThreadPoolExecutor pool;
    public SingleDaemonWorker() {
        pool=new ThreadPoolExecutor(1,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),
            new ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"warmer-ipc-research");t.setDaemon(true);return t;}});
        pool.allowCoreThreadTimeOut(true);
    }
    public boolean busy(){return pool.getActiveCount()!=0;}
    public boolean submit(Runnable task){if(task==null)return false;try{pool.execute(task);return true;}catch(RejectedExecutionException e){return false;}}
    /** Best-effort interrupt; it cannot cancel a synchronous Binder transact. */
    public void close(){pool.shutdownNow();}
}
