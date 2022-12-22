package org.telegram.messenger;

public class MultiDispatchQueue {

    private final DispatchQueuePool dispatchQueuePool;

    public MultiDispatchQueue(String name, int concurrencyLevel) {
        dispatchQueuePool = new DispatchQueuePool(name, concurrencyLevel);
    }

    public synchronized boolean postRunnable(Runnable runnable) {
        dispatchQueuePool.execute(runnable);
        return true;
    }

    public synchronized boolean postRunnable(Runnable runnable, long delay) {
        dispatchQueuePool.execute(runnable, delay);
        return true;
    }

    public void cancelRunnable(Runnable runnable) {
        dispatchQueuePool.cancelRunnable(runnable);
    }
}
