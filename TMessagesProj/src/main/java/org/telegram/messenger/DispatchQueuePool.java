package org.telegram.messenger;

import android.os.SystemClock;
import android.util.SparseIntArray;

import androidx.annotation.UiThread;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class DispatchQueuePool {

    private final LinkedList<DispatchQueue> queues = new LinkedList<>();
    private final SparseIntArray busyQueuesMap = new SparseIntArray();
    private final LinkedList<DispatchQueue> busyQueues = new LinkedList<>();
    private final Map<Runnable, DispatchQueue> postedRunnables = new HashMap<>();
    private final int maxCount;
    private int createdCount;
    private final String poolName;
    private int totalTasksCount;
    private boolean cleanupScheduled;

    private Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            if (!queues.isEmpty()) {
                long currentTime = SystemClock.elapsedRealtime();
                for (int a = 0, N = queues.size(); a < N; a++) {
                    DispatchQueue queue = queues.get(a);
                    if (queue.getLastTaskTime() < currentTime - 30000) {
                        queue.recycle();
                        queues.remove(a);
                        createdCount--;
                        a--;
                        N--;
                    }
                }
            }
            if (!queues.isEmpty() || !busyQueues.isEmpty()) {
                AndroidUtilities.runOnUIThread(this, 30000);
                cleanupScheduled = true;
            } else {
                cleanupScheduled = false;
            }
        }
    };

    public DispatchQueuePool(String poolName, int count) {
        this.poolName = poolName;
        maxCount = count;
    }

    @UiThread
    public void execute(Runnable runnable) {
        execute(runnable, 0L);
    }

    @UiThread
    public void execute(Runnable runnable, long delay) {
        DispatchQueue queue;
        if (!busyQueues.isEmpty() && (totalTasksCount / 2 <= busyQueues.size() || queues.isEmpty() && createdCount >= maxCount)) {
            queue = busyQueues.remove(0);
        } else if (queues.isEmpty()) {
            queue = new DispatchQueue(poolName + "_" + Utilities.random.nextInt());
            queue.setPriority(Thread.MAX_PRIORITY);
            createdCount++;
        } else {
            queue = queues.remove(0);
        }
        if (!cleanupScheduled) {
            AndroidUtilities.runOnUIThread(cleanupRunnable, 30000);
            cleanupScheduled = true;
        }
        totalTasksCount++;
        busyQueues.add(queue);
        int count = busyQueuesMap.get(queue.index, 0);
        busyQueuesMap.put(queue.index, count + 1);
        postedRunnables.put(runnable, queue);
        queue.postRunnable(() -> {
            postedRunnables.remove(runnable);
            runnable.run();
            AndroidUtilities.runOnUIThread(() -> {
                totalTasksCount--;
                int remainingTasksCount = busyQueuesMap.get(queue.index) - 1;
                if (remainingTasksCount == 0) {
                    busyQueuesMap.delete(queue.index);
                    busyQueues.remove(queue);
                    queues.add(queue);
                } else {
                    busyQueuesMap.put(queue.index, remainingTasksCount);
                }
            });
        }, delay);
    }

    @UiThread
    public void cancelRunnable(Runnable runnable) {
        DispatchQueue queue = postedRunnables.get(runnable);
        if (queue != null) {
            postedRunnables.remove(runnable);
            queue.cancelRunnable(runnable);
        }
    }
}
